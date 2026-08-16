package com.hazemafaneh.babymonitorpro.core

actual fun platformName(): String = "Web"

actual fun defaultDeviceName(): String = "Browser"

/** A browser cannot bind a listening socket, so this device can only ever watch. */
actual val supportsCameraRole: Boolean = false

actual val supportsNotifications: Boolean = false
