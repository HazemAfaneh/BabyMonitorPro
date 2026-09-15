package com.hazemafaneh.babymonitorpro.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig

/** CIO: a plain socket, so the multipart framing arrives exactly as the camera wrote it. */
internal actual fun cameraHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(CIO) {
        engine { forLongLivedStreams() }
        configure()
    }

/**
 * The engine settings that keep an MJPEG stream up.
 *
 * CIO's defaults are built for request/response traffic: [requestTimeout] is 15 seconds, and
 * this response is *designed never to end*. So every stream was being torn down a quarter of
 * a minute after it opened, the loop reconnected two seconds later, and the picture blinked
 * — forever, on a perfectly healthy network. That is the "it disconnects a lot and reconnects
 * immediately" everyone was seeing; nothing was wrong with the WiFi.
 *
 * - `requestTimeout = 0` disables the request deadline. A monitor stream is measured in
 *   hours, and the stall watchdog in `MjpegFrameLoop` is the thing that decides a stream is
 *   dead — it can tell a stalled stream from a slow one, which a fixed deadline cannot.
 * - `connectTimeout` stays short. Failing to *reach* a camera should be quick, because that
 *   is the case where retrying is the right answer.
 * - `socketTimeout` is generous rather than infinite: a camera that goes away mid-frame
 *   should eventually drop rather than hold a half-read socket open for the night. Twenty
 *   seconds is well past the eight the watchdog allows, so the watchdog stays the first
 *   thing to notice.
 */
private fun CIOEngineConfig.forLongLivedStreams() {
    requestTimeout = 0
    endpoint.connectTimeout = CONNECT_TIMEOUT_MILLIS
    endpoint.socketTimeout = SOCKET_TIMEOUT_MILLIS
    endpoint.keepAliveTime = KEEP_ALIVE_MILLIS
}

private const val CONNECT_TIMEOUT_MILLIS = 8_000L
private const val SOCKET_TIMEOUT_MILLIS = 20_000L
private const val KEEP_ALIVE_MILLIS = 30_000L

