package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.core.nowMillis
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
import com.hazemafaneh.babymonitorpro.ui.video.JpegFrameView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    // LocalClipboard, its replacement, takes a ClipEntry — and ClipEntry has no common
    // constructor in Compose Multiplatform 1.11: it wraps a ClipData on Android and a
    // Transferable on the JVM, so there is nothing to build one from in shared code.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val broadcaster = remember { createBroadcaster() }

    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var pinEnabled by remember { mutableStateOf(settings.pinEnabled) }
    var pin by remember { mutableStateOf(settings.pin) }
    var sensitivity by remember { mutableStateOf(settings.motionSensitivity.toFloat()) }
    var nightDim by remember { mutableStateOf(settings.nightMode) }
    var lastTouch by remember { mutableStateOf(nowMillis()) }
    var dimmed by remember { mutableStateOf(false) }

    val stateFlow = remember(broadcaster) {
        broadcaster?.state ?: MutableStateFlow(BroadcastState(running = false))
    }
    val state by stateFlow.collectAsState()

    KeepScreenAwake(enabled = state.running)

    val permissions = rememberCapturePermissions()

    // Both, not just the camera: on Android 14+ a foreground service that claims the
    // microphone type without RECORD_AUDIO granted is killed by the system, and the HTTP
    // server dies with the process.
    LaunchedEffect(permissions.needsRequest) {
        if (permissions.needsRequest) permissions.request()
    }

    // Keyed on the answer, not on the permissions themselves. Starting while the dialog is
    // still up starts twice — once with no camera, then again the moment the user taps
    // Allow — and the second start tears down the server, the pairing address and the QR
    // code the first one just put on screen. That double start is the flicker on entry.
    LaunchedEffect(broadcaster, permissions.resolved) {
        if (!permissions.resolved) return@LaunchedEffect
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

    // Night dim used to take hold the instant it was switched on and stay, which left the
    // parent reading their own settings at 45% opacity. It now settles after a moment and
    // any touch brings the screen back, so dimming for the room does not mean dimming for
    // whoever is still using it.
    LaunchedEffect(nightDim, lastTouch) {
        dimmed = false
        if (!nightDim) return@LaunchedEffect
        delay(DIM_SETTLE_MILLIS)
        dimmed = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Brighten the screen",
            ) { lastTouch = nowMillis() }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .alpha(if (dimmed) NIGHT_DIM_ALPHA else 1f),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Camera mode",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(16.dp))

        SelfPreview(
            frames = broadcaster?.frames,
            state = state,
            available = broadcaster != null,
            resolved = permissions.resolved,
            cameraGranted = permissions.cameraGranted,
        )

        // Only when there is something to do about it. A line of prose repeating what the
        // chip already says is a line the parent learns to skip.
        advisoryFor(
            available = broadcaster != null,
            resolved = permissions.resolved,
            cameraGranted = permissions.cameraGranted,
            synthetic = state.syntheticVideo,
        )?.let { advice ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = advice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                val pairing = "$address:${state.port}"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pairing,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    // Reading an address off one phone and typing it into another is the
                    // fallback when the camera is somewhere a QR code cannot be scanned.
                    TextButton(onClick = { clipboard.setText(AnnotatedString(pairing)) }) {
                        Text("Copy")
                    }
                }
                Spacer(Modifier.height(4.dp))
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

            if (state.canSwitchCamera) {
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    label = if (state.usingFrontCamera) "Front camera" else "Rear camera",
                    description = "Point the other lens at the cot",
                    action = if (state.usingFrontCamera) "Use rear" else "Use front",
                    onAction = { broadcaster?.switchCamera() },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Alerts") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sensitivity",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // A named level, not a bare number: 62 tells a parent nothing about whether
                // a passing car will wake them.
                Text(
                    text = sensitivityLabel(sensitivity.toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = {
                    val value = sensitivity.toInt()
                    settings.motionSensitivity = value
                    settings.soundSensitivity = value
                    broadcaster?.setSensitivity(value, value)
                },
                // Ten stops rather than a continuous sweep. The detector cannot tell 61 from
                // 62, so offering that precision only invites fiddling with it.
                steps = SENSITIVITY_STEPS,
                valueRange = 0f..100f,
            )
            Text(
                text = when (sensitivity.toInt()) {
                    0 -> "No alerts. The stream keeps running."
                    else -> "Higher means smaller movements and quieter sounds raise an alert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Comfort and access") {
            ToggleRow(
                label = "Night dim",
                description = "Fades this screen a moment after you stop touching it",
                checked = nightDim,
                onCheckedChange = {
                    nightDim = it
                    settings.nightMode = it
                    lastTouch = nowMillis()
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
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Grouped and spaced: this gets read aloud across a room, from a phone
                    // held at arm's length, usually in the dark.
                    Text(
                        text = groupPin(pin),
                        style = MaterialTheme.typography.headlineSmall,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = {
                        pin = generatePin()
                        settings.pin = pin
                        scope.launch { restart(broadcaster, deviceName, pin) }
                    }) { Text("New code") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Outlined and error-toned. This is the one control that takes the nursery offline,
        // and it used to look exactly like every other neutral surface on the screen.
        OutlinedButton(
            onClick = {
                broadcaster?.requestStop()
                onStop()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                text = "Stop broadcasting",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(Modifier.height(16.dp))
        PrivacyNote()
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The camera's view of itself, sized to the frames it is actually receiving.
 *
 * The box used to be a hardcoded 16:9 with `ContentScale.Crop`, which was wrong for the
 * common case: a phone held portrait streams portrait, so a portrait frame was cropped down
 * to a horizontal band across its middle. That looks like the picture is both tiny and
 * rotated, when in fact the frame was whole and the container was the wrong shape.
 *
 * Now the container takes the frame's own aspect ratio, so nothing is cropped and nothing is
 * letterboxed, and it is capped at a fraction of the window so a portrait stream cannot push
 * the rest of the screen out of reach.
 */
@Composable
private fun SelfPreview(
    frames: Flow<ByteArray>?,
    state: BroadcastState,
    available: Boolean,
    resolved: Boolean,
    cameraGranted: Boolean,
) {
    // 16:9 until the first frame proves otherwise — it is what the capture preset asks for.
    var aspect by remember { mutableStateOf(DEFAULT_PREVIEW_ASPECT) }

    // Constraints are unbounded inside a vertical scroll, so the window is the only thing
    // that can say how tall is too tall.
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // The window reports zero until it has been measured once. Flooring the cap keeps that
    // first pass from collapsing the box to nothing and back, which would be its own flicker.
    val maxHeight = (windowHeight * PREVIEW_MAX_HEIGHT_FRACTION).coerceAtLeast(PREVIEW_MIN_HEIGHT)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .heightIn(max = maxHeight)
                // Deliberately no fillMaxWidth: that would fix the width and leave
                // aspectRatio no room to shrink it for a portrait frame, which is exactly
                // the case this exists to handle. Matching height first for portrait keeps
                // the cap; matching width first for landscape fills the row.
                .aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            // The same frames the viewers receive, so what the parent sees here is exactly
            // what is going out.
            JpegFrameView(
                frames = frames,
                contentDescription = "Self preview",
                modifier = Modifier.fillMaxSize(),
                onAspectRatio = { aspect = it },
                placeholder = {
                    Text(
                        text = when {
                            !available -> "This device cannot broadcast"
                            !resolved -> "Waiting for permission…"
                            !cameraGranted -> "Camera access is off"
                            else -> "Waiting for video…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                },
            )

            // On the picture rather than in a card: whether this thing is actually
            // broadcasting is the one question the screen exists to answer.
            BroadcastChip(
                state = state,
                available = available,
                resolved = resolved,
                cameraGranted = cameraGranted,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )

            if (state.viewerCount > 0) {
                OverlayPill(
                    text = if (state.viewerCount == 1) "1 watching" else "${state.viewerCount} watching",
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }
        }
    }
}

/** Whether this device is actually sending anything, said in two words on the picture. */
@Composable
private fun BroadcastChip(
    state: BroadcastState,
    available: Boolean,
    resolved: Boolean,
    cameraGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val label: String
    val dot: Color
    when {
        !available -> {
            label = "Unavailable"
            dot = MaterialTheme.colorScheme.error
        }
        !resolved -> {
            label = "Waiting for permission"
            dot = MaterialTheme.colorScheme.onSurfaceVariant
        }
        state.syntheticVideo -> {
            label = "Test pattern"
            dot = MaterialTheme.colorScheme.secondary
        }
        state.running && !cameraGranted -> {
            label = "Sound only"
            dot = MaterialTheme.colorScheme.secondary
        }
        state.running -> {
            label = "Broadcasting"
            dot = MaterialTheme.colorScheme.primary
        }
        else -> {
            label = "Starting…"
            dot = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A steady dot, not a pulsing one. Anything that blinks in a dark room is a
            // thing the parent has to learn to ignore.
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OverlayPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
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
        // The whole row, not just the switch. A 32dp target on a phone held one-handed over
        // a cot is a target you miss.
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(vertical = 4.dp),
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
        // Null: the row owns the gesture, so the switch must not claim it as well.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(
    label: String,
    description: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth(0.6f)) {
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
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun advisoryFor(
    available: Boolean,
    resolved: Boolean,
    cameraGranted: Boolean,
    synthetic: Boolean,
): String? = when {
    !available -> "This device cannot broadcast."
    !resolved -> null
    !cameraGranted ->
        "Camera access is off. Allow it to send video — sound still works without it."
    synthetic -> "No camera available — sending a test pattern instead."
    else -> null
}

private fun sensitivityLabel(value: Int): String = when {
    value == 0 -> "Off"
    value < 34 -> "Low"
    value < 67 -> "Medium"
    else -> "High"
}

/** `123456` reads as one long number; `123 456` reads as a code. */
private fun groupPin(pin: String): String =
    if (pin.length == PIN_LENGTH) "${pin.take(3)} ${pin.drop(3)}" else pin

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
private const val SENSITIVITY_STEPS = 9
private const val DIM_SETTLE_MILLIS = 6000L
private const val DEFAULT_PREVIEW_ASPECT = 16f / 9f
private const val PREVIEW_MAX_HEIGHT_FRACTION = 0.48f
private val PREVIEW_MIN_HEIGHT = 180.dp
