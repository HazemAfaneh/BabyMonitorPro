package com.hazemafaneh.babymonitorpro.core

actual fun appVersion(): String {
    val context = AndroidPlatformContext.applicationContext ?: return FALLBACK_VERSION
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_VERSION
}
