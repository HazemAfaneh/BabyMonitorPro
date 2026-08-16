package com.hazemafaneh.babymonitorpro.client

/**
 * Incremental parser for `multipart/x-mixed-replace` streams.
 *
 * Feed it whatever the socket hands over — boundaries and headers routinely straddle
 * chunk edges — and it hands back complete JPEG frames.
 */
class MjpegParser(boundary: String = "frame") {

    private val boundaryMarker = "--$boundary".encodeToByteArray()
    private var buffer = ByteArray(0)

    /** Appends [chunk] and returns every frame that is now complete. */
    fun feed(chunk: ByteArray, length: Int = chunk.size): List<ByteArray> {
        buffer = if (buffer.isEmpty() && length == chunk.size) {
            chunk
        } else {
            buffer + chunk.copyOf(length)
        }

        val frames = mutableListOf<ByteArray>()
        while (true) {
            frames += extractFrame() ?: break
        }
        return frames
    }

    fun reset() {
        buffer = ByteArray(0)
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
