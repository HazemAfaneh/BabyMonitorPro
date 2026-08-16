package com.hazemafaneh.babymonitorpro

import android.app.Application
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext

class BabyMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Shared code reaches Android services (preferences, camera, mDNS) through this.
        AndroidPlatformContext.install(this)
    }
}
