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

    /**
     * Refused, and the system will not show the dialog again.
     *
     * The distinction matters because it decides what the screen can offer. While this is
     * false the honest button is "Allow", and tapping it puts the system dialog up. Once it
     * is true that button does nothing at all — Android returns the denial without drawing
     * anything — so the only truthful offer left is a trip to Settings.
     */
    val blocked: Boolean get() = false

    /**
     * Ask, or ask again.
     *
     * Safe to call repeatedly. Where the system has stopped answering, the screen should
     * be sending the parent to [openSettings] instead.
     */
    fun request()

    /** This app's page in the OS settings. A no-op where the platform has nowhere to go. */
    fun openSettings() = Unit
}

@Composable
expect fun rememberCapturePermissions(): CapturePermissions
