package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable

/** A desktop window is already a window: the parent can resize it and put it in a corner. */
actual object PopupPlayer {
    actual val isSupported: Boolean = false
    actual fun enter(): Boolean = false
    actual fun setAspectRatio(aspect: Float) = Unit
    actual fun setAutoEnter(enabled: Boolean) = Unit
}

@Composable
actual fun isInPopupPlayer(): Boolean = false
