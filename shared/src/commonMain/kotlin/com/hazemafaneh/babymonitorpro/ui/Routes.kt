package com.hazemafaneh.babymonitorpro.ui

object Routes {
    const val ROLE = "role"
    const val CAMERA = "camera"

    /**
     * Its own destination rather than a tab inside [CAMERA], so the system back button
     * returns to Confirm & Pair. As a tab, back would leave camera mode entirely and stop
     * the broadcast — which is the last thing a parent wants after adjusting a setting.
     */
    const val CAMERA_SETTINGS = "camera/settings"

    const val FIND = "find"
    const val LIVE = "live"
}
