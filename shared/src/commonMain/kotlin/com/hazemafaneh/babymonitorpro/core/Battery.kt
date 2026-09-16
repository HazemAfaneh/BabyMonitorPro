package com.hazemafaneh.babymonitorpro.core

/**
 * What the device doing the filming has left.
 *
 * Read on the camera and sent to viewers in every `Status`, because the nursery phone is
 * the one whose battery a parent cannot see: it is face-down on a shelf in a dark room, and
 * the failure it is heading for — a flat phone at 4am — is silent and total. The viewer is
 * in the parent's hand and reports its own battery perfectly well already.
 */
data class BatteryState(
    /** 0..100, or -1 where the platform will not say. */
    val percent: Int = -1,
    val charging: Boolean = false,
) {
    val known: Boolean get() = percent in 0..100

    /**
     * Worth warning about: under a fifth, and not on a charger.
     *
     * A charging phone at 8% is fine — it is filling up, and the parent has already done the
     * thing the warning would ask for.
     */
    val low: Boolean get() = known && !charging && percent <= LOW_PERCENT

    private companion object {
        const val LOW_PERCENT = 20
    }
}

/** The camera device's own battery, read fresh. Cheap enough to call per status message. */
expect fun readBattery(): BatteryState
