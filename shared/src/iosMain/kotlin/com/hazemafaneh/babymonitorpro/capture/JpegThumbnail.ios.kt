package com.hazemafaneh.babymonitorpro.capture

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreGraphics.CGImageRef
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize

/**
 * A JPEG decoded straight to a picture no larger than [maxPixel] on its long side.
 *
 * ImageIO's thumbnail path hands libjpeg a scale factor, so the DCT is only evaluated
 * to the resolution asked for — an eighth-size picture costs roughly an eighth of a full
 * decode, rather than a full decode followed by a resize. Both callers here are producing
 * something small from a frame the camera has already encoded at full size: a 64 x 48
 * luma plane for the motion detector, and a preview the size of a card.
 *
 * `CreateThumbnailFromImageAlways` because a camera frame has no embedded thumbnail, and
 * without the flag ImageIO would look for one, find nothing, and return null.
 *
 * The caller owns the returned image and must `CGImageRelease` it.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun decodeJpegThumbnail(jpeg: ByteArray, maxPixel: Int): CGImageRef? {
    if (jpeg.isEmpty() || maxPixel <= 0) return null

    val data = jpeg.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = jpeg.size.convert())
    }
    // Toll-free bridge to CFData. The retain is balanced by the release below.
    val cfData: CFDataRef = CFBridgingRetain(data)?.reinterpret() ?: return null

    try {
        val source = CGImageSourceCreateWithData(cfData, null) ?: return null
        try {
            return memScoped {
                val size = alloc<IntVar>().apply { value = maxPixel }
                val sizeNumber = CFNumberCreate(null, kCFNumberIntType, size.ptr)
                val keys = allocArray<COpaquePointerVar>(3)
                val values = allocArray<COpaquePointerVar>(3)
                keys[0] = kCGImageSourceCreateThumbnailFromImageAlways
                values[0] = kCFBooleanTrue
                keys[1] = kCGImageSourceThumbnailMaxPixelSize
                values[1] = sizeNumber
                keys[2] = kCGImageSourceShouldCacheImmediately
                values[2] = kCFBooleanTrue
                val options = CFDictionaryCreate(null, keys, values, 3, null, null)
                try {
                    CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
                } finally {
                    options?.let { CFRelease(it) }
                    sizeNumber?.let { CFRelease(it) }
                }
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(cfData)
    }
}
