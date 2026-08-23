package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint

/** Connection lifecycle of a video surface, surfaced as the live view's status chip. */
enum class VideoStatus { CONNECTING, LIVE, RECONNECTING, UNAUTHORIZED, FAILED }

/**
 * Renders the camera's MJPEG stream.
 *
 * Native targets decode frames and draw them with Compose; the browser hands the same URL
 * to an `<img>` element, which decodes `multipart/x-mixed-replace` natively. Same bytes,
 * two very different renderers — the reason the transport is MJPEG in the first place.
 */
@Composable
expect fun MjpegVideo(
    endpoint: CameraEndpoint,
    pin: String?,
    modifier: Modifier,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
    /**
     * Why the last attempt failed, or null once frames are flowing. A black screen with no
     * reason is the hardest thing to debug on a phone that has no console.
     */
    onError: (String?) -> Unit = {},
)

/** JPEG -> ImageBitmap. Returns null on web, which never decodes frames itself. */
expect fun decodeJpegFrame(bytes: ByteArray): ImageBitmap?

/**
 * True where the video is a platform layer sitting *behind* the UI surface (the browser's
 * `<img>`), which means the screen must not paint an opaque background over it.
 */
expect val videoRendersBehindUi: Boolean
