package com.hazemafaneh.babymonitorpro.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes at the smallest power-of-two fraction that still covers [width] x [height],
 * then scales the rest of the way.
 *
 * This runs on every captured frame, on the camera device, right after that same frame
 * was encoded. Decoding the whole 720p picture back to get 64 x 48 out of it cost about
 * as much as the encode did — the detector was doubling the camera's CPU bill for a
 * thumbnail. libjpeg does the subsampled decode in the DCT domain, so an eighth-size
 * decode is genuinely close to an eighth of the work, not a full decode plus a resize.
 */
actual fun downscaleToGray(jpeg: ByteArray, width: Int, height: Int): ByteArray? = runCatching {
    val decoded = decodeSubsampled(jpeg, width, height) ?: return null
    val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
    if (scaled != decoded) decoded.recycle()

    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    scaled.recycle()

    ByteArray(pixels.size) { index -> luminanceOf(pixels[index]) }
}.getOrNull()

/** Smallest decode whose dimensions are still at least [minWidth] x [minHeight]. */
internal fun decodeSubsampled(jpeg: ByteArray, minWidth: Int, minHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, minWidth, minHeight)
        // Half the bytes to scan for luma, and no alpha to strip.
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
}

/** Largest power of two that keeps both dimensions at or above the minimums. */
internal fun sampleSizeFor(width: Int, height: Int, minWidth: Int, minHeight: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= minWidth && height / (sample * 2) >= minHeight) sample *= 2
    return sample
}

/** Rec. 601 luma, integer arithmetic — this runs on every frame. */
internal fun luminanceOf(argb: Int): Byte {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (((r * 299) + (g * 587) + (b * 114)) / 1000).toByte()
}
