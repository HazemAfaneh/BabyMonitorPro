package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis
import kotlinx.coroutines.delay

@Composable
actual fun MjpegVideo(
    endpoint: CameraEndpoint,
    pin: String?,
    modifier: Modifier,
    onStatus: (VideoStatus) -> Unit,
    onFrame: (Long) -> Unit,
) {
    var frame by remember(endpoint.id) { mutableStateOf<ImageBitmap?>(null) }
    val client = remember(endpoint.id, pin) { ViewerClient(endpoint, pin) }

    DisposableEffect(client) {
        onDispose { client.close() }
    }

    LaunchedEffect(client) {
        var attempt = 0
        while (true) {
            onStatus(if (attempt == 0) VideoStatus.CONNECTING else VideoStatus.RECONNECTING)
            val outcome = runCatching {
                client.streamFrames().collect { jpeg ->
                    val decoded = decodeJpegFrame(jpeg)
                    if (decoded != null) {
                        frame = decoded
                        onStatus(VideoStatus.LIVE)
                        onFrame(nowMillis())
                    }
                }
            }
            if (outcome.isFailure) {
                val message = outcome.exceptionOrNull()?.message.orEmpty()
                onStatus(if ("401" in message) VideoStatus.UNAUTHORIZED else VideoStatus.FAILED)
            }
            attempt++
            // A nursery camera gets carried around; reconnect quietly rather than
            // dumping the parent back to the device list.
            delay(RECONNECT_DELAY_MILLIS)
        }
    }

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

/** Native targets draw frames into the same Compose surface as the UI. */
actual val videoRendersBehindUi: Boolean = false

private const val RECONNECT_DELAY_MILLIS = 1500L
