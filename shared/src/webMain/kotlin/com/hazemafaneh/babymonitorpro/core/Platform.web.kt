package com.hazemafaneh.babymonitorpro.core

actual fun platformName(): String = "Web"

actual fun defaultDeviceName(): String = "Browser"

/** A browser cannot bind a listening socket, so this device can only ever watch. */
actual val supportsCameraRole: Boolean = false

actual val supportsNotifications: Boolean = false

/** A TV browser is possible, but nothing here can tell one from a small laptop. */
actual val isTelevision: Boolean = false
