package com.hazemafaneh.babymonitorpro.notify

import android.content.Context

/**
 * The app's own mark for the status bar, resolved by name at post time.
 *
 * Resolved rather than referenced because these notifications are built in `:shared`, which
 * has its own `R` and cannot see the application module's drawables. The Live Update already
 * finds `ic_live_activity` this way, so this follows the pattern that is here rather than
 * adding a second mechanism beside it.
 *
 * Falls back to a platform icon if the drawable is ever renamed: a notification with a stock
 * glyph is a cosmetic problem, and one that throws at post time is a missed alert.
 */
internal fun notificationIcon(context: Context): Int {
    val own = context.resources.getIdentifier(SMALL_ICON, "drawable", context.packageName)
    return if (own != 0) own else android.R.drawable.stat_notify_more
}

private const val SMALL_ICON = "ic_notification"
