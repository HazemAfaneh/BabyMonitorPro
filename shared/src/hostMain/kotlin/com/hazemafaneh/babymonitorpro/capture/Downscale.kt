package com.hazemafaneh.babymonitorpro.capture

/**
 * Decodes a JPEG frame and returns it as a [width] x [height] grayscale thumbnail,
 * one byte per pixel, row-major. Returns null when the frame cannot be decoded.
 *
 * The motion detector works on this rather than the full frame: 64x48 is 3072 comparisons
 * per frame instead of nearly a million, and it smooths away sensor noise for free.
 */
expect fun downscaleToGray(jpeg: ByteArray, width: Int, height: Int): ByteArray?
