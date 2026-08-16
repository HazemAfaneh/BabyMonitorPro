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
    fun request()
}

@Composable
expect fun rememberCapturePermissions(): CapturePermissions
