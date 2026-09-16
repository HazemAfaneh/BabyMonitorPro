package com.hazemafaneh.babymonitorpro.core

/**
 * Desktops do not report a battery portably, and the machine acting as a camera is almost
 * always on mains anyway. Unknown is the honest answer; the viewer simply omits the reading.
 */
actual fun readBattery(): BatteryState = BatteryState()
