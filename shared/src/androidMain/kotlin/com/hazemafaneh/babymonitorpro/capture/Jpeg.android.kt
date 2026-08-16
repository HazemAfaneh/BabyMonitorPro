package com.hazemafaneh.babymonitorpro.capture

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

actual fun encodeJpeg(argb: IntArray, width: Int, height: Int, quality: Int): ByteArray? =
    runCatching {
        val bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream(width * height / 4)
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
        bitmap.recycle()
        output.toByteArray()
    }.getOrNull()
