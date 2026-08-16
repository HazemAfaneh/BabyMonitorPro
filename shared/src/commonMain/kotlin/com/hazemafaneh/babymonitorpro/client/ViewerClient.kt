package com.hazemafaneh.babymonitorpro.client

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.protocol.BmpJson
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.protocol.DeviceInfoResponse
import com.hazemafaneh.babymonitorpro.protocol.decodeControlMessage
import com.hazemafaneh.babymonitorpro.protocol.encode
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * The viewer half: reads `/info`, pulls MJPEG frames off `/stream`, and keeps the
 * `/control` and `/audio` sockets open.
 *
 * One instance per watched camera; [close] shuts the underlying HTTP client down.
 */
class ViewerClient(
    private val endpoint: CameraEndpoint,
    private val pin: String? = null,
) {
    private val client = HttpClient {
        install(WebSockets)
        expectSuccess = false
    }

    private val query: String
        get() = if (pin.isNullOrBlank()) "" else "?${Bmp.PIN_QUERY_PARAM}=$pin"

    suspend fun fetchInfo(): DeviceInfoResponse {
        val response = client.get("${endpoint.baseUrl}${Bmp.PATH_INFO}$query") {
            pin?.let { header(Bmp.PIN_HEADER, it) }
        }
        return BmpJson.decodeFromString(DeviceInfoResponse.serializer(), response.bodyAsText())
    }

    /**
     * JPEG frames as they arrive. Cold: collecting opens the connection, cancelling
     * closes it. Web viewers use an `<img>` element instead — a browser decodes
     * `multipart/x-mixed-replace` natively and far more cheaply.
     */
    fun streamFrames(): Flow<ByteArray> = flow {
        // UPGRADE PATH: a WebRTC PeerConnection would be established here, replacing the
        // multipart read loop; everything downstream still just sees decoded frames.
        val parser = MjpegParser(Bmp.MJPEG_BOUNDARY)
        client.prepareGet("${endpoint.baseUrl}${Bmp.PATH_STREAM}$query") {
            pin?.let { header(Bmp.PIN_HEADER, it) }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(READ_BUFFER)
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                for (frame in parser.feed(buffer, read)) emit(frame)
            }
        }
    }

    /** PCM chunks from `/audio`, exactly as captured: 16-bit LE mono 16 kHz. */
    fun audioChunks(): Flow<ByteArray> = channelFlow {
        val downstream = this
        client.webSocket(urlString = "${wsBase()}${Bmp.PATH_AUDIO}$query") {
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
        client.webSocket(urlString = "${wsBase()}${Bmp.PATH_CONTROL}$query") {
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

    private companion object {
        const val READ_BUFFER = 16 * 1024
    }
}
