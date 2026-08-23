package com.hazemafaneh.babymonitorpro.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

/**
 * CIO, not Darwin — the reason is the whole "randomly broken picture" bug on iOS.
 *
 * Darwin runs on `NSURLSession`, and CFNetwork treats `multipart/x-mixed-replace` as a
 * series of responses: it parses the framing itself and reports each part through
 * `URLSession:dataTask:didReceiveResponse:completionHandler:`. Ktor's delegate does not
 * implement that method, so the parts are never acknowledged one by one, and the body
 * handed up starts mid-JPEG and drops a stretch of bytes at every part boundary. The
 * parser then reads the head of one frame followed by the tail of a later one, which
 * ImageIO decodes into a picture that is right at the top, scrambled across the middle and
 * flat grey at the bottom.
 *
 * A raw socket has no opinion about multipart: the boundary and `Content-Length` of every
 * part arrive as sent, so a frame is either complete or not delivered at all.
 */
internal actual fun cameraHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(CIO) { configure() }
