package com.hazemafaneh.babymonitorpro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * What a colour *means*, as opposed to which Material role it happens to be.
 *
 * The reason this layer exists: before it, `primary` painted the live dot, the pairing
 * address, the sensitivity label and the alert banner — four unrelated meanings in one hue,
 * so none of them signalled anything. Amber is now reserved for "live and healthy",
 * and the way that discipline holds is that a call site asks for [statusLive] rather than
 * reaching for `primary` because it happens to look right.
 *
 * Every value maps onto a real `ColorScheme` role, so the three schemes stay the single
 * source of truth and this adds no colours of its own.
 */
@Immutable
data class BmpSemantic(
    /** Live and healthy, and nothing else. The live dot, the broadcasting dot, sound-on. */
    val statusLive: Color,
    /** Working, but not with everything: test pattern, sound-only, no video yet. */
    val statusDegraded: Color,
    /** Still deciding: connecting, starting, reconnecting. Never alarming. */
    val statusWaiting: Color,
    /** Something is wrong and the parent can act: port taken, camera stopped. */
    val statusFault: Color,
    /** The "on your WiFi" line, everywhere it appears. Used for nothing else. */
    val privacy: Color,
)

val LocalBmpSemantic = staticCompositionLocalOf {
    // Never read in practice — BabyMonitorTheme always provides a real one. The fallback is
    // the day scheme's values so a stray preview outside the theme is merely plain, not black.
    BmpSemantic(
        statusLive = Color(0xFFFFC66B),
        statusDegraded = Color(0xFF9AA6E8),
        statusWaiting = Color(0xFFB6BACB),
        statusFault = Color(0xFFE79187),
        privacy = Color(0xFF86C9AE),
    )
}

/** Reads the semantic layer. Mirrors how `MaterialTheme.colorScheme` is reached. */
object BmpTheme {
    val semantic: BmpSemantic
        @Composable @ReadOnlyComposable get() = LocalBmpSemantic.current
}

/**
 * The spacing scale. Named rather than inlined so a screen cannot invent 13.dp, and so the
 * tablet and desktop layouts can swap one gutter value instead of auditing every padding.
 */
object Space {
    /** Label to its value. */
    val xxs = 4.dp
    /** Inside a row. */
    val xs = 8.dp
    /** Between cards. */
    val sm = 12.dp
    /** Card internals. */
    val md = 16.dp
    /** Screen gutter on a phone. */
    val lg = 20.dp
    /** Card padding, role cards. */
    val xl = 24.dp
    /** Screen gutter on a tablet or desktop. */
    val xxl = 36.dp
}

object Touch {
    /**
     * Every interactive row and pill. A 32dp target on a phone held one-handed over a cot
     * is a target you miss.
     */
    val min = 48.dp
}
