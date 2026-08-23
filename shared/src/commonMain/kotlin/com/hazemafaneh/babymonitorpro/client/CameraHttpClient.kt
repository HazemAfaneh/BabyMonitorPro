package com.hazemafaneh.babymonitorpro.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * The HTTP client every viewer talks to a camera with.
 *
 * The engine is chosen per platform rather than left to whatever is on the classpath,
 * because the MJPEG stream is a `multipart/x-mixed-replace` response and not every engine
 * hands one over intact — see the iOS actual for the failure that forced this.
 */
internal expect fun cameraHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient
