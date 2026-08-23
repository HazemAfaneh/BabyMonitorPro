package com.hazemafaneh.babymonitorpro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberCapturePermissions(): CapturePermissions {
    val context = LocalContext.current

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var camera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var microphone by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    // A denial is an answer too, so this is not the same as both being granted: without it
    // a permanently denied microphone would leave the screen waiting forever.
    var answered by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        camera = result[Manifest.permission.CAMERA] ?: camera
        microphone = result[Manifest.permission.RECORD_AUDIO] ?: microphone
        answered = true
    }

    return remember(camera, microphone, answered) {
        object : CapturePermissions {
            override val cameraGranted = camera
            override val microphoneGranted = microphone
            override val resolved = answered || (camera && microphone)
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
        }
    }
}
