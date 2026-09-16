package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.protocol.BmpJson
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.protocol.DeviceInfoResponse
import com.hazemafaneh.babymonitorpro.protocol.decodeControlMessage
import com.hazemafaneh.babymonitorpro.protocol.encode
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The HTTP surface of a camera. Owns nothing but the socket: frames and audio arrive on
 * flows the [Broadcaster] feeds.
 *
 * Binds 0.0.0.0 because a LAN server has to be reachable from other devices on the WiFi.
 * That is the whole of the exposure — the app never forwards a port, never contacts a
 * relay, and refuses to advertise a non-private address in the pairing UI.
 */
class BroadcastServer(
    private val scope: CoroutineScope,
    private val frames: SharedFlow<ByteArray>,
    private val audio: SharedFlow<ByteArray>,
    private val outbound: SharedFlow<ControlMessage>,
    private val statusProvider: () -> ControlMessage.Status,
    private val infoProvider: () -> DeviceInfoResponse,
    private val onViewerCountChanged: (Int) -> Unit,
    private val onControlMessage: (ControlMessage) -> Unit,
) {
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private val viewers = MutableStateFlow(0)

    val running: Boolean get() = engine != null

    fun start(port: Int) {
        if (engine != null) return

        engine = embeddedServer(CIO, port = port, host = BIND_ALL_INTERFACES) {
            install(WebSockets)

            routing {
                get(Bmp.PATH_INFO) {
                    // BmpJson, not a bare Json: the default instance would drop every field
                    // still sitting at its default value and hand viewers a near-empty body.
                    call.respondText(
                        text = BmpJson.encodeToString(DeviceInfoResponse.serializer(), infoProvider()),
                        contentType = ContentType.Application.Json,
                    )
                }

                get(Bmp.PATH_STREAM) {
                    // Per-viewer frame rate, for someone watching over mobile data.
                    //
                    // Frames are *dropped*, not re-encoded. Re-encoding per viewer would mean
                    // decoding and recompressing every frame again on the nursery phone, for
                    // each watcher — the one device in the system that is already doing the
                    // most work and is usually the oldest. Dropping costs nothing and cuts
                    // the bandwidth very nearly linearly, because every MJPEG frame is a
                    // whole picture: half the frames is half the data.
                    //
                    // A viewer that asks for nothing gets everything, so this is invisible to
                    // anyone on the same WiFi.
                    val requestedFps = call.request.queryParameters[Bmp.QUERY_FPS]
                        ?.toIntOrNull()
                        ?.coerceIn(1, 60)
                    val minIntervalMillis = requestedFps?.let { 1000L / it } ?: 0L
                    var lastSentAt = 0L
                    run {
                        // UPGRADE PATH: a WebRTC video track would be negotiated here instead
                        // of this multipart response; the frame flow above stays as-is.
                        call.respondBytesWriter(
                            contentType = ContentType.parse(Bmp.MJPEG_CONTENT_TYPE),
                        ) {
                            // collect, not collectLatest: collectLatest cancels its block the
                            // moment the next frame arrives, which lands mid-write and puts a
                            // truncated JPEG on the wire behind an honest Content-Length. The
                            // viewer decodes half a picture — the "randomly broken image".
                            // Dropping stale frames is already handled upstream, where the
                            // frame flow is DROP_OLDEST, so a slow viewer misses whole frames
                            // instead of receiving damaged ones.
                            frames.collect { jpeg ->
                                if (minIntervalMillis > 0) {
                                    val now = nowMillis()
                                    if (now - lastSentAt < minIntervalMillis) return@collect
                                    lastSentAt = now
                                }
                                // One write per frame: a part must never be split across two
                                // suspension points where cancellation could land between them.
                                writeFully(framePacket(jpeg))
                                flush()
                            }
                        }
                    }
                }

                webSocket(Bmp.PATH_AUDIO) {
                    audio.collect { chunk -> send(Frame.Binary(true, chunk)) }
                }

                webSocket(Bmp.PATH_CONTROL) {
                    // Viewers are counted here, not on /stream.
                    //
                    // A viewer opens exactly one control socket and holds it for as long as
                    // it is watching. The video connection is not like that at all: it is
                    // reopened on every blink, every reconnect and every backgrounding, and
                    // each of those incremented the count on the way in and decremented it
                    // on the way out — so the camera screen showed "2 watching" for one
                    // television, and a viewer that had walked out of WiFi range stayed in
                    // the count until its socket finally timed out. Counting the channel
                    // that is *meant* to be long-lived is what makes the number true.
                    trackViewer {
                    send(Frame.Text(statusProvider().encode()))

                    val pump = scope.launch {
                        outbound.collect { message -> send(Frame.Text(message.encode())) }
                    }
                    try {
                        incoming.consumeEach { frame ->
                            val text = (frame as? Frame.Text)?.readText() ?: return@consumeEach
                            when (val message = decodeControlMessage(text)) {
                                null -> Unit
                                is ControlMessage.Ping ->
                                    send(Frame.Text(ControlMessage.Pong(message.nonce).encode()))
                                is ControlMessage.Hello ->
                                    send(Frame.Text(statusProvider().encode()))
                                else -> onControlMessage(message)
                            }
                        }
                    } finally {
                        pump.cancel()
                    }
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    suspend fun stop() {
        engine?.stop(GRACE_MILLIS, TIMEOUT_MILLIS)
        engine = null
        viewers.value = 0
        onViewerCountChanged(0)
    }

    private inline fun trackViewer(block: () -> Unit) {
        viewers.update { it + 1 }
        onViewerCountChanged(viewers.value)
        try {
            block()
        } finally {
            viewers.update { (it - 1).coerceAtLeast(0) }
            onViewerCountChanged(viewers.value)
        }
    }

    private companion object {
        /** Headers, payload and trailing CRLF as one buffer, ready for a single write. */
        fun framePacket(jpeg: ByteArray): ByteArray {
            val header = (
                "--${Bmp.MJPEG_BOUNDARY}\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${jpeg.size}\r\n\r\n"
                ).encodeToByteArray()
            val packet = ByteArray(header.size + jpeg.size + 2)
            header.copyInto(packet)
            jpeg.copyInto(packet, header.size)
            packet[packet.size - 2] = '\r'.code.toByte()
            packet[packet.size - 1] = '\n'.code.toByte()
            return packet
        }

        const val BIND_ALL_INTERFACES = "0.0.0.0"
        const val GRACE_MILLIS = 300L
        const val TIMEOUT_MILLIS = 1000L
    }
}
