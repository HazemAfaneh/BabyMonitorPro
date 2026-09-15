package com.hazemafaneh.babymonitorpro.capture

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake

/**
 * Decodes only as much picture as the detector needs, then lets CoreGraphics do the
 * greyscale conversion on the way into a one-byte-per-pixel context.
 *
 * Previously went through `UIImage.imageWithData`, which decodes the full 720p frame —
 * on the camera device, on every frame, immediately after that frame was encoded. The
 * thumbnail path asks libjpeg for the next power of two above the target instead.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun downscaleToGray(jpeg: ByteArray, width: Int, height: Int): ByteArray? {
    if (jpeg.isEmpty() || width <= 0 || height <= 0) return null

    // Twice the target: ImageIO rounds the scale to a power of two and may land just
    // under a bare maximum, and a detector fed an upscaled 40 x 30 sees blur as stillness.
    val image = decodeJpegThumbnail(jpeg, maxOf(width, height) * 2) ?: return null

    try {
        val output = ByteArray(width * height)
        val drawn = output.usePinned { pinned ->
            // One byte per pixel, no alpha: CoreGraphics does the greyscale conversion.
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.convert(),
                height = height.convert(),
                bitsPerComponent = 8.convert(),
                bytesPerRow = width.convert(),
                space = CGColorSpaceCreateDeviceGray(),
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value,
            ) ?: return@usePinned false

            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                image,
            )
            true
        }
        return if (drawn) output else null
    } finally {
        CGImageRelease(image)
    }
}
