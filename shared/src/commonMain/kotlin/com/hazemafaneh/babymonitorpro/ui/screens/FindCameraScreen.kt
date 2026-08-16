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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
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
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.discovery.createBrowser
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyNote
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.scan.QrScanner
import com.hazemafaneh.babymonitorpro.ui.scan.qrScanningSupported
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

@Composable
fun FindCameraScreen(
    onConnect: (CameraEndpoint, String?) -> Unit,
    onBack: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    var manualHost by remember { mutableStateOf(settings.lastManualHost) }
    var manualPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

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
            onCancel = { scanning = false },
            onCode = { raw ->
                val parsed = PairingUri.parse(raw)
                if (parsed != null) {
                    scanning = false
                    onConnect(
                        CameraEndpoint(
                            name = parsed.host,
                            host = parsed.host,
                            port = parsed.port,
                            pinRequired = parsed.pin != null,
                            source = CameraEndpoint.Source.QR,
                        ),
                        parsed.pin,
                    )
                }
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Find a camera",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Cameras on this WiFi network appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Nearby") {
            when {
                browser == null -> Text(
                    text = "This device cannot search the network. Enter the address shown " +
                        "on the camera screen below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                discovered.isEmpty() -> Text(
                    text = "Looking for cameras… Open BabyMonitor Pro on the nursery device " +
                        "and choose \"Use this device as Camera\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column {
                    for (camera in discovered) {
                        DiscoveredRow(
                            camera = camera,
                            onClick = {
                                onConnect(camera, manualPin.takeIf { camera.pinRequired && it.isNotBlank() })
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Connect by IP") {
            OutlinedTextField(
                value = manualHost,
                onValueChange = {
                    manualHost = it
                    error = null
                },
                label = { Text("Address, e.g. 192.168.1.42:8080") },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manualPin,
                onValueChange = { manualPin = it.filter(Char::isDigit).take(6) },
                label = { Text("PIN (only if the camera asks for one)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val parsed = PairingUri.parse(manualHost)
                        ?: PairingUri.parseHostPort(manualHost)?.let { (host, port) ->
                            PairingUri.Parsed(host, port, null)
                        }
                    if (parsed == null) {
                        error = "That does not look like an address."
                    } else {
                        settings.lastManualHost = manualHost
                        val pin = parsed.pin ?: manualPin.takeIf { it.isNotBlank() }
                        onConnect(
                            CameraEndpoint(
                                name = parsed.host,
                                host = parsed.host,
                                port = parsed.port,
                                pinRequired = pin != null,
                                source = CameraEndpoint.Source.MANUAL,
                            ),
                            pin,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Connect")
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            if (qrScanningSupported) {
                TextButton(onClick = { scanning = true }) { Text("Scan QR") }
            }
        }

        Spacer(Modifier.height(12.dp))
        PrivacyNote()
        Spacer(Modifier.height(24.dp))
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
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = camera.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (camera.pinRequired) "${camera.id} · PIN required" else camera.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanOverlay(
    onCancel: () -> Unit,
    onCode: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        QrScanner(modifier = Modifier.fillMaxSize(), onResult = onCode)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Point at the code on the camera screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
