package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

// Desktop draws the full frame; the bound is advisory and a laptop has the CPU to ignore it.
actual fun decodeJpegFrame(bytes: ByteArray, maxWidth: Int): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
