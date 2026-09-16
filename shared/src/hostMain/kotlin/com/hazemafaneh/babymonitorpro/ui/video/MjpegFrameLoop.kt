package com.hazemafaneh.babymonitorpro.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    maxFps: Int?,
    onStatus: (VideoStatus) -> Unit,
    onError: (String?) -> Unit,
    onFrame: (Long) -> Unit,
    render: suspend (ByteArray) -> Boolean,
) {
    val client = remember(endpoint.id) { ViewerClient(endpoint) }

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
                    client.streamFrames(maxFps).collect { jpeg ->
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
                    when {
                        // The connection is up and the camera is answering; it is the
                        // pictures that stopped. Reported apart from a dead connection so
                        // the parent is not sent to check their WiFi over a working one.
                        cause is StalledStreamException ||
                            cause is UndecodableStreamException -> VideoStatus.NO_PICTURE
                        else -> VideoStatus.FAILED
                    },
                )
                onError(cause?.message ?: cause?.toString() ?: "Unknown error")
            }
            attempt++
            // Backed off, rather than a flat two seconds every time.
            //
            // Most reconnects are a blink — a dropped frame boundary, a phone switching
            // access point — and a fixed two-second wait turned every one of them into two
            // seconds of stale picture. The first retry is now almost immediate, and only a
            // camera that keeps refusing earns the longer waits, which is the case where
            // hammering it helps nobody.
            delay(reconnectDelay(attempt))
        }
    }
}

/** Native targets draw frames into their own view, layered with the Compose surface. */
actual val videoRendersBehindUi: Boolean = false

internal class StalledStreamException : Exception(
    "Connected, but the nursery device sent no video. Check the camera screen is still open there.",
)

internal class UndecodableStreamException(received: Int) : Exception(
    "Received $received frames from the nursery device, but none could be decoded.",
)

/** 250ms, 500, 1000, 2000, then a flat 3s ceiling. */
private fun reconnectDelay(attempt: Int): Long {
    val backoff = FIRST_RECONNECT_MILLIS shl (attempt - 1).coerceIn(0, 4)
    return backoff.coerceAtMost(MAX_RECONNECT_MILLIS)
}

private const val FIRST_RECONNECT_MILLIS = 250L
private const val MAX_RECONNECT_MILLIS = 3000L
private const val STALL_CHECK_MILLIS = 1000L
private const val STALL_TIMEOUT_MILLIS = 8000L
