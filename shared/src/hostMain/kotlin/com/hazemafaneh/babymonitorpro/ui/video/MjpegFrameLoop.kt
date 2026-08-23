package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.hazemafaneh.babymonitorpro.client.CameraHttpException
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connection, retry and stall detection for the MJPEG stream, with no opinion about how the
 * picture is drawn. Each platform renders with its own surface — a `UIImageView` on iOS, an
 * `ImageView` on Android — but they all need identical reconnect behaviour, so it lives here.
 *
 * [render] receives each JPEG and returns whether it produced a picture. It is suspending on
 * purpose: decoding is the expensive part of this pipeline and must not happen on the
 * caller's thread, which on both platforms is the UI thread.
 *
 * Draws nothing itself.
 */
@Composable
internal fun MjpegFrameLoop(
    endpoint: CameraEndpoint,
    pin: String?,
    onStatus: (VideoStatus) -> Unit,
    onError: (String?) -> Unit,
    onFrame: (Long) -> Unit,
    render: suspend (ByteArray) -> Boolean,
) {
    val client = remember(endpoint.id, pin) { ViewerClient(endpoint, pin) }

    DisposableEffect(client) {
        onDispose { client.close() }
    }

    LaunchedEffect(client) {
        var attempt = 0
        while (true) {
            onStatus(if (attempt == 0) VideoStatus.CONNECTING else VideoStatus.RECONNECTING)
            val outcome = runCatching {
                // A connection that opens and then delivers nothing is the worst case: no
                // exception ever arrives, so without this watchdog the screen stays black
                // forever. Anything that stalls is treated as dead and retried.
                var lastDrawnAt = nowMillis()
                var received = 0
                var drawn = 0
                coroutineScope {
                    val watchdog = launch {
                        while (true) {
                            delay(STALL_CHECK_MILLIS)
                            if (nowMillis() - lastDrawnAt <= STALL_TIMEOUT_MILLIS) continue
                            // Bytes arriving but nothing drawing is a different failure from
                            // nothing arriving at all, and the two need different answers.
                            throw if (received > 0 && drawn == 0) {
                                UndecodableStreamException(received)
                            } else {
                                StalledStreamException()
                            }
                        }
                    }
                    client.streamFrames().collect { jpeg ->
                        received++
                        if (render(jpeg)) {
                            drawn++
                            lastDrawnAt = nowMillis()
                            onStatus(VideoStatus.LIVE)
                            onError(null)
                            onFrame(nowMillis())
                        }
                    }
                    watchdog.cancel()
                }
            }
            // Leaving the screen cancels this effect; that is not a connection failure.
            outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }

            if (outcome.isFailure) {
                val cause = outcome.exceptionOrNull()
                onStatus(
                    if (cause is CameraHttpException && cause.unauthorized) {
                        VideoStatus.UNAUTHORIZED
                    } else {
                        VideoStatus.FAILED
                    },
                )
                onError(cause?.message ?: cause?.toString() ?: "Unknown error")
            }
            attempt++
            // A nursery camera gets carried around; reconnect quietly rather than
            // dumping the parent back to the device list.
            delay(RECONNECT_DELAY_MILLIS)
        }
    }
}

/** Native targets draw frames into their own view, layered with the Compose surface. */
actual val videoRendersBehindUi: Boolean = false

private class StalledStreamException : Exception(
    "Connected, but the camera sent no video. Check the camera screen is still open.",
)

private class UndecodableStreamException(received: Int) : Exception(
    "Received $received frames from the camera but none could be decoded.",
)

private const val RECONNECT_DELAY_MILLIS = 1500L
private const val STALL_CHECK_MILLIS = 1000L
private const val STALL_TIMEOUT_MILLIS = 8000L
