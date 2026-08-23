package com.hazemafaneh.babymonitorpro.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

/** CIO: a plain socket, so the multipart framing arrives exactly as the camera wrote it. */
internal actual fun cameraHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(CIO) { configure() }
