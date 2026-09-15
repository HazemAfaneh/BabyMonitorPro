package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Draws a stream of JPEG frames this device is already producing — the camera's own view of
 * what it is sending — as opposed to [MjpegVideo], which opens a connection to a remote one.
 *
 * Three things here exist to keep the picture steady, and all three were what made the
 * previous inline version flicker on iOS:
 *
 * - **No transition between frames.** A `Crossfade` keyed on the frame restarts a fade
 *   every time one arrives, so at capture rate the picture spends nearly all of its life at
 *   partial alpha, dissolving into itself. That reads as flicker, not motion. Frames replace
 *   each other whole; there is nothing to animate between them.
 * - **The decode is off the UI thread.** It is the expensive part of the pipeline, and on
 *   iOS the UI thread is also the only thread that draws, so a decode there competes with
 *   drawing the frame before it.
 * - **The frame is held in state read inside this composable.** Read it one level up and a
 *   new frame invalidates every sibling too — text fields, sliders, the QR canvas and its
 *   several hundred rectangles — at capture rate.
 *
 * Unlike [MjpegVideo] this draws with Compose on every target, iOS included, rather than
 * handing frames to a native `UIImageView`. A native view interoperated into a
 * `verticalScroll` container lags the Compose content it is supposed to move with, and this
 * preview sits in the middle of a scrolling settings column — the interop cure would be
 * worse than the disease. The picture is also a 16:9 thumbnail rather than a full screen, so
 * the Skia path has room to spare.
 *
 * [onAspectRatio] reports the shape of the decoded frame, because the caller cannot know it
 * in advance: a phone held portrait produces a portrait stream, and a box sized for
 * landscape crops that to a band across the middle — which looks far more like a rotation
 * fault than like cropping. Size the container from this instead of assuming.
 *
 * [frames] may be null where the platform cannot broadcast at all; the placeholder stays.
 */
@Composable
internal fun JpegFrameView(
    frames: Flow<ByteArray>?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onAspectRatio: (Float) -> Unit = {},
    placeholder: @Composable () -> Unit = {},
) {
    var frame by remember(frames) { mutableStateOf<ImageBitmap?>(null) }
    val reportAspect by rememberUpdatedState(onAspectRatio)
    // How wide the box actually is, in pixels, so the decoder can stop at that width. A
    // plain var read from the collector rather than state: it changes on layout, not per
    // frame, and the frame after a resize picking it up is soon enough.
    var boxWidthPx by remember(frames) { mutableStateOf(0) }

    LaunchedEffect(frames) {
        val source = frames ?: return@LaunchedEffect
        // Held here rather than in state: the shape changes once, when the first frame
        // lands, and putting it in state would recompose this on every frame to no purpose.
        var lastAspect = 0f
        // The source drops frames for a slow collector rather than queueing them, so a
        // decode that cannot keep up falls behind in rate, never in time.
        source.collect { jpeg ->
            // This is the third decode of a frame the camera device has already encoded and
            // motion-checked, and it is for a thumbnail. Bounding it to the box turns a
            // 720p decode into whatever fraction the box actually needs.
            val bound = boxWidthPx
            val decoded = withContext(Dispatchers.Default) { decodeJpegFrame(jpeg, bound) }
                ?: return@collect
            frame = decoded
            val aspect = if (decoded.height > 0) decoded.width.toFloat() / decoded.height else 0f
            if (aspect > 0f && aspect != lastAspect) {
                lastAspect = aspect
                reportAspect(aspect)
            }
        }
    }

    // Deliberately no fallback to the placeholder once a frame has been drawn: the stream
    // pausing is not the same as it never having started, and a preview that blanks itself
    // looks like a fault.
    val bitmap = frame
    Box(
        modifier = modifier.onSizeChanged { boxWidthPx = it.width },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            placeholder()
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
