package com.hazemafaneh.babymonitorpro.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCapturePermissions(): CapturePermissions {
    val context = LocalContext.current

    var camera by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    var microphone by remember {
        mutableStateOf(context.isGranted(Manifest.permission.RECORD_AUDIO))
    }
    // A denial is an answer too, so this is not the same as both being granted: without it
    // a permanently denied microphone would leave the screen waiting forever.
    var answered by remember { mutableStateOf(false) }
    // Set only once a request has actually come back, because the rationale flag is false
    // both before the first ask and after the last one.
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        camera = result[Manifest.permission.CAMERA] ?: camera
        microphone = result[Manifest.permission.RECORD_AUDIO] ?: microphone
        answered = true
        // Blocked on the strength of the two that matter. Notifications ride along in the
        // same dialog, but a parent who refuses those still gets a working monitor, and
        // sending them to Settings over it would be a lie about what is broken.
        denied = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .any { !context.isGranted(it) && !context.wouldShowDialog(it) }
    }

    // Re-read on every resume. A parent sent to Settings changes the switch there and comes
    // back to a screen that would otherwise still be insisting it has no camera, because
    // nothing in this composition would have noticed the grant.
    OnResume {
        camera = context.isGranted(Manifest.permission.CAMERA)
        microphone = context.isGranted(Manifest.permission.RECORD_AUDIO)
        if (camera && microphone) {
            answered = true
            denied = false
        }
    }

    return remember(camera, microphone, answered, denied) {
        object : CapturePermissions {
            override val cameraGranted = camera
            override val microphoneGranted = microphone
            override val resolved = answered || (camera && microphone)
            override val blocked = denied

            override fun request() {
                val requested = buildList {
                    add(Manifest.permission.CAMERA)
                    add(Manifest.permission.RECORD_AUDIO)
                    // Without this the foreground-service notification is silent on 13+.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                launcher.launch(requested.toTypedArray())
            }

            override fun openSettings() = context.openAppSettings()
        }
    }
}
