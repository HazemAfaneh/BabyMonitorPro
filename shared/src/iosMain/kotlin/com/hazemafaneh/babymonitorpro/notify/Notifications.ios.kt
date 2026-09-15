package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.core.Bmp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.PairingUri
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual fun notifyAlert(endpoint: CameraEndpoint, alert: CameraAlert) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
    ) { granted, _ ->
        if (!granted) return@requestAuthorizationWithOptions

        val content = UNMutableNotificationContent().apply {
            // Which sensor fired first, the reading second, the camera third — iOS shows the
            // title in bold and the subtitle beneath it, so this is the same ordering the
            // Android shade gets.
            setTitle(alert.headline)
            setSubtitle(endpoint.name)
            setBody(alert.detail)
            // Read back by the notification delegate in MainViewController when the parent
            // taps this. audio=1 because the app raised it for a sound it heard — landing
            // muted would make them hunt for the control while the moment passes.
            setUserInfo(
                mapOf<Any?, Any?>(
                    NOTIFICATION_LINK_KEY to buildString {
                        append(PairingUri.build(endpoint.host, endpoint.port))
                        append('?').append(Bmp.AUDIO_QUERY_PARAM).append("=1")
                    },
                ),
            )
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            // Per kind, so a sound alert never replaces a motion one — iOS coalesces by
            // identifier, and the two answer different questions.
            identifier = "bmpro-alert-${alert.kind.name.lowercase()}",
            content = content,
            // The shortest trigger iOS accepts; the alert is already late by definition.
            trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false),
        )
        center.addNotificationRequest(request, null)
    }
}

/** Key under which the pairing link rides in the notification's userInfo. */
const val NOTIFICATION_LINK_KEY = "bmpro.link"
