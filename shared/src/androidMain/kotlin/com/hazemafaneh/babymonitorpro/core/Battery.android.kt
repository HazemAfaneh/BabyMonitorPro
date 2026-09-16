package com.hazemafaneh.babymonitorpro.core

import android.content.Context
import android.os.BatteryManager

actual fun readBattery(): BatteryState {
    val context = AndroidPlatformContext.applicationContext ?: return BatteryState()
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        ?: return BatteryState()
    return runCatching {
        BatteryState(
            percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            // isCharging covers AC, USB and wireless, which is the distinction that matters:
            // the question is whether the number is falling, not what it is plugged into.
            charging = manager.isCharging,
        )
    }.getOrElse { BatteryState() }
}
