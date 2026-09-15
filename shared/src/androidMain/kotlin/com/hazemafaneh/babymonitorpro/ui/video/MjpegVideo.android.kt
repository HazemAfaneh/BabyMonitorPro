package com.hazemafaneh.babymonitorpro.ui.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A plain `ImageView` fed decoded frames, rather than a Compose `Image` recomposed per frame.
 *
 * Decoding runs on a background dispatcher; only the assignment touches the main thread.
 * The previous path decoded inside a `LaunchedEffect`, which is the main thread, so every
 * frame competed with drawing the frame before it.
 */
@Composable
actual fun MjpegVideo(
    endpoint: CameraEndpoint,
    modifier: Modifier,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
    onError: (String?) -> Unit,
    onAspectRatio: (Float) -> Unit,
) {
    val context = LocalContext.current
    // Held outside the render lambda so the callback fires on a change, not per frame.
    var lastAspect by remember(endpoint.id) { mutableStateOf(0f) }
    val pool = remember(endpoint.id) { FrameBitmapPool() }
    val imageView = remember(endpoint.id) {
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    MjpegFrameLoop(
        endpoint = endpoint,
        onStatus = onStatus,
        onError = onError,
        onFrame = onFrame,
        render = { jpeg ->
            val bitmap = withContext(Dispatchers.Default) { pool.decode(jpeg) }
            if (bitmap == null) {
                false
            } else {
                withContext(Dispatchers.Main) { imageView.setImageBitmap(bitmap) }
                val ratio =
                    if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height else 0f
                if (ratio > 0f && ratio != lastAspect) {
                    lastAspect = ratio
                    onAspectRatio(ratio)
                }
                true
            }
        },
    )

    AndroidView(factory = { imageView }, modifier = modifier)
}

/**
 * A ring of decode targets, so each frame lands in a bitmap that already exists.
 *
 * `decodeByteArray` on its own allocates a fresh 1280 x 720 ARGB bitmap per frame — some
 * 3.7 MB, forty-odd megabytes a second at the stream's rate — and every one of them is
 * garbage the moment the next frame arrives. The collector pauses were not the network
 * and not the decoder; they were the GC clearing up after them.
 *
 * Three slots, not one: the view is still drawing the bitmap it was last handed while the
 * next frame decodes, and with hardware rendering the upload of a bitmap can outlive the
 * call that set it. Two frames of separation is enough that a slot is never being
 * written and read at once.
 *
 * `inBitmap` is strict about what it will reuse, and a stream can change shape when the
 * camera is turned round. A rejected slot is dropped and that frame allocates normally,
 * after which the new bitmap takes the slot.
 */
private class FrameBitmapPool(slots: Int = 3) {
    private val ring = arrayOfNulls<Bitmap>(slots)
    private var next = 0
    private val options = BitmapFactory.Options().apply { inMutable = true }

    fun decode(jpeg: ByteArray): Bitmap? {
        val slot = next
        next = (next + 1) % ring.size
        options.inBitmap = ring[slot]
        val decoded = try {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        } catch (_: IllegalArgumentException) {
            // The slot did not fit this frame. Let this one allocate, keep the result.
            options.inBitmap = null
            runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) }.getOrNull()
        }
        if (decoded != null) ring[slot] = decoded
        return decoded
    }
}
