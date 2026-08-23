package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.discovery.createBrowser
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.components.Chevron
import com.hazemafaneh.babymonitorpro.ui.components.MonoValue
import com.hazemafaneh.babymonitorpro.ui.components.NavRow
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyLine
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.components.SectionLabel
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.scan.QrScanner
import com.hazemafaneh.babymonitorpro.ui.scan.qrScanningSupported
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * Three routes to one camera, ranked rather than stacked.
 *
 * Discovery owns the top card because it is the route that works with no effort; QR and
 * manual entry sit below it as two small equals. Previously all three had equal weight, so
 * the fallback competed for attention with the good path — and on web, where discovery
 * cannot work at all, the only route that *does* work was the one buried at the bottom.
 */
@Composable
fun FindCameraScreen(
    onConnect: (CameraEndpoint) -> Unit,
    onBack: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val window = rememberWindowClass()

    var manualHost by remember { mutableStateOf(settings.lastManualHost) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scannerDenied by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }

    val browser = remember { createBrowser() }
    val discovered by remember(browser) {
        browser?.cameras ?: MutableStateFlow(emptyList<CameraEndpoint>())
    }.collectAsState()

    DisposableEffect(browser) {
        browser?.start()
        onDispose { browser?.stop() }
    }

    if (scanning) {
        ScanOverlay(
            denied = scannerDenied,
            onCancel = { scanning = false },
            onTypeInstead = {
                scanning = false
                manualOpen = true
            },
            onUnavailable = { scannerDenied = true },
            onCode = { raw ->
                val parsed = PairingUri.parse(raw) ?: return@ScanOverlay
                scanning = false
                onConnect(
                    CameraEndpoint(
                        name = parsed.host,
                        host = parsed.host,
                        port = parsed.port,
                        source = CameraEndpoint.Source.QR,
                    ),
                )
            },
        )
        return
    }

    // Where discovery is impossible the manual field is not a consolation prize, it is the
    // only door — so it opens as the primary control rather than behind a disclosure.
    val discoveryImpossible = browser == null
    val showManual = manualOpen || discoveryImpossible

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = window.gutter()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Touch.min),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Find a camera",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = onBack) { Text("Back") }
            }

            Spacer(Modifier.height(Space.sm))

            SectionCard(title = if (discoveryImpossible) "On this network" else "On this network") {
                SectionLabel(if (discoveryImpossible) "not available here" else "mDNS · local only")
                Spacer(Modifier.height(Space.sm))
                when {
                    discoveryImpossible -> EmptyNote(
                        title = "This browser cannot search your network",
                        note = "Browsers have no way to look for devices on your WiFi. " +
                            "Type the address the camera screen shows.",
                    )

                    discovered.isEmpty() -> EmptyNote(
                        title = "Still looking",
                        note = "Open BabyMonitor Pro on the nursery device and choose " +
                            "\"Use this device as Camera\".",
                    )

                    else -> Column {
                        for (camera in discovered) {
                            // The row is the action. One result should be one obvious tap,
                            // not an address to read and a Connect button to find.
                            DiscoveredRow(
                                camera = camera,
                                onClick = { onConnect(camera) },
                            )
                        }
                    }
                }
            }

            if (!discoveryImpossible) {
                Spacer(Modifier.height(Space.sm))
                SectionCard(title = "Other ways in") {
                    if (qrScanningSupported) {
                        NavRow(
                            label = "Scan QR",
                            description = "Both devices in hand",
                            onClick = {
                                scannerDenied = false
                                scanning = true
                            },
                        )
                    }
                    NavRow(
                        label = "Type address",
                        description = "If nothing appears above",
                        onClick = { manualOpen = !manualOpen },
                    )
                }
            }

            if (showManual) {
                Spacer(Modifier.height(Space.sm))
                SectionCard(title = "Type the address") {
                    OutlinedTextField(
                        value = manualHost,
                        onValueChange = {
                            manualHost = it
                            error = null
                        },
                        label = { Text("Address") },
                        placeholder = { Text("192.168.1.42") },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                    Button(
                        onClick = {
                            val parsed = parseAddress(manualHost)
                            if (parsed == null) {
                                // Show the shape wanted. "That does not look like an
                                // address" tells a tired parent nothing they can act on.
                                error = "That is not a full address. It should look like " +
                                    "192.168.1.42, or 192.168.1.42:8080 if the port was changed."
                            } else {
                                settings.lastManualHost = manualHost
                                onConnect(
                                    CameraEndpoint(
                                        name = parsed.host,
                                        host = parsed.host,
                                        port = parsed.port,
                                        source = CameraEndpoint.Source.MANUAL,
                                    ),
                                )
                            }
                        },
                        // Disabled rather than allowed to fail silently.
                        enabled = manualHost.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Connect")
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            PrivacyLine(address = null)
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = if (discoveryImpossible) {
                    "Nothing about this connection leaves your network — the browser talks " +
                        "straight to the camera."
                } else {
                    "Searching this WiFi network only. No device on the internet can be " +
                        "found here, and none is contacted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun EmptyNote(title: String, note: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoveredRow(
    camera: CameraEndpoint,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxs)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(Space.md).heightIn(min = Touch.min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The row carries the reason to choose — which device — rather than an id
                // the parent has to decode.
                MonoValue(
                    text = camera.id,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(Space.xs))
            Chevron()
        }
    }
}

@Composable
private fun ScanOverlay(
    denied: Boolean,
    onCancel: () -> Unit,
    onTypeInstead: () -> Unit,
    onUnavailable: () -> Unit,
    onCode: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!denied) {
            QrScanner(
                modifier = Modifier.fillMaxSize(),
                onResult = onCode,
                onUnavailable = onUnavailable,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = if (denied) {
                        "Scanning is unavailable without camera access."
                    } else {
                        "Point at the code on the camera screen"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                // A dead end has to offer the other route. Before, a denied camera left the
                // overlay telling the parent to point at a code the phone could not see.
                if (denied) {
                    TextButton(onClick = onTypeInstead) { Text("Type the address") }
                }
            }
        }
    }
}

/** Accepts a full `bmpro://` link or a bare `host[:port]`. */
private fun parseAddress(input: String): PairingUri.Parsed? =
    PairingUri.parse(input)
        ?: PairingUri.parseHostPort(input)?.let { (host, port) ->
            PairingUri.Parsed(host, port)
        }

private val CONTENT_MAX_WIDTH = 560.dp
