package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.hazemafaneh.babymonitorpro.capture.decodeJpegThumbnail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big

@OptIn(ExperimentalForeignApi::class)
actual fun decodeJpegFrame(bytes: ByteArray, maxWidth: Int): ImageBitmap? = runCatching {
    if (maxWidth <= 0) {
        return Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    // Skia's encoded-image path cannot decode at a fraction of the size, so a bounded
    // decode goes through ImageIO and is handed to Skia as raw RGBA. The copy is a
    // fraction of the pixels the full decode would have produced, which is the point.
    val cgImage = decodeJpegThumbnail(bytes, maxWidth) ?: return null
    try {
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        if (width <= 0 || height <= 0) return null

        val rgba = ByteArray(width * height * 4)
        val drawn = rgba.usePinned { pinned ->
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.convert(),
                height = height.convert(),
                bitsPerComponent = 8.convert(),
                bytesPerRow = (width * 4).convert(),
                space = CGColorSpaceCreateDeviceRGB(),
                // R, G, B, A in memory order, premultiplied: what Skia calls RGBA_8888 PREMUL.
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or
                    kCGBitmapByteOrder32Big,
            ) ?: return@usePinned false
            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                cgImage,
            )
            true
        }
        if (!drawn) return null

        val bitmap = Bitmap()
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        if (!bitmap.allocPixels(info)) return null
        if (!bitmap.installPixels(rgba)) return null
        bitmap.setImmutable()
        Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    } finally {
        CGImageRelease(cgImage)
    }
}.getOrNull()
