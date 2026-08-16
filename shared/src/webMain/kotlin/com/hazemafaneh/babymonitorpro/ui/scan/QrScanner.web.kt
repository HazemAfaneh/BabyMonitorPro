package com.hazemafaneh.babymonitorpro.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Web users read the address off the camera screen; no camera access is requested. */
actual val qrScanningSupported: Boolean = false

@Composable
actual fun QrScanner(modifier: Modifier, onResult: (String) -> Unit) = Unit
