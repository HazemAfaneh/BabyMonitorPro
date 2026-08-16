package com.hazemafaneh.babymonitorpro.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** False on desktop and web, where the Scan action is hidden rather than shown broken. */
expect val qrScanningSupported: Boolean

/**
 * Full-bleed camera viewfinder that reports the first pairing code it sees.
 * [onResult] may fire more than once; callers should act on the first value only.
 */
@Composable
expect fun QrScanner(
    modifier: Modifier,
    onResult: (String) -> Unit,
)
