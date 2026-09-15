package com.hazemafaneh.babymonitorpro.client

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.protocol.BmpJson
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.protocol.DeviceInfoResponse
import com.hazemafaneh.babymonitorpro.protocol.decodeControlMessage
import com.hazemafaneh.babymonitorpro.protocol.encode
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * The viewer half: reads `/info`, pulls MJPEG frames off `/stream`, and keeps the
 * `/control` and `/audio` sockets open.
 *
 * One instance per watched camera; [close] shuts the underlying HTTP client down.
 */
class ViewerClient(
    private val endpoint: CameraEndpoint,
) {
    // expectSuccess stays off so the status can be turned into a message a parent can act
    // on rather than Ktor's generic client exception. Every call site has to check the
    // status itself — see [requireSuccess].
    private val client = cameraHttpClient {
        install(WebSockets)
        expectSuccess = false
    }

    suspend fun fetchInfo(): DeviceInfoResponse {
        val response = client.get("${endpoint.baseUrl}${Bmp.PATH_INFO}")
        response.requireSuccess()
        return BmpJson.decodeFromString(DeviceInfoResponse.serializer(), response.bodyAsText())
    }

    /**
     * JPEG frames as they arrive. Cold: collecting opens the connection, cancelling
     * closes it. Web viewers use an `<img>` element instead — a browser decodes
     * `multipart/x-mixed-replace` natively and far more cheaply.
     */
    fun streamFrames(): Flow<ByteArray> = channelFlow {
        // channelFlow, not flow: `execute` runs its block in the engine's own context, and
        // on Darwin that is a different dispatcher from the collector's. A bare flow{} then
        // fails every single read with "Flow invariant is violated", which the viewer's
        // reconnect loop turns into a permanently black picture. The audio and control
        // sockets below were always channelFlow, which is why only video was affected.
        val downstream = this
        // UPGRADE PATH: a WebRTC PeerConnection would be established here, replacing the
        // multipart read loop; everything downstream still just sees decoded frames.
        val parser = MjpegParser(Bmp.MJPEG_BOUNDARY)
        client.prepareGet("${endpoint.baseUrl}${Bmp.PATH_STREAM}").execute { response ->
            // Without this the viewer sits on an error body forever: no boundary ever
            // arrives, so the parser yields no frames and the screen stays black with no
            // explanation.
            response.requireSuccess()
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(READ_BUFFER)
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                for (frame in parser.feed(buffer, read)) downstream.send(frame)
            }
        }
    }
        // Newest frame wins, per PROTOCOL.md. Without this the default 64-deep buffer fills
        // with 720p JPEGs whenever the decoder is slower than the network — which it is on
        // iOS, where decoding happens on the main thread — trading memory for latency.
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** PCM chunks from `/audio`, exactly as captured: 16-bit LE mono 16 kHz. */
    fun audioChunks(): Flow<ByteArray> = channelFlow {
        val downstream = this
        client.webSocket(urlString = "${wsBase()}${Bmp.PATH_AUDIO}") {
            for (frame in incoming) {
                if (frame is Frame.Binary) downstream.send(frame.readBytes())
            }
        }
    }

    /**
     * Opens `/control` and pumps both directions: messages from the camera are emitted,
     * anything appearing on [outgoing] is forwarded to it.
     */
    fun control(outgoing: Flow<ControlMessage>? = null): Flow<ControlMessage> = channelFlow {
        val downstream = this
        client.webSocket(urlString = "${wsBase()}${Bmp.PATH_CONTROL}") {
            send(Frame.Text(ControlMessage.Hello(viewerName = "viewer").encode()))

            val sender = outgoing?.let { source ->
                launch { source.collect { message -> send(Frame.Text(message.encode())) } }
            }

            try {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    decodeControlMessage(text)?.let { downstream.send(it) }
                }
            } finally {
                sender?.cancel()
            }
        }
    }

    fun close() = client.close()

    private fun wsBase(): String = "ws://${endpoint.host}:${endpoint.port}"

    private fun HttpResponse.requireSuccess() {
        if (status.isSuccess()) return
        throw CameraHttpException(status.value)
    }

    private companion object {
        /**
         * Sized to take a whole 720p frame in one or two reads. At 16 KB a typical frame
         * arrived in seven pieces, and each piece was another pass through the parser.
         */
        const val READ_BUFFER = 128 * 1024
    }
}

/**
 * A camera answered, but not with the stream. Carried as an exception because every read
 * path is a cold flow the caller collects — there is nowhere else to put the status.
 */
class CameraHttpException(val statusCode: Int) : Exception(describe(statusCode)) {

    private companion object {
        fun describe(status: Int): String = when (status) {
            HttpStatusCode.NotFound.value -> "That address is not a BabyMonitor Pro camera"
            else -> "The camera answered with HTTP $status"
        }
    }
}
