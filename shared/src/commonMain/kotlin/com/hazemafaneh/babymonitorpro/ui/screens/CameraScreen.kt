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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.PairingUri
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.server.BroadcastConfig
import com.hazemafaneh.babymonitorpro.server.BroadcastState
import com.hazemafaneh.babymonitorpro.server.Broadcaster
import com.hazemafaneh.babymonitorpro.di.BroadcasterHolder
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.KeepScreenAwake
import com.hazemafaneh.babymonitorpro.ui.layout.WindowClass
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.layout.windowHeight
import com.hazemafaneh.babymonitorpro.ui.rememberCapturePermissions
import com.hazemafaneh.babymonitorpro.ui.components.OverlayPill
import com.hazemafaneh.babymonitorpro.ui.components.MonoValue
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyLine
import com.hazemafaneh.babymonitorpro.ui.components.QrCode
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.components.SectionLabel
import com.hazemafaneh.babymonitorpro.ui.components.StatusChip
import com.hazemafaneh.babymonitorpro.ui.components.StopBroadcastingButton
import com.hazemafaneh.babymonitorpro.ui.components.StatusTone
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch
import com.hazemafaneh.babymonitorpro.ui.video.JpegFrameView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * Confirm & Pair: is this thing working, and how do I get the other device onto it.
 *
 * The settings that used to share this scroll now live in [CameraSettingsScreen]. They are
 * two unrelated jobs — this one is urgent, done standing in the room, and finished within
 * two minutes; the other is touched roughly once ever. Sharing one scroll pushed the
 * pairing code and the stop action below the fold on a 393pt phone, which is the
 * opposite of the priority.
 */
@Composable
fun CameraScreen(
    nightEnabled: Boolean,
    onNightActiveChanged: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    // Injected, not constructed: opening Settings must not hand that screen a second,
    // socket-less broadcaster.
    val broadcaster = koinInject<BroadcasterHolder>().broadcaster
    val window = rememberWindowClass()

    val deviceName = remember { settings.deviceName }

    var lastTouch by remember { mutableStateOf(nowMillis()) }

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
    // code the first one just put on screen.
    LaunchedEffect(broadcaster, permissions.resolved) {
        if (!permissions.resolved) return@LaunchedEffect
        // Only if it is not already up. This effect re-runs every time the screen is
        // recomposed from scratch — which now includes coming back from Settings — and
        // start() begins by calling stop(), so an unguarded call here would tear the server
        // down and drop every viewer just because the parent looked at a setting.
        if (broadcaster?.state?.value?.running == true) return@LaunchedEffect
        broadcaster?.start(
            BroadcastConfig(
                deviceName = deviceName,
                motionSensitivity = settings.motionSensitivity,
                soundSensitivity = settings.soundSensitivity,
            ),
        )
    }

    // Deliberately no stop-on-dispose. Opening Settings disposes this screen, and tearing
    // the server down for that would drop every viewer the moment a parent went to rename
    // the device. Stopping is now only ever an explicit action — the Stop broadcasting
    // button, which navigates back to the role picker.

    // Night settles a moment after the last touch and any touch lifts it. The screen is
    // being dimmed for the room, not for whoever is still using it — so this reports the
    // state up and the theme swaps its whole ColorScheme, rather than an alpha being laid
    // over text that then has to be read at 45%.
    LaunchedEffect(nightEnabled, lastTouch) {
        onNightActiveChanged(false)
        if (!nightEnabled) return@LaunchedEffect
        delay(DIM_SETTLE_MILLIS)
        onNightActiveChanged(true)
    }

    DisposableEffect(Unit) {
        onDispose { onNightActiveChanged(false) }
    }

    val touch = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = "Brighten the screen",
    ) { lastTouch = nowMillis() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .then(touch),
    ) {
        CameraHeader(
            deviceName = deviceName,
            address = state.primaryAddress,
            actionLabel = "Settings",
            onAction = onSettings,
            modifier = Modifier.padding(horizontal = window.gutter()),
        )

        if (window == WindowClass.EXPANDED) {
            // Landscape is where the split earns its keep: the two jobs become two columns
            // and neither scrolls.
            //
            // Not two equal halves. The picture is the job the parent is actually doing —
            // is this thing pointed at the right place and working — and the pairing rail is
            // a fixed amount of text and one code. Splitting the width evenly gave a 12"
            // tablet a pairing card wider than a phone screen with an address floating in
            // the middle of it, and shrank the preview to make room for the emptiness.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = window.gutter())
                    .padding(bottom = window.gutter()),
                horizontalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PreviewBlock(
                        broadcaster?.frames,
                        state,
                        broadcaster != null,
                        permissions.resolved,
                        permissions.cameraGranted,
                        window,
                        // Takes the height the column has rather than a fraction of the
                        // window: in landscape, the fraction left the bottom half of the
                        // column empty and the picture smaller than the card beside it.
                        modifier = Modifier.weight(1f),
                    )
                    AdvisoryBlock(broadcaster != null, permissions.resolved, permissions.cameraGranted, state)
                }
                Column(Modifier.width(RAIL_WIDTH).fillMaxHeight()) {
                    Spacer(Modifier.height(Space.md))
                    PortConflictBanner(state, broadcaster)
                    PairCard(state, window)
                    // Pinned to the bottom of the rail rather than left under the card. It
                    // is the one destructive control on the screen, and putting distance
                    // between it and the things a parent taps while pairing is the point.
                    Spacer(Modifier.weight(1f))
                    StopBroadcastingButton(broadcaster, onStop)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = window.gutter()),
            ) {
                PreviewBlock(broadcaster?.frames, state, broadcaster != null, permissions.resolved, permissions.cameraGranted, window)
                AdvisoryBlock(broadcaster != null, permissions.resolved, permissions.cameraGranted, state)
                Spacer(Modifier.height(Space.md))
                PortConflictBanner(state, broadcaster)
                PairCard(state, window)
                Spacer(Modifier.height(Space.md))
                StopBroadcastingButton(broadcaster, onStop)
                Spacer(Modifier.height(Space.xl))
            }
        }
    }
}

@Composable
private fun CameraHeader(
    deviceName: String,
    address: String?,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            // The privacy promise as telemetry rather than prose, in the same slot on every
            // screen: this address, on your network, right now. A parent can check it
            // against their own router, which is not true of a paragraph.
            PrivacyLine(address)
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** The picture, its status, and who is watching it. */
@Composable
private fun PreviewBlock(
    frames: Flow<ByteArray>?,
    state: BroadcastState,
    available: Boolean,
    resolved: Boolean,
    cameraGranted: Boolean,
    window: WindowClass,
    modifier: Modifier = Modifier,
) {
    // 16:9 until the first frame proves otherwise — it is what the capture preset asks for.
    var aspect by remember { mutableStateOf(DEFAULT_PREVIEW_ASPECT) }
    // A running server is not a running picture. Until a frame has actually been decoded the
    // chip says "Starting" rather than "Broadcasting", because amber here means the parent
    // stops checking — and this is precisely the window where there is nothing to see.
    var hasFrame by remember { mutableStateOf(false) }

    // Constraints are unbounded inside a vertical scroll, so the window is the only thing
    // that can say how tall is too tall. Floored so the first, unmeasured pass does not
    // collapse the box to nothing and back.
    val maxHeight = (windowHeight() * PREVIEW_MAX_HEIGHT_FRACTION)
        .coerceAtLeast(PREVIEW_MIN_HEIGHT)

    Spacer(Modifier.height(Space.md))

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .heightIn(max = if (window == WindowClass.EXPANDED) Dp.Infinity else maxHeight)
                // Deliberately no fillMaxWidth: that would fix the width and leave
                // aspectRatio no room to shrink it for a portrait frame, which is the case
                // this exists to handle. A phone held upright streams upright, uncropped.
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
                contentScale = ContentScale.Crop,
                onAspectRatio = {
                    aspect = it
                    hasFrame = true
                },
                placeholder = {
                    val prose = when {
                        !available -> "This device cannot broadcast"
                        !resolved -> "The system is asking for camera and microphone access."
                        !cameraGranted -> "Camera access is off"
                        else -> null
                    }
                    if (prose != null) {
                        Text(
                            text = prose,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Space.xl),
                        )
                    } else {
                        // A label, not a spinner. The judder this used to have was the
                        // double start, not slow capture, so there is nothing here worth
                        // animating in a dark room.
                        SectionLabel("First frame pending")
                    }
                },
            )

            // On the picture rather than in a card: whether this thing is actually
            // broadcasting is the one question the screen exists to answer.
            val (label, tone) = broadcastStatus(available, resolved, cameraGranted, hasFrame, state)
            val chipInset = if (window.isCompact) Space.sm else Space.md
            StatusChip(
                label = label,
                tone = tone,
                modifier = Modifier.align(Alignment.TopStart).padding(chipInset),
            )

            // Printed at zero too. A pill that disappears makes "nobody has connected"
            // look identical to "the count is broken", and zero is a fact worth stating.
            if (state.running) {
                OverlayPill(
                    text = when (state.viewerCount) {
                        0 -> "Nobody watching"
                        1 -> "1 watching"
                        else -> "${state.viewerCount} watching"
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(chipInset),
                )
            }
        }
    }
    if (window.atLeastMedium) Spacer(Modifier.height(Space.xs))
}

@Composable
private fun AdvisoryBlock(
    available: Boolean,
    resolved: Boolean,
    cameraGranted: Boolean,
    state: BroadcastState,
) {
    // Only when there is something to do about it. A line of prose repeating what the chip
    // already said is a line the parent learns to skip past.
    val advice = when {
        !available -> "This device cannot broadcast."
        !resolved -> null
        !cameraGranted ->
            "Camera access is off. Sound is still going out. Turn video back on in Settings."
        state.syntheticVideo ->
            "This device has no working camera. Viewers get a test pattern, so sound and " +
                "pairing can still be checked."
        else -> null
    } ?: return

    Spacer(Modifier.height(Space.sm))
    Text(
        text = advice,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The fault, and the way out of it.
 *
 * A port already in use is the one broadcast failure a parent can fix from this screen —
 * usually a second copy of the app left running on the same device — so it is the one that
 * carries an action rather than an instruction. Naming the next port in the button means the
 * fix is one tap, not a trip into settings to guess a number.
 */
@Composable
private fun PortConflictBanner(state: BroadcastState, broadcaster: Broadcaster?) {
    val problem = state.lastError ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(Space.md)) {
            Text(
                text = problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (state.portUnavailable && broadcaster != null) {
                val alternate = state.alternatePort
                TextButton(
                    onClick = { broadcaster.retryOnPort(alternate) },
                    modifier = Modifier.heightIn(min = Touch.min),
                ) {
                    Text(
                        text = "Broadcast on $alternate instead",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(Space.sm))
}

/** Address and code — everything the other device needs. */
@Composable
private fun PairCard(state: BroadcastState, window: WindowClass) {
    // LocalClipboard, its replacement, takes a ClipEntry — and ClipEntry has no common
    // constructor in Compose Multiplatform 1.11: it wraps a ClipData on Android and a
    // Transferable on the JVM, so there is nothing to build one from in shared code.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val address = state.primaryAddress

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(COPIED_VISIBLE_MILLIS)
        copied = false
    }

    SectionCard(title = "Pair another device") {
        if (address == null) {
            Text(
                // Two different silences. With the port taken there is nothing to wait for
                // and saying so stops the parent watching the card; otherwise the address is
                // withheld rather than guessed at — a 169.254 address is private, so the old
                // filter accepted it, but nothing on the WiFi can reach one, and offering it
                // hands the viewer an address that can only refuse.
                text = if (state.portUnavailable) "No address yet" else "Waiting for the network…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val pairing = "$address:${state.port}"
        Row(
            Modifier.fillMaxWidth().heightIn(min = Touch.min),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoValue(
                text = pairing,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Reading an address off one phone and typing it into another is the fallback
            // when the camera sits somewhere a code cannot be scanned.
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(pairing))
                copied = true
            }) {
                Text(if (copied) "Copied" else "Copy")
            }
        }

        Spacer(Modifier.height(Space.sm))

        val scanHint = "Scan this from the other device, or type the address in by hand. " +
            "Encodes bmpro:// — no internet lookup."
        // Named, not nagged about: anyone on this WiFi who has the address can watch.
        val reachHint = "Anyone on this WiFi who knows the address can watch."

        if (window.isCompact) {
            // A phone has no width to spare, so the code takes most of it and the prose sits
            // underneath.
            QrCode(
                content = PairingUri.build(address, state.port),
                modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f),
            )
            Spacer(Modifier.height(Space.xs))
            Text(scanHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.md))
            Text(reachHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // On a tablet the code is a fixed 150dp — sized to be scanned from across the
            // room, which is the whole reason the QR route exists, and not one pixel more.
            // Scaling it with the card instead produced a code the size of a coaster with a
            // column of dead space beside it; the prose belongs in that space.
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                QrCode(
                    content = PairingUri.build(address, state.port),
                    modifier = Modifier.size(TABLET_QR_SIZE),
                )
                Column(Modifier.weight(1f)) {
                    Text(scanHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Space.sm))
                    Text(reachHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun broadcastStatus(
    available: Boolean,
    resolved: Boolean,
    cameraGranted: Boolean,
    hasFrame: Boolean,
    state: BroadcastState,
): Pair<String, StatusTone> = when {
    !available -> "Unavailable" to StatusTone.FAULT
    state.lastError != null -> "Stopped" to StatusTone.FAULT
    !resolved -> "Waiting for permission" to StatusTone.WAITING
    state.syntheticVideo -> "Test pattern" to StatusTone.DEGRADED
    state.running && !cameraGranted -> "Sound only" to StatusTone.DEGRADED
    // Server up, no picture yet. Amber is reserved for live and healthy, and a camera with
    // no first frame is neither.
    state.running && !hasFrame -> "Starting" to StatusTone.WAITING
    state.running -> "Broadcasting" to StatusTone.LIVE
    else -> "Starting" to StatusTone.WAITING
}

private const val DIM_SETTLE_MILLIS = 6000L
private const val COPIED_VISIBLE_MILLIS = 1600L
private const val DEFAULT_PREVIEW_ASPECT = 16f / 9f
private val RAIL_WIDTH = 420.dp
private val TABLET_QR_SIZE = 150.dp
private const val PREVIEW_MAX_HEIGHT_FRACTION = 0.48f
private val PREVIEW_MIN_HEIGHT = 180.dp
