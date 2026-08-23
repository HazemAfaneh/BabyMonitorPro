package com.hazemafaneh.babymonitorpro.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.ui.theme.Space

/**
 * How much room there is, in the three sizes this app actually lays out differently.
 *
 * Measured off `LocalWindowInfo` rather than pulling in the Material window-size-class
 * artifact: three breakpoints do not justify a dependency, and this compiles unchanged on
 * all five targets including wasm.
 */
enum class WindowClass {
    /** A phone. One column, chrome auto-hides, the picture is the interface. */
    COMPACT,

    /** A tablet, or a small desktop window. Two columns where the content wants them. */
    MEDIUM,

    /** A desktop window or a landscape tablet. Persistent chrome, side rails. */
    EXPANDED;

    val isCompact: Boolean get() = this == COMPACT
    val atLeastMedium: Boolean get() = this != COMPACT
}

@Composable
@ReadOnlyComposable
fun rememberWindowClass(): WindowClass {
    val width = windowWidth()
    return when {
        width < MEDIUM_BREAKPOINT -> WindowClass.COMPACT
        width < EXPANDED_BREAKPOINT -> WindowClass.MEDIUM
        else -> WindowClass.EXPANDED
    }
}

/** Window width in dp. Zero before the first measure, which reads as COMPACT — the safe default. */
@Composable
@ReadOnlyComposable
fun windowWidth(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
}

@Composable
@ReadOnlyComposable
fun windowHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
}

/** Screen gutter: tighter on a phone, where horizontal space is the scarce thing. */
@Composable
@ReadOnlyComposable
fun WindowClass.gutter(): Dp = if (isCompact) Space.lg else Space.xxl

private val MEDIUM_BREAKPOINT = 600.dp
private val EXPANDED_BREAKPOINT = 840.dp
