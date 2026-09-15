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
import com.hazemafaneh.babymonitorpro.core.isTelevision

actual fun notifyAlert(endpoint: CameraEndpoint, alert: CameraAlert) {
    val context = AndroidPlatformContext.applicationContext ?: return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH),
    )

    val notification = Notification.Builder(context, CHANNEL_ID)
        // Which sensor fired, on the line the eye lands on. Both kinds used to arrive titled
        // with the camera's name and bodied with a sentence that differed in one word, so a
        // glance at the shade could not tell a baby crying from a blanket being kicked off.
        .setContentTitle(alert.headline)
        // The reading that raised it, in words. This is the half that decides whether a
        // parent gets up, and it is measured rather than guessed.
        .setContentText(alert.detail)
        // Which camera, which is the question the moment a second one exists. It goes third
        // because with one camera it is the least interesting line on the notification.
        .setSubText(endpoint.name)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setCategory(Notification.CATEGORY_ALARM)
        // Distinct per kind, so a sound alert never replaces a motion one in the shade. They
        // are answers to different questions and a parent may want both.
        .setGroup(GROUP_KEY)
        .setAutoCancel(true)
        .setWhen(alert.atMillis)
        .setShowWhen(true)
        .apply {
            // Nobody dismisses a notification on a television. There is no swipe, the remote
            // has no gesture for it, and an alert left standing over the picture is still
            // there an hour later saying the baby moved — which is precisely the lie this
            // app cannot afford. On a phone it stays until it is tapped or swiped, because
            // there it is the thing that wakes a parent who is asleep.
            if (isTelevision) setTimeoutAfter(TV_DISMISS_MILLIS)
            openLiveView(context, endpoint)?.let(::setContentIntent)
        }
        .build()

    // Silently ignored when POST_NOTIFICATIONS was declined — the in-app banner still shows.
    runCatching { manager.notify(notificationId(alert.kind), notification) }
}

/**
 * One id per kind, so motion and sound coexist rather than overwrite each other — and so a
 * second event of the same kind still replaces the first, which is the behaviour a stack of
 * near-identical alerts should have.
 */
private fun notificationId(kind: AlertKind): Int = when (kind) {
    AlertKind.MOTION -> NOTIFICATION_ID_MOTION
    AlertKind.SOUND -> NOTIFICATION_ID_SOUND
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
private const val GROUP_KEY = "bmpro.alerts"
private const val NOTIFICATION_ID_MOTION = 43
private const val NOTIFICATION_ID_SOUND = 44
private const val REQUEST_CODE = 43

/**
 * Long enough to be noticed by someone looking away from the screen, short enough that it is
 * gone before it stops being true. The detectors debounce at 3 seconds, so this also stops a
 * busy room leaving a permanent banner across the top of the picture.
 */
private const val TV_DISMISS_MILLIS = 12_000L
