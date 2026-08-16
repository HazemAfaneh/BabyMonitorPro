package com.hazemafaneh.babymonitorpro.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext

actual fun notifyAlert(cameraName: String, message: String) {
    val context = AndroidPlatformContext.applicationContext ?: return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH),
    )

    val notification = Notification.Builder(context, CHANNEL_ID)
        .setContentTitle(cameraName)
        .setContentText(message)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setAutoCancel(true)
        .build()

    // Silently ignored when POST_NOTIFICATIONS was declined — the in-app banner still shows.
    runCatching { manager.notify(NOTIFICATION_ID, notification) }
}

private const val CHANNEL_ID = "alerts"
private const val NOTIFICATION_ID = 43
