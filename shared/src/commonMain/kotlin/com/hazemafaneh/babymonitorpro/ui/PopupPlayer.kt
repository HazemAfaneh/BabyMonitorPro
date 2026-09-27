package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable

/**
 * The picture in a small window that floats over whatever else the parent is doing.
 *
 * This is the shape of the actual use: a parent is watching the cot, and then they want to
 * answer a message, check the time, look something up. Today that means leaving the live view
 * — the stream keeps running in its foreground service and the alerts still arrive, but the
 * picture is gone, and the picture is the point. A popup window keeps the cot on screen for
 * the ninety seconds somebody spends in another app.
 *
 * On Android this is picture-in-picture, which the system provides: the activity keeps
 * running and is simply drawn small, so the stream, the sound and the detectors are untouched
 * by it. Nowhere else has an equivalent worth faking — a desktop window is already a window
 * the parent can resize, a browser tab has Document PiP but no video element to hand it, and
 * iOS PiP is only for `AVPlayer` content, which an MJPEG stream is not. Those return
 * [isSupported] = false and every call here is a no-op.
 */
expect object PopupPlayer {
    /** Whether this device can show the popup at all. False everywhere but Android. */
    val isSupported: Boolean

    /**
     * Shrinks the app into the popup window now. False when the platform refused — most
     * often because the parent has turned the popup off for this app in system settings.
     */
    fun enter(): Boolean

    /**
     * The shape of the picture, so the popup is the shape of the cot rather than a letterbox
     * with black down both sides. Width over height; ignored where it is not positive.
     */
    fun setAspectRatio(aspect: Float)

    /**
     * Whether leaving the app should enter the popup by itself.
     *
     * On while the live view is up and off the moment it closes: a parent who presses home
     * from the nursery picture wants to keep seeing it, and a parent who presses home from
     * the camera list does not want a window following them around.
     */
    fun setAutoEnter(enabled: Boolean)
}

/**
 * True while the app is drawn in the popup window.
 *
 * The window is a couple of hundred dp across, so everything that is not the picture has to
 * go: chrome, rail, banners and panels are all unreadable at that size and all of them cover
 * the one thing worth showing.
 */
@Composable
expect fun isInPopupPlayer(): Boolean
