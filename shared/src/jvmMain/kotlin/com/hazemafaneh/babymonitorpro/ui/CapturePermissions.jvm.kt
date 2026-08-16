package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** macOS prompts on first capture; Windows and Linux gate at the OS settings level. */
@Composable
actual fun rememberCapturePermissions(): CapturePermissions = remember { AlwaysGranted }

private object AlwaysGranted : CapturePermissions {
    override val cameraGranted = true
    override val microphoneGranted = true
    override fun request() = Unit
}
