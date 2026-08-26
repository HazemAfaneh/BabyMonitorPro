package com.hazemafaneh.babymonitorpro.server

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps the process alive and the camera bound while the phone acting as the camera sits
 * with its screen off. Holds no capture state of its own — the broadcaster
 * owns that; this is purely the Android lifecycle contract.
 */
class BroadcastService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME).orEmpty()
        startInForeground(deviceName)
        return START_STICKY
    }

    private fun startInForeground(deviceName: String) {
        createChannel()

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BabyMonitor Pro is broadcasting")
            .setContentText(
                if (deviceName.isBlank()) "Streaming to your WiFi network"
                else "$deviceName is streaming to your WiFi network",
            )
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        // From API 34 a camera/microphone foreground service whose runtime permission is
        // missing throws SecurityException. Unhandled in onStartCommand that kills the
        // process — taking the HTTP server with it, so viewers see a refused connection
        // seconds after the camera screen showed them an address. Claim only what is
        // actually granted, and never let this be fatal: without the mic the video half
        // still works, and a dead service is better than a dead app.
        val types = grantedServiceTypes()
        runCatching {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> startForeground(NOTIFICATION_ID, notification)
                types != 0 -> startForeground(NOTIFICATION_ID, notification, types)
                else -> startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.e(TAG, "Foreground service refused; broadcasting only while open", it) }
    }

    private fun grantedServiceTypes(): Int {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

        var types = 0
        if (granted(Manifest.permission.CAMERA)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Broadcasting",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.hazemafaneh.babymonitorpro.START_BROADCAST"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val CHANNEL_ID = "broadcast"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "BroadcastService"
    }
}
