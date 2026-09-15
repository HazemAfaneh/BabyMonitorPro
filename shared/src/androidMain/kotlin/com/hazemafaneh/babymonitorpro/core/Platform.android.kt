package com.hazemafaneh.babymonitorpro.core

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Set once from the Android Application/Activity. Kept here rather than threaded through
 * every constructor so commonMain stays free of Android types.
 */
object AndroidPlatformContext {
    @Volatile
    var applicationContext: Context? = null
        private set

    fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    fun require(): Context = checkNotNull(applicationContext) {
        "AndroidPlatformContext.install(context) must be called from Application.onCreate()"
    }
}

actual fun platformName(): String = "Android"

actual fun defaultDeviceName(): String {
    val model = Build.MODEL?.trim().orEmpty()
    val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
    return when {
        model.isEmpty() -> "Android device"
        manufacturer.isNotEmpty() && !model.startsWith(manufacturer, ignoreCase = true) ->
            "$manufacturer $model"
        else -> model
    }
}

actual val supportsCameraRole: Boolean = true

actual val supportsNotifications: Boolean = true

/**
 * Read from [UiModeManager] rather than from `FEATURE_LEANBACK`, because a phone-shaped APK
 * sideloaded onto a TV box has no leanback feature declared for it and is a television all
 * the same — which is exactly how this app arrives on one.
 *
 * Resolved once and cached: it cannot change for the life of the process, and it is read on
 * every recomposition that draws a focus ring.
 */
actual val isTelevision: Boolean by lazy {
    val context = AndroidPlatformContext.applicationContext ?: return@lazy false
    val modes = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    modes?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
