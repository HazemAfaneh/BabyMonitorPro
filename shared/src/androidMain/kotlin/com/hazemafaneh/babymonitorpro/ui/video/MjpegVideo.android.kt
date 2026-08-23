package com.hazemafaneh.babymonitorpro.ui.video

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    pin: String?,
    modifier: Modifier,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
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
        pin = pin,
        onStatus = onStatus,
        onError = onError,
        onFrame = onFrame,
        render = { jpeg ->
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }.getOrNull()
            }
            if (bitmap == null) {
                false
            } else {
                withContext(Dispatchers.Main) { imageView.setImageBitmap(bitmap) }
                true
            }
        },
    )

    AndroidView(factory = { imageView }, modifier = modifier)
}
