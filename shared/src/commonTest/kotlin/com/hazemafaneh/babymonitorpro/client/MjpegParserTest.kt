package com.hazemafaneh.babymonitorpro.client

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MjpegParserTest {

    private fun part(payload: ByteArray): ByteArray =
        ("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${payload.size}\r\n\r\n")
            .encodeToByteArray() + payload + "\r\n".encodeToByteArray()

    @Test
    fun readsConsecutiveFramesFromOneChunk() {
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(9, 8)
        val parser = MjpegParser()

        val frames = parser.feed(part(first) + part(second))

        assertEquals(2, frames.size)
        assertContentEquals(first, frames[0])
        assertContentEquals(second, frames[1])
    }

    @Test
    fun reassemblesAFrameSplitAcrossChunks() {
        val payload = ByteArray(64) { it.toByte() }
        val stream = part(payload)
        val parser = MjpegParser()

        // Split mid-header: the boundary and the Content-Length land in different reads.
        val cut = 12
        assertTrue(parser.feed(stream.copyOfRange(0, cut)).isEmpty())
        val frames = parser.feed(stream.copyOfRange(cut, stream.size))

        assertEquals(1, frames.size)
        assertContentEquals(payload, frames.single())
    }

    @Test
    fun holdsBackAPartialBodyUntilItIsComplete() {
        val payload = ByteArray(40) { (it * 3).toByte() }
        val stream = part(payload)
        val parser = MjpegParser()
        val headerEnd = stream.size - payload.size - 2

        assertTrue(parser.feed(stream.copyOfRange(0, headerEnd + 10)).isEmpty())
        val frames = parser.feed(stream.copyOfRange(headerEnd + 10, stream.size))

        assertContentEquals(payload, frames.single())
    }

    @Test
    fun toleratesAMissingContentLength() {
        val payload = byteArrayOf(7, 7, 7, 7)
        val stream = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".encodeToByteArray() +
            payload + "\r\n".encodeToByteArray() + "--frame\r\n".encodeToByteArray()

        val frames = MjpegParser().feed(stream)

        assertContentEquals(payload, frames.single())
    }

    @Test
    fun jpegBytesContainingTheBoundaryTextAreNotTruncated() {
        // Content-Length is authoritative, so a payload that happens to spell "--frame"
        // must still come through whole.
        val payload = "--frame".encodeToByteArray() + byteArrayOf(0, 1, 2)
        val frames = MjpegParser().feed(part(payload))

        assertContentEquals(payload, frames.single())
    }
}
