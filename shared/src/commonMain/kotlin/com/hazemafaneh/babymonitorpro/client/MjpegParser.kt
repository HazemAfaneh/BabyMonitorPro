package com.hazemafaneh.babymonitorpro.client

/**
 * Incremental parser for `multipart/x-mixed-replace` streams.
 *
 * Feed it whatever the socket hands over — boundaries and headers routinely straddle
 * chunk edges — and it hands back complete JPEG frames.
 *
 * Two wire shapes arrive in practice, so the parser sniffs which one it is looking at:
 *
 *  - **Multipart**, the actual protocol: `--frame` plus per-part headers. Every client
 *    reads a raw socket (see `cameraHttpClient`), so this is the normal path on all
 *    platforms.
 *  - **Bare concatenated JPEGs**, with the framing already gone. Some HTTP stacks parse
 *    `multipart/x-mixed-replace` themselves and hand each part up as its own body — Apple's
 *    CFNetwork does, which is what the iOS viewer used to go through. Scanning for `--frame`
 *    in such a stream finds nothing, which is a black picture rather than a visible error,
 *    so the shape is still recognised and handled.
 */
class MjpegParser(boundary: String = "frame") {

    private enum class Framing { UNKNOWN, MULTIPART, BARE_JPEG }

    private sealed interface Scan {
        /** Index just past this JPEG's EOI. */
        data class Complete(val end: Int) : Scan

        /** More bytes needed before the frame can be judged. */
        data object Incomplete : Scan

        /** Frame is unusable; resume scanning from [resumeAt]. */
        data class Damaged(val resumeAt: Int) : Scan
    }

    /** Frames dropped as truncated. Exposed so a viewer can report a degrading link. */
    var damagedFrames: Int = 0
        private set

    private val boundaryMarker = "--$boundary".encodeToByteArray()
    private var buffer = ByteArray(0)
    private var framing = Framing.UNKNOWN

    /** Appends [chunk] and returns every frame that is now complete. */
    fun feed(chunk: ByteArray, length: Int = chunk.size): List<ByteArray> {
        // Always copy. Callers read into one reused array — ViewerClient hands the same
        // 16 KB buffer to every readAvailable — so keeping the caller's array whenever a
        // read happened to fill it exactly left the pending frame to be overwritten in
        // place by the next read. That corrupts whichever frame straddled the boundary,
        // which surfaces as a decode failure rather than anything pointing back here.
        buffer = if (buffer.isEmpty()) chunk.copyOf(length) else buffer + chunk.copyOf(length)

        if (framing == Framing.UNKNOWN) {
            framing = sniff()
            // Not enough bytes to tell yet. Returning early matters: the multipart branch
            // discards everything but a boundary-sized tail when it finds no boundary,
            // which would eat the head of a bare JPEG stream before it was recognised.
            if (framing == Framing.UNKNOWN) return emptyList()
        }

        val frames = mutableListOf<ByteArray>()
        while (true) {
            val frame = when (framing) {
                Framing.MULTIPART -> extractFrame()
                else -> extractBareJpeg()
            } ?: break
            frames += frame
        }
        return frames
    }

    fun reset() {
        buffer = ByteArray(0)
        framing = Framing.UNKNOWN
    }

    /** A multipart stream opens on its boundary; a stripped one opens on a JPEG SOI. */
    private fun sniff(): Framing = when {
        startsWithSoi(buffer) -> Framing.BARE_JPEG
        indexOf(buffer, boundaryMarker, 0) >= 0 -> Framing.MULTIPART
        // A boundary can straddle a read, so a short buffer proves nothing either way.
        buffer.size < boundaryMarker.size -> Framing.UNKNOWN
        else -> Framing.MULTIPART
    }

    private fun startsWithSoi(data: ByteArray): Boolean =
        data.size >= 2 && data[0] == MARKER && data[1] == SOI

    /**
     * One JPEG, walked segment by segment rather than scanned for a trailing `FF D9`.
     * An EXIF thumbnail is a whole JPEG nested inside an APP1 segment, so a naive search
     * for the end marker would cut the frame short; skipping each segment by its declared
     * length steps over the thumbnail instead of into it.
     */
    private fun extractBareJpeg(): ByteArray? {
        while (true) {
            val start = indexOf(buffer, SOI_MARKER, 0)
            if (start < 0) {
                if (buffer.size > 1) buffer = buffer.copyOfRange(buffer.size - 1, buffer.size)
                return null
            }
            when (val scan = scanJpeg(buffer, start)) {
                Scan.Incomplete -> return null

                // The frame ended at the next picture's SOI instead of its own EOI, so it
                // was cut short in transit. Drop it and restart there. Without this the
                // scan sails through the truncated tail into the following frame and emits
                // the two spliced together as one "complete" JPEG — after which every
                // frame is damaged, not just the one that was lost.
                is Scan.Damaged -> {
                    damagedFrames++
                    buffer = buffer.copyOfRange(scan.resumeAt, buffer.size)
                }

                is Scan.Complete -> {
                    val frame = buffer.copyOfRange(start, scan.end)
                    buffer = buffer.copyOfRange(scan.end, buffer.size)
                    return frame
                }
            }
        }
    }

    private fun extractFrame(): ByteArray? {
        val boundaryAt = indexOf(buffer, boundaryMarker, 0)
        if (boundaryAt < 0) {
            // Keep only a boundary-sized tail; the rest can never start a boundary.
            if (buffer.size > boundaryMarker.size) {
                buffer = buffer.copyOfRange(buffer.size - boundaryMarker.size, buffer.size)
            }
            return null
        }

        val headerStart = boundaryAt + boundaryMarker.size
        val headerEnd = indexOf(buffer, HEADER_TERMINATOR, headerStart)
        if (headerEnd < 0) return null

        val headers = buffer.decodeToString(headerStart, headerEnd)
        val bodyStart = headerEnd + HEADER_TERMINATOR.size
        val contentLength = contentLengthOf(headers)

        if (contentLength != null) {
            val bodyEnd = bodyStart + contentLength
            if (buffer.size < bodyEnd) return null
            val frame = buffer.copyOfRange(bodyStart, bodyEnd)
            buffer = buffer.copyOfRange(bodyEnd, buffer.size)
            return frame
        }

        // No Content-Length: the frame runs up to the next boundary.
        val nextBoundary = indexOf(buffer, boundaryMarker, bodyStart)
        if (nextBoundary < 0) return null
        var bodyEnd = nextBoundary
        if (bodyEnd >= 2 && buffer[bodyEnd - 2] == CR && buffer[bodyEnd - 1] == LF) bodyEnd -= 2
        val frame = buffer.copyOfRange(bodyStart, bodyEnd)
        buffer = buffer.copyOfRange(nextBoundary, buffer.size)
        return frame
    }

    private fun contentLengthOf(headers: String): Int? = headers
        .split("\r\n", "\n")
        .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()

    private companion object {
        const val CR: Byte = 13
        const val LF: Byte = 10
        val HEADER_TERMINATOR = byteArrayOf(CR, LF, CR, LF)

        const val MARKER = 0xFF.toByte()
        const val SOI = 0xD8.toByte()
        val SOI_MARKER = byteArrayOf(MARKER, SOI)

        /**
         * Walks one JPEG from [from]. A second SOI before this picture's EOI means the
         * frame was truncated on the wire and the next one has already begun.
         */
        fun scanJpeg(data: ByteArray, from: Int): Scan {
            var at = from + 2
            while (true) {
                if (at + 1 >= data.size) return Scan.Incomplete
                if (data[at] != MARKER) {
                    at++
                    continue
                }
                when (val marker = data[at + 1].toInt() and 0xFF) {
                    // A run of 0xFF bytes is legal padding before the real marker byte.
                    0xFF -> at++
                    0xD9 -> return Scan.Complete(at + 2)
                    0xD8 -> return Scan.Damaged(at)
                    // Standalone markers: TEM and the restart markers carry no payload.
                    0x01, in 0xD0..0xD7 -> at += 2
                    0xDA -> {
                        val header = segmentLength(data, at) ?: return Scan.Incomplete
                        at += 2 + header
                        // Entropy-coded scan data. A literal 0xFF inside it is stuffed as
                        // FF 00, so any other marker byte here ends the scan.
                        while (true) {
                            if (at + 1 >= data.size) return Scan.Incomplete
                            if (data[at] != MARKER) {
                                at++
                                continue
                            }
                            val next = data[at + 1].toInt() and 0xFF
                            if (next == 0x00 || next in 0xD0..0xD7) {
                                at += 2
                                continue
                            }
                            if (next == 0xFF) {
                                at++
                                continue
                            }
                            break
                        }
                    }
                    else -> {
                        // Not a marker carrying a length: the stream is out of step here.
                        if (marker < 0xC0) return Scan.Damaged(at + 2)
                        val length = segmentLength(data, at) ?: return Scan.Incomplete
                        at += 2 + length
                    }
                }
            }
        }

        fun segmentLength(data: ByteArray, markerAt: Int): Int? {
            if (markerAt + 3 >= data.size) return null
            val length = ((data[markerAt + 2].toInt() and 0xFF) shl 8) or
                (data[markerAt + 3].toInt() and 0xFF)
            return if (length < 2) null else length
        }

        fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
            if (needle.isEmpty() || haystack.size < needle.size) return -1
            outer@ for (i in from.coerceAtLeast(0)..haystack.size - needle.size) {
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
