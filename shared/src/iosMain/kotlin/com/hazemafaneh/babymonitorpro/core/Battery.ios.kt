package com.hazemafaneh.babymonitorpro.core

import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState

actual fun readBattery(): BatteryState {
    val device = UIDevice.currentDevice
    // iOS reports -1 for everything until monitoring is switched on, and switching it on is
    // idempotent, so it is done here rather than in some startup path that the camera role
    // might never run through.
    device.batteryMonitoringEnabled = true
    val level = device.batteryLevel
    return BatteryState(
        percent = if (level < 0f) -1 else (level * 100).toInt(),
        charging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull,
    )
}
