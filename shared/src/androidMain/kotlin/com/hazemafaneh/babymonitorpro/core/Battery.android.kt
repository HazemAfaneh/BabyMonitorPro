package com.hazemafaneh.babymonitorpro.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Read from the sticky `ACTION_BATTERY_CHANGED` broadcast, with [BatteryManager] as backup.
 *
 * `BatteryManager.isCharging()` looked like the obvious call and it lies on real hardware: a
 * Huawei reporting `AC powered: true` and `status: 2` (charging) through dumpsys still came
 * back false from `isCharging`, so the viewer showed a draining battery for a phone on a
 * charger — which is the one distinction this reading exists to make.
 *
 * The sticky broadcast is the source Android itself uses. It costs no registration (a null
 * receiver returns the last value immediately) and it carries the status, the plug and the
 * level together, so all three agree with each other.
 */
actual fun readBattery(): BatteryState {
    val context = AndroidPlatformContext.applicationContext ?: return BatteryState()

    val sticky = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    if (sticky != null) {
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        // Plugged in counts as charging even when the status says FULL: a phone at 100% on a
        // charger is not draining, which is the whole question. And plenty of devices report
        // a plug with a status of UNKNOWN.
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        if (percent >= 0) return BatteryState(percent = percent, charging = charging)
    }

    // Nothing sticky yet — very early in the process life. Ask the service directly.
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        ?: return BatteryState()
    return runCatching {
        BatteryState(
            percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = manager.isCharging,
        )
    }.getOrElse { BatteryState() }
}
