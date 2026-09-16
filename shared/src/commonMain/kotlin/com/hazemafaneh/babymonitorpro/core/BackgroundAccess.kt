package com.hazemafaneh.babymonitorpro.core

/**
 * Whether the system will let this app keep working while nobody is looking at it.
 *
 * A foreground service and a wake lock are the *supported* way to keep a stream alive, and on
 * stock Android they are enough. They are not the whole story on a real phone:
 *
 * - **Battery optimisation.** An app outside the exemption list is subject to Doze and to
 *   app-standby buckets, which throttle exactly the things a monitor needs — network, alarms,
 *   the ability to be restarted after a kill.
 * - **The manufacturer's own killer.** TECNO's HiOS and Huawei's EMUI keep their own lists,
 *   under names like "protected apps" or "auto-launch", and enforce them regardless of what
 *   Android says. Nothing can request these; the parent has to allow it, once, by hand.
 *
 * So this is a permission the app must *ask* for rather than declare — and one the settings
 * screen has to be honest about when it has not been granted, because the failure it produces
 * is a monitor that quietly stops in the night.
 */
expect object BackgroundAccess {
    /** True when this app is exempt from battery optimisation. */
    fun isUnrestricted(): Boolean

    /**
     * Whether this device offers the exemption at all.
     *
     * **A television does not.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` resolves to no
     * activity on Android TV — it is a battery setting, and a TV is plugged into the wall.
     * Asking there does nothing, so the screen must not warn about a restriction the parent
     * has no way to lift, on a device that is not subject to it in the first place.
     */
    fun canRequest(): Boolean

    /** Asks the system for the exemption. Safe to call when already granted. */
    fun request()

    /**
     * Opens the place a parent can find the manufacturer's own restrictions.
     *
     * Deliberately the app's own settings page rather than a guess at an OEM-specific screen:
     * the names differ per manufacturer, the activities are not stable, and landing somebody
     * on a crash is worse than landing them one tap away from the right switch.
     */
    fun openSystemSettings()

    /** False where none of this applies — desktop, browser, iOS. */
    val isRelevant: Boolean
}
