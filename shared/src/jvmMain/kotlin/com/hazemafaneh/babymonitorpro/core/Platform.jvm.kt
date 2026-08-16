package com.hazemafaneh.babymonitorpro.core

import java.net.InetAddress

actual fun platformName(): String = "Desktop"

actual fun defaultDeviceName(): String =
    runCatching { InetAddress.getLocalHost().hostName.substringBefore('.') }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: (System.getProperty("os.name") ?: "Desktop")

actual val supportsCameraRole: Boolean = true

/** Desktop alerts use an in-app banner plus a tray notification; see NotificationCenter. */
actual val supportsNotifications: Boolean = true
