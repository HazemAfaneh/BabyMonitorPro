package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectZero
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/**
 * A plain `UIImageView` fed decoded frames, rather than a Compose `Image` recomposed per
 * frame off a Skia bitmap.
 *
 * The expensive part is the JPEG decode, and the previous path did it inside a
 * `LaunchedEffect`, which runs on the UI thread — roughly a fifth of every second spent
 * decoding on the same thread that has to draw, which is what the stutter was. Here the
 * decode happens on a background dispatcher and only the assignment touches the main queue.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MjpegVideo(
    endpoint: CameraEndpoint,
    modifier: Modifier,
    maxFps: Int?,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
    onError: (String?) -> Unit,
    onAspectRatio: (Float) -> Unit,
) {
    // Held outside the render lambda so the callback fires on a change, not per frame.
    var lastAspect by remember(endpoint.id) { mutableStateOf(0f) }

    val imageView = remember(endpoint.id) {
        UIImageView(frame = CGRectZero.readValue()).apply {
            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
            backgroundColor = UIColor.blackColor
            // The stream replaces the whole image every frame; there is nothing to animate
            // between them, and an implicit animation would smear one frame into the next.
            layer.removeAllAnimations()
        }
    }

    MjpegFrameLoop(
        endpoint = endpoint,
        maxFps = maxFps,
        onStatus = onStatus,
        onError = onError,
        onFrame = onFrame,
        render = { jpeg ->
            val decoded = withContext(Dispatchers.Default) { decodeImmediately(jpeg) }
            if (decoded == null) {
                false
            } else {
                withContext(Dispatchers.Main) {
                    imageView.image = decoded
                    val size = decoded.size
                    // Read here rather than off the view: the view is full-screen, it is the
                    // image inside it that carries the shape.
                    val ratio = size.useContents {
                        if (height > 0.0) (width / height).toFloat() else 0f
                    }
                    if (ratio > 0f && ratio != lastAspect) {
                        lastAspect = ratio
                        onAspectRatio(ratio)
                    }
                }
                true
            }
        },
    )

    UIKitView(
        factory = { imageView },
        modifier = modifier,
        update = { },
        // Nothing on the picture is tappable; the live view's own controls sit above it.
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

/**
 * `UIImage(data:)` is lazy — it defers the actual decode until the image is first drawn,
 * which drags the work straight back onto the main thread and defeats the point of
 * decoding off it. Going through ImageIO with `kCGImageSourceShouldCacheImmediately` forces
 * the decode to happen here, on whichever thread called this.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun decodeImmediately(jpeg: ByteArray): UIImage? {
    if (jpeg.isEmpty()) return null

    val data = jpeg.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = jpeg.size.convert())
    }
    // Toll-free bridge to CFData. The retain is balanced by the release below.
    val cfData: CFDataRef = CFBridgingRetain(data)?.reinterpret() ?: return null

    try {
        val source = CGImageSourceCreateWithData(cfData, null) ?: return null
        try {
            val cgImage: CGImageRef? = memScoped {
                val keys = allocArray<COpaquePointerVar>(1)
                val values = allocArray<COpaquePointerVar>(1)
                keys[0] = kCGImageSourceShouldCacheImmediately
                values[0] = kCFBooleanTrue
                val options = CFDictionaryCreate(null, keys, values, 1, null, null)
                try {
                    CGImageSourceCreateImageAtIndex(source, 0u, options)
                } finally {
                    options?.let { CFRelease(it) }
                }
            }
            return cgImage?.let {
                val image = UIImage.imageWithCGImage(it)
                CGImageRelease(it)
                image
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(cfData)
    }
}
