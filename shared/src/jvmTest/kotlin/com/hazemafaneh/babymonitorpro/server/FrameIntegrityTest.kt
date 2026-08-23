package com.hazemafaneh.babymonitorpro.server

import com.hazemafaneh.babymonitorpro.core.Bmp
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads deliberately slower than the camera encodes and checks every delivered part against
 * its own Content-Length: a part must be exactly as long as it claims and must run from a
 * JPEG SOI to a JPEG EOI.
 *
 * Written while chasing randomly corrupted frames, on the theory that the route's old
 * `collectLatest` was cancelling its block part-way through a write. That theory is **not**
 * confirmed: this test passes against the `collectLatest` version too, even with the reader
 * throttled to a quarter of the production rate, so Ktor appears to either finish the
 * buffered write or fail the whole response rather than emit a partial part.
 *
 * It is kept as a straightforward integrity guard on the wire format.
 */
class FrameIntegrityTest {

    private val broadcaster = KtorBroadcaster()

    @AfterTest
    fun tearDown() = runBlocking { broadcaster.stop() }

    @Test
    fun everyPartMatchesItsContentLengthAndIsAWholeJpeg() = runBlocking {
        val port = 18_140
        broadcaster.start(
            BroadcastConfig(deviceName = "Integrity", port = port, useSyntheticVideo = true),
        )

        val client = HttpClient()
        val collected = ArrayList<Byte>(512 * 1024)
        try {
            withTimeout(TIMEOUT) {
                client.prepareGet("http://127.0.0.1:$port${Bmp.PATH_STREAM}").execute { response ->
                    val channel = response.bodyAsChannel()
                    // Small reads: the point is to consume far slower than the camera
                    // produces, so the socket buffer fills and the server's writes start
                    // suspending. Nothing is cancellable until a write actually blocks.
                    val buffer = ByteArray(1024)
                    while (collected.size < TARGET_BYTES) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        for (i in 0 until read) collected += buffer[i]
                        // Read slower than the camera encodes, so the server is always
                        // mid-write when the next frame lands.
                        delay(SLOW_READER_DELAY)
                    }
                }
            }
        } finally {
            client.close()
        }

        val stream = collected.toByteArray()
        val parts = parseParts(stream)
        assertTrue(parts.size >= 3, "expected several frames, got ${parts.size}")

        for ((index, part) in parts.withIndex()) {
            assertEquals(
                0xFF.toByte(), part[0],
                "part $index does not start with a JPEG SOI marker",
            )
            assertEquals(0xD8.toByte(), part[1], "part $index has a damaged SOI marker")
            // A truncated payload is exactly what collectLatest produced: the declared
            // length was honest, the bytes behind it were not.
            assertEquals(
                0xFF.toByte(), part[part.size - 2],
                "part $index is truncated: no EOI marker at the declared length",
            )
            assertEquals(0xD9.toByte(), part[part.size - 1], "part $index has a damaged EOI marker")
        }
    }

    /**
     * Splits on the wire format directly rather than reusing the client's parser.
     *
     * Byte offsets throughout: decoding the stream to a String first shifts every index,
     * because JPEG payloads are not valid UTF-8 and the invalid runs collapse into
     * replacement characters.
     */
    private fun parseParts(stream: ByteArray): List<ByteArray> {
        val boundary = "--${Bmp.MJPEG_BOUNDARY}".encodeToByteArray()
        val headerEnd = "\r\n\r\n".encodeToByteArray()
        val parts = mutableListOf<ByteArray>()
        var cursor = 0
        while (true) {
            val at = indexOf(stream, boundary, cursor).takeIf { it >= 0 } ?: break
            val bodyAt = indexOf(stream, headerEnd, at).takeIf { it >= 0 } ?: break
            val headers = stream.decodeToString(at, bodyAt)
            val length = Regex("Content-Length: (\\d+)")
                .find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: break
            val start = bodyAt + headerEnd.size
            if (start + length > stream.size) break // final part still in flight, not a defect
            parts += stream.copyOfRange(start, start + length)
            cursor = start + length
        }
        return parts
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private companion object {
        const val TIMEOUT = 40_000L

        // ~20 KB/s consumed against ~85 KB/s produced (12 fps of 720p JPEG), so the
        // buffers are saturated within a couple of seconds and stay that way.
        const val TARGET_BYTES = 200_000
        const val SLOW_READER_DELAY = 50L
    }
}
