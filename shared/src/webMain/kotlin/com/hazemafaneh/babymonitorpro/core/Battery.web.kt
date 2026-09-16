package com.hazemafaneh.babymonitorpro.core

/** The browser is viewer-only, so nothing ever asks it for a camera device's battery. */
actual fun readBattery(): BatteryState = BatteryState()
