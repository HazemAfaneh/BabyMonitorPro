package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop has no native image widget worth reaching for, so the frames are drawn with
 * Compose. The decode still moves off the UI thread — that is where the cost is.
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
    var frame by remember(endpoint.id) { mutableStateOf<ImageBitmap?>(null) }
    // Held outside the render lambda so the callback fires on a change, not per frame.
    var lastAspect by remember(endpoint.id) { mutableStateOf(0f) }

    MjpegFrameLoop(
        endpoint = endpoint,
        onStatus = onStatus,
        onError = onError,
        onFrame = onFrame,
        render = { jpeg ->
            val decoded = withContext(Dispatchers.Default) { decodeJpegFrame(jpeg) }
            if (decoded == null) {
                false
            } else {
                frame = decoded
                val ratio =
                    if (decoded.height > 0) decoded.width.toFloat() / decoded.height else 0f
                if (ratio > 0f && ratio != lastAspect) {
                    lastAspect = ratio
                    onAspectRatio(ratio)
                }
                true
            }
        },
    )

    Box(modifier.background(Color.Black)) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "Live view of ${endpoint.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
