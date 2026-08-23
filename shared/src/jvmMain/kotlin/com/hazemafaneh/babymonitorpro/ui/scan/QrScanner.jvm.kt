package com.hazemafaneh.babymonitorpro.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop pairs by typing the address shown on the camera screen. */
actual val qrScanningSupported: Boolean = false

@Composable
actual fun QrScanner(
    modifier: Modifier,
    onResult: (String) -> Unit,
    onUnavailable: () -> Unit,
) = Unit
