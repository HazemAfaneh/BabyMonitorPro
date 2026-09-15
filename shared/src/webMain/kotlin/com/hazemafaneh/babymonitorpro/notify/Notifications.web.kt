package com.hazemafaneh.babymonitorpro.notify

import com.hazemafaneh.babymonitorpro.core.CameraEndpoint

/**
 * TODO(platform): the Notification API needs a permission prompt tied to a user gesture.
 * The web viewer shows the inline banner only.
 */
actual fun notifyAlert(endpoint: CameraEndpoint, alert: CameraAlert) = Unit
