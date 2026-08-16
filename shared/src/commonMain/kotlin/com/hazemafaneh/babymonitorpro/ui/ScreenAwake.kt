package com.hazemafaneh.babymonitorpro.ui

import androidx.compose.runtime.Composable

/** Holds the screen on while a camera is broadcasting. No-op where the platform has no say. */
@Composable
expect fun KeepScreenAwake(enabled: Boolean)
