package com.hazemafaneh.babymonitorpro.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext
import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.PairingUri

actual fun notifyAlert(endpoint: CameraEndpoint, message: String) {
    val context = AndroidPlatformContext.applicationContext ?: return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH),
    )

    val notification = Notification.Builder(context, CHANNEL_ID)
        .setContentTitle(endpoint.name)
        .setContentText(message)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setAutoCancel(true)
        .apply { openLiveView(context, endpoint)?.let(::setContentIntent) }
        .build()

    // Silently ignored when POST_NOTIFICATIONS was declined — the in-app banner still shows.
    runCatching { manager.notify(NOTIFICATION_ID, notification) }
}

/**
 * Opens this camera with sound already on.
 *
 * The `audio=1` is the point: the app raised this notification because it *heard* something,
 * so landing muted and making the parent hunt for the Sound control is three actions in the
 * one situation where the requirement is the fewest possible.
 */
private fun openLiveView(
    context: Context,
    endpoint: CameraEndpoint,
): PendingIntent? = runCatching {
    val link = buildString {
        append(PairingUri.build(endpoint.host, endpoint.port))
        append('?').append(Bmp.AUDIO_QUERY_PARAM).append("=1")
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
        setPackage(context.packageName)
        // singleTask, so this reaches onNewIntent on an app that is already up rather than
        // stacking a second copy of the live view on top of the running one.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}.getOrNull()

private const val CHANNEL_ID = "alerts"
private const val NOTIFICATION_ID = 43
private const val REQUEST_CODE = 43
