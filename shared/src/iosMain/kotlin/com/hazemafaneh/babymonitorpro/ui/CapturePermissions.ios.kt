package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType

@Composable
actual fun rememberCapturePermissions(): CapturePermissions = remember { IosCapturePermissions() }

private class IosCapturePermissions : CapturePermissions {
    // The AVMediaType constants come through as String? from the Objective-C headers.
    private val video: String = AVMediaTypeVideo ?: "vide"
    private val audio: String = AVMediaTypeAudio ?: "soun"

    private fun status(mediaType: String) =
        AVCaptureDevice.authorizationStatusForMediaType(mediaType)

    // Not-determined counts as granted: iOS raises its own prompt the moment the capture
    // session starts, so blocking the screen behind a second prompt helps nobody.
    override val cameraGranted: Boolean
        get() = status(video).let {
            it == AVAuthorizationStatusAuthorized || it == AVAuthorizationStatusNotDetermined
        }

    override val microphoneGranted: Boolean
        get() = status(audio).let {
            it == AVAuthorizationStatusAuthorized || it == AVAuthorizationStatusNotDetermined
        }

    override fun request() {
        AVCaptureDevice.requestAccessForMediaType(video) { }
        AVCaptureDevice.requestAccessForMediaType(audio) { }
    }
}
