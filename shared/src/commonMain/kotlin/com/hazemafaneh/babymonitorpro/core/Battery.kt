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
    /**
     * Degrees Celsius, or -1 where unknown.
     *
     * The nursery phone is the one device in the house being asked to encode video for eight
     * hours while lying face-down on a shelf, frequently under a blanket or propped against
     * bedding, and frequently on a charger at the same time. That is how phones cook. It is
     * the other half of "will this thing still be working at 4am", and it comes free — the
     * same battery broadcast that carries the charge carries the temperature.
     */
    val temperatureC: Float = -1f,
) {
    val known: Boolean get() = percent in 0..100

    /**
     * Worth warning about: under a fifth, and not on a charger.
     *
     * A charging phone at 8% is fine — it is filling up, and the parent has already done the
     * thing the warning would ask for.
     */
    val low: Boolean get() = known && !charging && percent <= LOW_PERCENT

    val temperatureKnown: Boolean get() = temperatureC > 0f

    /**
     * Warm enough to mention: the phone is working hard, and probably somewhere with no air.
     *
     * A phone idling sits in the twenties; encoding video takes it into the thirties, which is
     * normal and not worth a colour. Forty is where throttling starts on most hardware, and
     * where a parent might want to move it off the bedding or take it off the charger.
     */
    val warm: Boolean get() = temperatureKnown && temperatureC >= WARM_C

    /** Hot enough to act on — sustained temperatures here shorten a battery's life. */
    val hot: Boolean get() = temperatureKnown && temperatureC >= HOT_C

    private companion object {
        const val LOW_PERCENT = 20
        const val WARM_C = 40f
        const val HOT_C = 45f
    }
}

/** The camera device's own battery, read fresh. Cheap enough to call per status message. */
expect fun readBattery(): BatteryState
