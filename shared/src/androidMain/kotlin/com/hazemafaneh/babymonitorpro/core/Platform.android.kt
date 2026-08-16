package com.hazemafaneh.babymonitorpro.core

import android.content.Context
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
