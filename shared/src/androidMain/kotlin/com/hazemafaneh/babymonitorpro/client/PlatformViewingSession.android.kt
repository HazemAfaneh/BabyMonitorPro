package com.hazemafaneh.babymonitorpro.client

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext

actual object PlatformViewingSession {

    actual fun begin(cameraName: String) {
        val context = AndroidPlatformContext.applicationContext ?: return
        val intent = Intent(context, ViewingService::class.java).apply {
            action = ViewingService.ACTION_START
            putExtra(ViewingService.EXTRA_CAMERA_NAME, cameraName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun end() {
        val context = AndroidPlatformContext.applicationContext ?: return
        context.stopService(Intent(context, ViewingService::class.java))
    }
}
