package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable

/**
 * Camera and microphone access, as the camera screen sees it.
 *
 * Only Android asks up front: iOS prompts when the capture session starts, and desktop
 * grants at the OS level, so those platforms report granted and do nothing on [request].
 */
interface CapturePermissions {
    val cameraGranted: Boolean
    val microphoneGranted: Boolean
    val needsRequest: Boolean get() = !cameraGranted || !microphoneGranted

    /**
     * Whether the platform has finished deciding. Only Android has a stretch where the
     * answer is genuinely unknown — a system dialog is on screen — and anything that starts
     * capturing during it starts twice: once without a camera, then again the instant the
     * user taps Allow. Every other platform has nothing to wait for.
     */
    val resolved: Boolean get() = true

    fun request()
}

@Composable
expect fun rememberCapturePermissions(): CapturePermissions
