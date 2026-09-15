package com.hazemafaneh.babymonitorpro.ui.video

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.hazemafaneh.babymonitorpro.capture.sampleSizeFor

actual fun decodeJpegFrame(bytes: ByteArray, maxWidth: Int): ImageBitmap? = runCatching {
    if (maxWidth <= 0) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Never below the box: a power-of-two step that lands under it would be upscaled on
    // draw, and a soft preview reads as a soft camera.
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxWidth, 1)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()
