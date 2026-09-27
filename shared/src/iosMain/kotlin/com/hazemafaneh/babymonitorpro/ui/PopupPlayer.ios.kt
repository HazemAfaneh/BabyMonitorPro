package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable

/**
 * TODO(platform): iOS picture-in-picture is `AVPictureInPictureController`, and it will only
 * carry an `AVPlayerLayer` or an `AVSampleBufferDisplayLayer`. This stream is MJPEG decoded
 * frame by frame into a Compose canvas, so making this work means feeding the frames into a
 * sample-buffer layer first — real work, and it needs a Mac to try.
 */
actual object PopupPlayer {
    actual val isSupported: Boolean = false
    actual fun enter(): Boolean = false
    actual fun setAspectRatio(aspect: Float) = Unit
    actual fun setAutoEnter(enabled: Boolean) = Unit
}

@Composable
actual fun isInPopupPlayer(): Boolean = false
