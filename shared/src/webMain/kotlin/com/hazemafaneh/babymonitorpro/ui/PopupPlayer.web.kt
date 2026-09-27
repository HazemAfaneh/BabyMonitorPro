package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable

/**
 * TODO(platform): the Document Picture-in-Picture API could host the video element the web
 * build already layers over the canvas, but it is Chromium-only and the element would have to
 * be moved into the popup document and back. Not for v1.
 */
actual object PopupPlayer {
    actual val isSupported: Boolean = false
    actual fun enter(): Boolean = false
    actual fun setAspectRatio(aspect: Float) = Unit
    actual fun setAutoEnter(enabled: Boolean) = Unit
}

@Composable
actual fun isInPopupPlayer(): Boolean = false
