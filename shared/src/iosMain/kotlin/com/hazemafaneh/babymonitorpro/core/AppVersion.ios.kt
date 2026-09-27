package com.hazemafaneh.babymonitorpro.core

import platform.Foundation.NSBundle

actual fun appVersion(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
        ?.takeIf { it.isNotBlank() }
        ?: FALLBACK_VERSION
