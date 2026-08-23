package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.server.BroadcastConfig
import com.hazemafaneh.babymonitorpro.server.BroadcastState
import com.hazemafaneh.babymonitorpro.server.Broadcaster
import com.hazemafaneh.babymonitorpro.server.createBroadcaster
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.KeepScreenAwake
import com.hazemafaneh.babymonitorpro.ui.rememberCapturePermissions
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyNote
import com.hazemafaneh.babymonitorpro.ui.components.QrCode
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.video.decodeJpegFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun CameraScreen(
    onNightDimChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val scope = rememberCoroutineScope()
    val broadcaster = remember { createBroadcaster() }

    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var pinEnabled by remember { mutableStateOf(settings.pinEnabled) }
    var pin by remember { mutableStateOf(settings.pin) }
    var sensitivity by remember { mutableStateOf(settings.motionSensitivity.toFloat()) }
    var nightDim by remember { mutableStateOf(settings.nightMode) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    val stateFlow = remember(broadcaster) {
        broadcaster?.state ?: MutableStateFlow(BroadcastState(running = false))
    }
    val state by stateFlow.collectAsState()

    KeepScreenAwake(enabled = state.running)

    // Self-preview decodes the same frames the viewers receive, so what the parent sees
    // here is exactly what is going out.
    LaunchedEffect(broadcaster) {
        broadcaster?.frames?.collect { jpeg -> preview = decodeJpegFrame(jpeg) }
    }

    // Android must ask before the first frame; elsewhere this reports granted immediately.
    val permissions = rememberCapturePermissions()
    // Both, not just the camera: on Android 14+ a foreground service that claims the
    // microphone type without RECORD_AUDIO granted is killed by the system, and the HTTP
    // server dies with the process.
    LaunchedEffect(permissions.cameraGranted, permissions.microphoneGranted) {
        if (!permissions.cameraGranted || !permissions.microphoneGranted) permissions.request()
    }

    LaunchedEffect(broadcaster, permissions.cameraGranted, permissions.microphoneGranted) {
        broadcaster?.start(
            BroadcastConfig(
                deviceName = deviceName,
                pin = pin.takeIf { pinEnabled },
                motionSensitivity = sensitivity.toInt(),
                soundSensitivity = sensitivity.toInt(),
            ),
        )
    }

    DisposableEffect(broadcaster) {
        onDispose { broadcaster?.requestStop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .alpha(if (nightDim) NIGHT_DIM_ALPHA else 1f),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Camera mode",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                broadcaster == null -> "This device cannot broadcast."
                !permissions.cameraGranted ->
                    "Camera access is off. Allow it to send video — sound still works without it."
                state.syntheticVideo -> "No camera available — sending a test pattern."
                state.running -> "Broadcasting on this WiFi network."
                else -> "Starting…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(preview) { bitmap ->
                if (bitmap == null) {
                    Text(
                        text = "Waiting for video…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Self preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        state.lastError?.let { problem ->
            Surface(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = problem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionCard(title = "Pairing") {
            val address = state.primaryAddress
            if (address == null) {
                Text(
                    text = "Looking for this device's WiFi address…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "$address:${state.port}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Type this into another device, or scan the code below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                QrCode(
                    content = PairingUri.build(address, state.port, pin.takeIf { pinEnabled }),
                    modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "This camera") {
            OutlinedTextField(
                value = deviceName,
                onValueChange = {
                    deviceName = it
                    settings.deviceName = it
                },
                label = { Text("Device name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Viewers connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${state.viewerCount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.canSwitchCamera) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.fillMaxWidth(0.6f)) {
                        Text(
                            text = if (state.usingFrontCamera) "Front camera" else "Rear camera",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Point the other lens at the cot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { broadcaster?.switchCamera() }) {
                        Text(if (state.usingFrontCamera) "Use rear" else "Use front")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Alerts") {
            Text(
                text = "Sensitivity ${sensitivity.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = {
                    val value = sensitivity.toInt()
                    settings.motionSensitivity = value
                    settings.soundSensitivity = value
                    broadcaster?.setSensitivity(value, value)
                },
                valueRange = 0f..100f,
            )
            Text(
                text = "Higher means smaller movements and quieter sounds raise an alert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Comfort and access") {
            ToggleRow(
                label = "Night dim",
                description = "Dims this screen without stopping the stream",
                checked = nightDim,
                onCheckedChange = {
                    nightDim = it
                    settings.nightMode = it
                    onNightDimChanged(it)
                },
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                label = "Require a PIN",
                description = "Viewers must enter a 6-digit code",
                checked = pinEnabled,
                onCheckedChange = {
                    pinEnabled = it
                    settings.pinEnabled = it
                    if (it && pin.length != PIN_LENGTH) {
                        pin = generatePin()
                        settings.pin = pin
                    }
                    scope.launch { restart(broadcaster, deviceName, pin.takeIf { _ -> pinEnabled }) }
                },
            )
            if (pinEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pin,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.fillMaxWidth(0.1f))
                    TextButton(onClick = {
                        pin = generatePin()
                        settings.pin = pin
                        scope.launch { restart(broadcaster, deviceName, pin) }
                    }) { Text("New code") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                broadcaster?.requestStop()
                onStop()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("Stop broadcasting")
        }

        Spacer(Modifier.height(16.dp))
        PrivacyNote()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth(0.75f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private suspend fun restart(
    broadcaster: Broadcaster?,
    deviceName: String,
    pin: String?,
) {
    broadcaster ?: return
    broadcaster.start(BroadcastConfig(deviceName = deviceName, pin = pin))
}

private fun generatePin(): String =
    (1..PIN_LENGTH).map { (0..9).random() }.joinToString("")

private const val PIN_LENGTH = 6
private const val NIGHT_DIM_ALPHA = 0.45f
