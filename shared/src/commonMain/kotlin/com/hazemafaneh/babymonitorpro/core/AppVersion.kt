package com.hazemafaneh.babymonitorpro.core

/**
 * What this build calls itself, for the About page.
 *
 * Asked of the platform rather than kept as a constant here, because the number that matters
 * is the one in the package a parent actually installed. A constant in shared code drifts from
 * `versionName` the first time anybody bumps one and not the other, and a version line that is
 * wrong is worse than no version line — it is the first thing anybody asks for when a bug
 * report arrives.
 */
expect fun appVersion(): String

/** Used where the platform has no packaging of its own to ask. Keep in step with Gradle. */
internal const val FALLBACK_VERSION = "1.0"
