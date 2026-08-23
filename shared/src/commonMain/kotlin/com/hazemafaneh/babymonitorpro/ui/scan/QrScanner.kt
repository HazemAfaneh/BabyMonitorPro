package com.hazemafaneh.babymonitorpro.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** False on desktop and web, where the Scan action is hidden rather than shown broken. */
expect val qrScanningSupported: Boolean

/**
 * Full-bleed camera viewfinder that reports the first pairing code it sees.
 * [onResult] may fire more than once; callers should act on the first value only.
 *
 * [onUnavailable] fires when the camera cannot be used at all — permission refused, or no
 * usable device. The caller has to know, because a scanner that silently shows black is a
 * dead end telling the parent to point at a code the phone cannot see; the screen needs to
 * offer the other route instead.
 */
@Composable
expect fun QrScanner(
    modifier: Modifier,
    onResult: (String) -> Unit,
    onUnavailable: () -> Unit,
)
