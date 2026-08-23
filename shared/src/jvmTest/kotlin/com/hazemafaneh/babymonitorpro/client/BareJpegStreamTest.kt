package com.hazemafaneh.babymonitorpro.client

import com.hazemafaneh.babymonitorpro.capture.SyntheticVideoSource
import com.hazemafaneh.babymonitorpro.core.CaptureConfig
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Apple's CFNetwork parses `multipart/x-mixed-replace` itself and hands each part up as a
 * separate response body, so an iOS viewer receives the JPEG payloads concatenated with no
 * boundary and no headers. Verified against the real server with a URLSession probe: the
 * first bytes of every data callback were `FF D8 FF E0 JFIF`, never `--frame`.
 *
 * Real encoder output, not hand-built bytes — the parser has to walk actual JPEG segments.
 */
class BareJpegStreamTest {

    private fun realJpegs(count: Int): List<ByteArray> = runBlocking {
        SyntheticVideoSource(CaptureConfig(width = 320, height = 240))
            .frames.take(count).toList()
    }

    @Test
    fun recoversFramesFromAStreamWithTheMultipartFramingStripped() {
        val originals = realJpegs(4)
        assertTrue(originals.all { it.size > 500 }, "encoder produced suspiciously small JPEGs")

        val stream = originals.reduce { acc, jpeg -> acc + jpeg }
        val parser = MjpegParser()
        val frames = parser.feed(stream)

        assertEquals(originals.size, frames.size)
        for (i in originals.indices) assertContentEquals(originals[i], frames[i])
    }

    @Test
    fun reassemblesBareJpegsSplitAcrossAwkwardChunkSizes() {
        val originals = realJpegs(3)
        val stream = originals.reduce { acc, jpeg -> acc + jpeg }
        val parser = MjpegParser()

        // A read boundary landing inside a segment header is the case that breaks a naive
        // scan; 511 is deliberately coprime with anything the encoder emits.
        val recovered = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < stream.size) {
            val end = minOf(offset + 511, stream.size)
            recovered += parser.feed(stream.copyOfRange(offset, end))
            offset = end
        }

        assertEquals(originals.size, recovered.size)
        for (i in originals.indices) assertContentEquals(originals[i], recovered[i])
    }

    /** Reproduces the aliasing bug: one reused read buffer, refilled between feeds. */
    @Test
    fun survivesACallerThatReusesOneReadBuffer() {
        val originals = realJpegs(3)
        val stream = originals.reduce { acc, jpeg -> acc + jpeg }
        val parser = MjpegParser()
        val readBuffer = ByteArray(4096)

        val recovered = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < stream.size) {
            val read = minOf(readBuffer.size, stream.size - offset)
            stream.copyInto(readBuffer, 0, offset, offset + read)
            recovered += parser.feed(readBuffer, read)
            // What the socket does next: overwrite the very same array.
            readBuffer.fill(0x5A)
            offset += read
        }

        assertEquals(originals.size, recovered.size)
        for (i in originals.indices) assertContentEquals(originals[i], recovered[i])
    }

    /**
     * A frame cut short in transit must be dropped, not spliced onto the next one.
     *
     * Without resync the scanner runs past the missing EOI, treats the following picture's
     * SOI as a harmless standalone marker, and returns one "complete" JPEG made of the tail
     * of a broken frame and all of a good one. ImageIO then reports exactly what the device
     * log showed: "Decoding incomplete with error code -1".
     */
    @Test
    fun dropsATruncatedFrameInsteadOfSplicingItOntoTheNext() {
        val originals = realJpegs(3)
        val truncated = originals[0].copyOfRange(0, originals[0].size / 2)
        val stream = truncated + originals[1] + originals[2]

        val parser = MjpegParser()
        val frames = parser.feed(stream)

        assertEquals(2, frames.size, "the damaged frame should be dropped, the good ones kept")
        assertContentEquals(originals[1], frames[0])
        assertContentEquals(originals[2], frames[1])
        assertEquals(1, parser.damagedFrames)
    }

    @Test
    fun stillReadsTheRealMultipartFramingItWasWrittenFor() {
        val originals = realJpegs(2)
        val stream = originals.fold(ByteArray(0)) { acc, jpeg ->
            acc + ("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n")
                .encodeToByteArray() + jpeg + "\r\n".encodeToByteArray()
        }

        val frames = MjpegParser().feed(stream)

        assertEquals(originals.size, frames.size)
        for (i in originals.indices) assertContentEquals(originals[i], frames[i])
    }
}
