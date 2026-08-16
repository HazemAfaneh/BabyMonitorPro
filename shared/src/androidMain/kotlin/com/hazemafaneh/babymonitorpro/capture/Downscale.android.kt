package com.hazemafaneh.babymonitorpro.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory

actual fun downscaleToGray(jpeg: ByteArray, width: Int, height: Int): ByteArray? = runCatching {
    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
    val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
    if (scaled != decoded) decoded.recycle()

    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    scaled.recycle()

    ByteArray(pixels.size) { index -> luminanceOf(pixels[index]) }
}.getOrNull()

/** Rec. 601 luma, integer arithmetic — this runs on every frame. */
internal fun luminanceOf(argb: Int): Byte {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (((r * 299) + (g * 587) + (b * 114)) / 1000).toByte()
}
