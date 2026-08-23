package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * [frames] may be null where the platform cannot broadcast at all; the placeholder stays.
 */
@Composable
internal fun JpegFrameView(
    frames: Flow<ByteArray>?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    var frame by remember(frames) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(frames) {
        val source = frames ?: return@LaunchedEffect
        // The source drops frames for a slow collector rather than queueing them, so a
        // decode that cannot keep up falls behind in rate, never in time.
        source.collect { jpeg ->
            withContext(Dispatchers.Default) { decodeJpegFrame(jpeg) }?.let { frame = it }
        }
    }

    // Deliberately no fallback to the placeholder once a frame has been drawn: the stream
    // pausing is not the same as it never having started, and a preview that blanks itself
    // looks like a fault.
    val bitmap = frame
    if (bitmap == null) {
        placeholder()
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
