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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps the process alive and the camera bound while the phone acting as the camera sits
 * with its screen off. Holds no capture state of its own — the broadcaster
 * owns that; this is purely the Android lifecycle contract.
 */
class BroadcastService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME).orEmpty()
        startInForeground(deviceName)
        holdLocks()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    /**
     * Keeps the radio and the CPU awake while the nursery phone sits face-down with its
     * screen off — which is the entire job, and the state the app was failing in.
     *
     * Two separate locks, because the screen going off does two different things:
     *
     * - **The WiFi lock.** With the screen off Android puts the WiFi chip into power save,
     *   where it wakes only periodically and drops most multicast. mDNS is entirely
     *   multicast, so the camera stopped answering discovery queries the moment the phone
     *   locked: the viewer's "On this network" list came up empty for a camera that was
     *   broadcasting perfectly well. `WIFI_MODE_FULL_LOW_LATENCY` also holds the sleep
     *   policy off the stream itself, which is what stops the picture stuttering a few
     *   seconds after the screen goes dark.
     * - **The wake lock.** A foreground service is not allowed to be killed, but its threads
     *   are still suspended in deep doze. The capture pipeline and the HTTP server are both
     *   plain coroutines; without a partial wake lock they are frozen along with everything
     *   else, and every viewer sees a stream that simply stops.
     *
     * Both are released in onDestroy, which the broadcaster triggers by stopping the service.
     * Neither is held for a second longer than the broadcast.
     */
    private fun holdLocks() {
        if (wakeLock != null) return
        runCatching {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.e(TAG, "No wake lock; capture may stop with the screen off", it) }

        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, WIFI_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.e(TAG, "No WiFi lock; discovery may fail with the screen off", it) }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun startInForeground(deviceName: String) {
        createChannel()

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BabyMonitor Pro is broadcasting")
            .setContentText(
                if (deviceName.isBlank()) "Streaming to your WiFi network"
                else "$deviceName is streaming to your WiFi network",
            )
            .setSmallIcon(com.hazemafaneh.babymonitorpro.notify.notificationIcon(this))
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
        private const val WAKE_TAG = "babymonitorpro:broadcast"
        private const val WIFI_TAG = "babymonitorpro:broadcast-wifi"
    }
}
