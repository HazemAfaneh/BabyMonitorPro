package com.hazemafaneh.babymonitorpro.server

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext

actual object PlatformBroadcastSession {

    actual fun begin(deviceName: String) {
        val context = AndroidPlatformContext.applicationContext ?: return
        val intent = Intent(context, BroadcastService::class.java).apply {
            action = BroadcastService.ACTION_START
            putExtra(BroadcastService.EXTRA_DEVICE_NAME, deviceName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun end() {
        val context = AndroidPlatformContext.applicationContext ?: return
        context.stopService(Intent(context, BroadcastService::class.java))
    }
}
