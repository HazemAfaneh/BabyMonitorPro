package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint

/**
 * Connection lifecycle of a video surface, surfaced as the live view's status chip.
 *
 * [NO_PICTURE] is deliberately distinct from [FAILED]: a camera that answers on the control
 * channel while sending no frames is the most dangerous state in the app, because the parent
 * sees a black rectangle that a healthy-looking status has told them to trust. "Not
 * connected" and "connected, but blind" need different words and different answers.
 */
enum class VideoStatus { CONNECTING, LIVE, RECONNECTING, NO_PICTURE, FAILED }

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
    modifier: Modifier,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
    /**
     * Why the last attempt failed, or null once frames are flowing. A black screen with no
     * reason is the hardest thing to debug on a phone that has no console.
     */
    onError: (String?) -> Unit = {},
    /**
     * Width / height of the decoded frame, reported when it first becomes known and again
     * whenever it changes.
     *
     * The viewer cannot assume it: the camera's orientation is whatever the other device is
     * lying in, so a portrait phone watching a landscape camera aspect-fits the picture into
     * about a third of its screen. Knowing the shape is what lets the live view say so.
     */
    onAspectRatio: (Float) -> Unit = {},
)

/**
 * JPEG -> ImageBitmap. Returns null on web, which never decodes frames itself.
 *
 * [maxWidth] is the widest picture the caller can show, in pixels, or zero for the full
 * frame. A decoder that knows the answer is going into a 400px box can do a quarter of
 * the work; one that does not has to produce all of 720p and let the draw throw it away.
 */
expect fun decodeJpegFrame(bytes: ByteArray, maxWidth: Int = 0): ImageBitmap?

/**
 * True where the video is a platform layer sitting *behind* the UI surface (the browser's
 * `<img>`), which means the screen must not paint an opaque background over it.
 */
expect val videoRendersBehindUi: Boolean
