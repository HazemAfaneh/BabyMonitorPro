package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** The web build never captures — it only watches. */
@Composable
actual fun rememberCapturePermissions(): CapturePermissions = remember { NeverCaptures }

private object NeverCaptures : CapturePermissions {
    override val cameraGranted = false
    override val microphoneGranted = false
    override fun request() = Unit
}
