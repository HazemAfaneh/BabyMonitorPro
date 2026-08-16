package com.hazemafaneh.babymonitorpro.notify

/**
 * TODO(platform): the Notification API needs a permission prompt tied to a user gesture.
 * The web viewer shows the inline banner only.
 */
actual fun notifyAlert(cameraName: String, message: String) = Unit
