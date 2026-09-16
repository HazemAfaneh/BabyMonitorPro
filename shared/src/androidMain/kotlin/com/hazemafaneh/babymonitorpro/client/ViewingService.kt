package com.hazemafaneh.babymonitorpro.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Keeps a watching device watching while the parent is in another app.
 *
 * The camera end has had a foreground service since the beginning; the watching end had
 * nothing at all. So a parent who switched to WhatsApp handed Android permission to freeze
 * this process: the MJPEG stream and the control socket were cut, the Live Update in the
 * shade fell to "Reconnecting", and **no alert arrived** — the app was asleep, so there was
 * nothing listening for one. A baby monitor that only works while you are looking at it is
 * not a baby monitor.
 *
 * Holds no state. The live view still owns every socket; this exists purely so the system
 * lets those sockets keep running.
 */
class ViewingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(intent?.getStringExtra(EXTRA_CAMERA_NAME).orEmpty())
        holdLocks()
        // Not sticky, unlike the camera. A restarted viewing service would be watching
        // nothing — the live view is gone, and the thing to do is stay quiet rather than
        // put a notification back on a phone whose owner closed the app.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    private fun startInForeground(cameraName: String) {
        createChannel()

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(
                if (cameraName.isBlank()) "Watching the nursery" else "Watching $cameraName",
            )
            .setContentText("Sound and movement alerts are on")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        runCatching {
            // mediaPlayback, because that is what it is: the viewer plays the nursery's
            // audio. Claiming a type the app does not have permission for is fatal from
            // API 34, so this stays the one type the manifest declares for it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.e(TAG, "Foreground refused; watching only while open", it) }
    }

    /**
     * The same pair the camera holds, for the same reasons: the CPU lock keeps the stream's
     * coroutines running in doze, and the WiFi lock keeps the radio out of power save so the
     * TCP connection is not dropped a few seconds after the screen goes dark.
     */
    private fun holdLocks() {
        if (wakeLock != null) return
        runCatching {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.e(TAG, "No wake lock; the stream may stop in the background", it) }

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
        }.onFailure { Log.e(TAG, "No WiFi lock; the stream may drop in the background", it) }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watching",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.hazemafaneh.babymonitorpro.START_WATCHING"
        const val EXTRA_CAMERA_NAME = "camera_name"
        private const val CHANNEL_ID = "watching"
        private const val NOTIFICATION_ID = 45
        private const val TAG = "ViewingService"
        private const val WAKE_TAG = "babymonitorpro:watch"
        private const val WIFI_TAG = "babymonitorpro:watch-wifi"
    }
}
