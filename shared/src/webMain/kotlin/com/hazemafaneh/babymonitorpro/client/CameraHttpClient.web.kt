package com.hazemafaneh.babymonitorpro.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * The browser's own engine — the only one there is. Video does not come through here: an
 * `<img>` element decodes the stream natively, so what is left is `/info` and the sockets.
 */
internal actual fun cameraHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient { configure() }
