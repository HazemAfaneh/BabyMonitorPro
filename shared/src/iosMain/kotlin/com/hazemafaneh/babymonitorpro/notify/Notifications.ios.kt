package com.hazemafaneh.babymonitorpro.notify

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual fun notifyAlert(cameraName: String, message: String) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
    ) { granted, _ ->
        if (!granted) return@requestAuthorizationWithOptions

        val content = UNMutableNotificationContent().apply {
            setTitle(cameraName)
            setBody(message)
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "bmpro-alert",
            content = content,
            // The shortest trigger iOS accepts; the alert is already late by definition.
            trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false),
        )
        center.addNotificationRequest(request, null)
    }
}
