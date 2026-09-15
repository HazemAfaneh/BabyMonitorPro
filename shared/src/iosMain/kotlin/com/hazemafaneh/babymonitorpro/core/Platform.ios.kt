package com.hazemafaneh.babymonitorpro.core

import platform.UIKit.UIDevice

actual fun platformName(): String = "iOS"

actual fun defaultDeviceName(): String = UIDevice.currentDevice.name

actual val supportsCameraRole: Boolean = true

actual val supportsNotifications: Boolean = true

/** tvOS is not a target; every iOS device this runs on is handheld. */
actual val isTelevision: Boolean = false
