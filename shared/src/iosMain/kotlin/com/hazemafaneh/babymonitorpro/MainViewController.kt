package com.hazemafaneh.babymonitorpro

import androidx.compose.ui.window.ComposeUIViewController
import com.hazemafaneh.babymonitorpro.core.DeepLinks
import com.hazemafaneh.babymonitorpro.notify.NOTIFICATION_LINK_KEY
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

fun MainViewController() = ComposeUIViewController {
    installNotificationDelegate()
    App()
}

/**
 * Held in a property because `UNUserNotificationCenter.delegate` is a weak reference: a
 * delegate created inline is collected the moment this function returns, and the taps then
 * go nowhere with no error to show for it.
 */
private var notificationDelegate: AlertNotificationDelegate? = null

private fun installNotificationDelegate() {
    if (notificationDelegate != null) return
    val delegate = AlertNotificationDelegate()
    notificationDelegate = delegate
    UNUserNotificationCenter.currentNotificationCenter().setDelegate(delegate)
}

/**
 * Turns a tapped alert into a live view with the sound already on.
 *
 * The link rides in the notification's `userInfo` (see `notifyAlert`), so the tap carries
 * enough to reconnect to that specific camera rather than merely reopening the app.
 */
private class AlertNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val link = didReceiveNotificationResponse.notification.request.content
            .userInfo[NOTIFICATION_LINK_KEY] as? String
        if (link != null) DeepLinks.offerUri(link)
        withCompletionHandler()
    }

    /**
     * iOS suppresses a notification while its own app is in the foreground. Here that is
     * exactly backwards: the parent watching one camera should still be told the *other*
     * one heard something.
     */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: platform.UserNotifications.UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound,
        )
    }
}
