package com.hazemafaneh.babymonitorpro.capture

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun downscaleToGray(jpeg: ByteArray, width: Int, height: Int): ByteArray? {
    if (jpeg.isEmpty() || width <= 0 || height <= 0) return null

    val data = jpeg.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = jpeg.size.convert())
    }
    val image = UIImage.imageWithData(data)?.CGImage ?: return null

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
}

@Suppress("unused")
@OptIn(ExperimentalForeignApi::class)
private fun NSData.copyInto(destination: ByteArray) {
    destination.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
}
