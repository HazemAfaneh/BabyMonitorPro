package com.hazemafaneh.babymonitorpro.core

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

actual object BackgroundAccess {

    /** What a device that has no such screen resolves the request to instead. */
    private const val STUB_ACTIVITY = "EmptyStub"


    actual val isRelevant: Boolean = true

    actual fun isUnrestricted(): Boolean {
        val context = AndroidPlatformContext.applicationContext ?: return false
        val power = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    actual fun canRequest(): Boolean {
        val context = AndroidPlatformContext.applicationContext ?: return false
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        // Never on a television. It is mains powered, it has no battery optimisation, and —
        // the part that caught this out — Android TV *does* resolve the intent: to
        // `com.android.tv.settings.EmptyStubActivity`, a deliberate no-op. A non-null
        // resolution is therefore not evidence that anything will happen, so the stub is
        // named and rejected, and the whole question is skipped on a TV in any case.
        if (isTelevision) return false

        return runCatching {
            val resolved = context.packageManager.resolveActivity(intent, 0)
            val name = resolved?.activityInfo?.name.orEmpty()
            resolved != null && !name.contains(STUB_ACTIVITY, ignoreCase = true)
        }.getOrDefault(false)
    }

    actual fun request() {
        val context = AndroidPlatformContext.applicationContext ?: return
        if (isUnrestricted()) return
        // The targeted request, which shows a system dialog naming this app. Some builds
        // refuse to resolve it (it is policy-gated on a few devices), so the general battery
        // settings screen is the fallback rather than nothing happening at all.
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching { context.startActivity(direct) }.isSuccess
        if (!started) openSystemSettings()
    }

    actual fun openSystemSettings() {
        val context = AndroidPlatformContext.applicationContext ?: return
        val settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(settings) }
    }
}
