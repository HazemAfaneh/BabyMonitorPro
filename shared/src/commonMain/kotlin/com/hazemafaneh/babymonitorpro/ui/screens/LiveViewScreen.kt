package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.audio.createAudioPlayer
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.notify.liveSessions
import com.hazemafaneh.babymonitorpro.notify.notifyAlert
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.ui.KeepScreenAwake
import com.hazemafaneh.babymonitorpro.ui.components.MonoValue
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyLine
import com.hazemafaneh.babymonitorpro.ui.components.SectionLabel
import com.hazemafaneh.babymonitorpro.ui.components.StatusChip
import com.hazemafaneh.babymonitorpro.ui.components.StatusDot
import com.hazemafaneh.babymonitorpro.ui.components.StatusTone
import com.hazemafaneh.babymonitorpro.ui.layout.WindowClass
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.layout.windowHeight
import com.hazemafaneh.babymonitorpro.ui.layout.windowWidth
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.LocalReducedMotion
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch
import com.hazemafaneh.babymonitorpro.ui.video.MjpegVideo
import com.hazemafaneh.babymonitorpro.ui.video.VideoStatus
import com.hazemafaneh.babymonitorpro.ui.video.videoRendersBehindUi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Chrome-free by design: the video is the interface. Controls surface on tap and fade
 * out again after four seconds so a parent glancing at a dark room sees the room.
 *
 * Two contexts opt out of that. A desktop window competing with other windows needs a
 * persistent identity and has no scarce screen edge to reclaim, so its chrome never hides.
 * The browser is the other — see [videoRendersBehindUi].
 *
 * [autoAudio] arrives from a tapped alert notification: the app raised it *because it heard
 * something*, so the parent lands with the sound already on rather than hunting for the
 * control while the reason for the alert passes.
 */
@Composable
fun LiveViewScreen(
    endpoint: CameraEndpoint,
    onBack: () -> Unit,
    autoAudio: Boolean = false,
) {
    // All keyed on the endpoint: the live view is now singleTop, so the same composition is
    // reused when the parent switches cameras. Unkeyed, the new camera inherited the old
    // one's status, latency and last error — which is the one lie this screen must not tell.
    var status by remember(endpoint.id) { mutableStateOf(VideoStatus.CONNECTING) }
    var cameraStatus by remember(endpoint.id) { mutableStateOf<ControlMessage.Status?>(null) }
    var latencyMillis by remember(endpoint.id) { mutableStateOf<Long?>(null) }
    var alert by remember(endpoint.id) { mutableStateOf<Alert?>(null) }
    var failure by remember(endpoint.id) { mutableStateOf<String?>(null) }
    var audioOn by remember { mutableStateOf(autoAudio) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(nowMillis()) }
    var frameAspect by remember(endpoint.id) { mutableStateOf(0f) }
    var rotationHintSeen by remember(endpoint.id) { mutableStateOf(false) }

    // In-session only, never written to disk — the no-recording promise covers alert
    // history as much as it covers video.
    val alertHistory = remember(endpoint.id) { mutableStateListOf<Alert>() }

    val reducedMotion = LocalReducedMotion.current
    val window = rememberWindowClass()
    val audioPlayer = remember(endpoint.id) { createAudioPlayer(AudioConfig()) }

    KeepScreenAwake(enabled = true)

    // The watching session on the Lock Screen and in the Dynamic Island, for exactly as long
    // as this screen is up. Keyed on the endpoint so switching cameras ends one session and
    // starts another rather than relabelling the first.
    DisposableEffect(endpoint.id) {
        liveSessions.startViewing(endpoint)
        onDispose { liveSessions.stopViewing() }
    }

    // The status chip's own words, so a glance at the phone face-down on the bed and a glance
    // at the app say the same thing. This is also how an alert reaches the Lock Screen: the
    // running session is updated in place rather than a second notification being posted on
    // top of it — see [raiseAlert].
    LaunchedEffect(status, alert) {
        liveSessions.updateViewing(
            statusLabel = status.describe().first,
            alert = alert?.label,
        )
    }

    LaunchedEffect(lastInteraction, window) {
        controlsVisible = true
        // Desktop keeps its chrome: there is nothing to reveal by hiding it.
        if (window == WindowClass.EXPANDED || videoRendersBehindUi) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MILLIS)
        controlsVisible = false
    }

    // Control channel: status, alerts, and a round-trip ping that doubles as the
    // connection's health check.
    LaunchedEffect(endpoint.id) {
        val client = ViewerClient(endpoint)
        val outgoing = MutableSharedFlow<ControlMessage>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val pinger = launch {
            while (true) {
                delay(PING_INTERVAL_MILLIS)
                outgoing.emit(ControlMessage.Ping(nowMillis()))
            }
        }
        try {
            while (true) {
                runCatching {
                    client.control(outgoing).collect { message ->
                        when (message) {
                            is ControlMessage.Status -> cameraStatus = message
                            is ControlMessage.Pong ->
                                latencyMillis = nowMillis() - message.nonce
                            is ControlMessage.MotionEvent -> {
                                val raised = Alert("Movement detected", message.atMillis)
                                alert = raised
                                alertHistory.record(raised)
                                raiseAlert(endpoint.named(cameraStatus), raised.label)
                            }
                            is ControlMessage.SoundEvent -> {
                                val raised = Alert("Sound detected", message.atMillis)
                                alert = raised
                                alertHistory.record(raised)
                                raiseAlert(endpoint.named(cameraStatus), raised.label)
                            }
                            else -> Unit
                        }
                    }
                }
                delay(RECONNECT_DELAY_MILLIS)
            }
        } finally {
            pinger.cancel()
            client.close()
        }
    }

    LaunchedEffect(alert) {
        if (alert != null) {
            delay(ALERT_VISIBLE_MILLIS)
            alert = null
        }
    }

    // Audio reconnects on its own like the video does: a socket dropped by a roaming phone
    // used to leave sound dead until the parent noticed and toggled it twice.
    LaunchedEffect(audioOn, endpoint.id) {
        val player = audioPlayer
        if (!audioOn || player == null) return@LaunchedEffect
        val client = ViewerClient(endpoint)
        player.start()
        try {
            while (true) {
                runCatching { client.audioChunks().collect { chunk -> player.write(chunk) } }
                delay(RECONNECT_DELAY_MILLIS)
            }
        } finally {
            player.stop()
            client.close()
        }
    }

    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer?.stop() }
    }

    // On web the video is a DOM element layered over the Compose canvas — which is not
    // transparent — so anything drawn underneath it would be invisible. There the video
    // gets its own band between the chrome instead of sitting behind it, and the controls
    // stay put rather than auto-hiding over a picture they cannot overlay.
    val videoIsSeparateLayer = videoRendersBehindUi
    val chromeAlwaysVisible = videoIsSeparateLayer || window == WindowClass.EXPANDED ||
        controlsVisible

    // A landscape camera watched on an upright phone aspect-fits into roughly a third of the
    // screen, with the rest black. Nothing is wrong and nothing is cropped — the picture is
    // simply the wrong shape for the window — so the answer is to say so once rather than to
    // crop the cot out of frame. Landscape is already permitted on iPhone, so turning the
    // phone is all it takes.
    val portraitWindow = windowHeight() > windowWidth()
    val suggestRotation = !rotationHintSeen &&
        window.isCompact &&
        portraitWindow &&
        !videoIsSeparateLayer &&
        frameAspect > LANDSCAPE_THRESHOLD &&
        status == VideoStatus.LIVE

    // Shown once per camera, then left alone. A hint that keeps reappearing over a sleeping
    // baby is worse than no hint.
    LaunchedEffect(suggestRotation) {
        if (!suggestRotation) return@LaunchedEffect
        delay(ROTATION_HINT_MILLIS)
        rotationHintSeen = true
    }

    val audioAvailable = audioPlayer != null && cameraStatus?.audioAvailable != false

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        LivePane(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            endpoint = endpoint,
            status = status,
            latencyMillis = latencyMillis,
            cameraStatus = cameraStatus,
            failure = failure,
            alert = alert,
            audioOn = audioOn,
            audioAvailable = audioAvailable,
            audioSupported = audioPlayer != null,
            chromeVisible = chromeAlwaysVisible,
            statusTone = status.describe().second,
            suggestRotation = suggestRotation,
            videoIsSeparateLayer = videoIsSeparateLayer,
            reducedMotion = reducedMotion,
            onStatus = { status = it },
            onError = { failure = it },
            onAspectRatio = { frameAspect = it },
            onTouch = { lastInteraction = nowMillis() },
            onToggleAudio = {
                audioOn = !audioOn
                lastInteraction = nowMillis()
            },
            onBack = onBack,
        )

        // Desktop only: a window needs a persistent identity, and the rail gives the alert
        // history somewhere to live.
        if (window == WindowClass.EXPANDED && !videoIsSeparateLayer) {
            SideRail(
                name = cameraStatus?.deviceName ?: endpoint.name,
                address = endpoint.id,
                history = alertHistory,
            )
        }
    }
}

/**
 * The picture and everything drawn over it.
 *
 * Its own composable rather than an inline `Box`, because inside the outer `Row` the
 * `RowScope.AnimatedVisibility` overload wins overload resolution over the plain one and
 * refuses to compile. Lifting it out drops that receiver, and the pane is a distinct thing
 * from the rail beside it in any case.
 */
@Composable
private fun LivePane(
    modifier: Modifier,
    endpoint: CameraEndpoint,
    status: VideoStatus,
    latencyMillis: Long?,
    cameraStatus: ControlMessage.Status?,
    failure: String?,
    alert: Alert?,
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    chromeVisible: Boolean,
    statusTone: StatusTone,
    suggestRotation: Boolean,
    videoIsSeparateLayer: Boolean,
    reducedMotion: Boolean,
    onStatus: (VideoStatus) -> Unit,
    onError: (String?) -> Unit,
    onAspectRatio: (Float) -> Unit,
    onTouch: () -> Unit,
    onToggleAudio: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onTouch() },
    ) {
        MjpegVideo(
            endpoint = endpoint,
            modifier = if (videoIsSeparateLayer) {
                Modifier.fillMaxSize().padding(top = 56.dp, bottom = 88.dp)
            } else {
                Modifier.fillMaxSize()
            }
                // A dead feed at full brightness reads as live. Holding the last frame back
                // keeps it as context without claiming it is current — and it is a fade,
                // not a blink, per the dark-room rule.
                .alpha(if (status == VideoStatus.RECONNECTING) STALE_FRAME_ALPHA else 1f),
            onStatus = onStatus,
            onFrame = { },
            onError = onError,
            onAspectRatio = onAspectRatio,
        )

        // States with no picture at all say so in words, centred, rather than leaving a
        // black rectangle the parent has to interpret.
        val empty = emptyPictureText(status, endpoint)
        if (empty != null) {
            Text(
                text = empty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = Space.xl),
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = if (reducedMotion) fadeIn(snap()) else fadeIn(),
            exit = if (reducedMotion) fadeOut(snap()) else fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopChrome(
                status = status,
                latencyMillis = latencyMillis,
                endpoint = endpoint,
                failure = failure,
                alert = alert,
                // On web the alert cannot be drawn over the picture, so it moves into the
                // bar with everything else.
                inlineAlert = videoIsSeparateLayer,
                onBack = onBack,
            )
        }

        if (!videoIsSeparateLayer) {
            AnimatedVisibility(
                visible = alert != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                AlertBanner(
                    alert = alert,
                    modifier = Modifier
                        .safeContentPadding()
                        .padding(top = 76.dp, start = Space.md, end = Space.md),
                )
            }
        }

        // The one thing that never hides. With the full chrome faded there was nothing at
        // all on screen answering "is this still live", which is the question the parent
        // picked the phone up to ask.
        if (!chromeVisible) {
            StatusDot(
                tone = statusTone,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeContentPadding()
                    .padding(Space.md),
            )
        }

        if (suggestRotation) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeContentPadding()
                    .padding(bottom = 104.dp, start = Space.md, end = Space.md),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Turn your phone sideways for a bigger picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = Space.xs),
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = if (reducedMotion) fadeIn(snap()) else fadeIn(),
            exit = if (reducedMotion) fadeOut(snap()) else fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(
                name = cameraStatus?.deviceName ?: endpoint.name,
                address = endpoint.id,
                audioOn = audioOn,
                audioAvailable = audioAvailable,
                audioSupported = audioSupported,
                fullWidth = videoIsSeparateLayer,
                onToggleAudio = onToggleAudio,
            )
        }
    }
}

@Composable
private fun TopChrome(
    status: VideoStatus,
    latencyMillis: Long?,
    endpoint: CameraEndpoint,
    failure: String?,
    alert: Alert?,
    inlineAlert: Boolean,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .safeContentPadding()
            .padding(Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.fillMaxWidth(0.8f)) {
            val (label, tone) = status.describe()
            StatusChip(
                label = label,
                tone = tone,
                trailing = latencyMillis
                    ?.takeIf { status == VideoStatus.LIVE }
                    ?.let { "${it.coerceAtLeast(0)} ms" },
            )
            Spacer(Modifier.size(Space.xxs))
            PrivacyLine(endpoint.id)

            // A black picture with no explanation is unactionable. Once the stream is live
            // the reason is stale, so it only shows while it is still true.
            val reason = status.reason() ?: failure?.takeIf { status != VideoStatus.LIVE }
            if (reason != null) {
                Spacer(Modifier.size(Space.xs))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = Space.xs),
                    )
                }
            }

            if (inlineAlert && alert != null) {
                Spacer(Modifier.size(Space.xs))
                AlertBanner(alert)
            }
        }
        TextButton(onClick = onBack) { Text("Close") }
    }
}

/**
 * The alert that just fired.
 *
 * Outlined rather than filled with `primaryContainer`, which is the same amber family as the
 * live dot — so the alert and the healthy state used to be the same colour, and neither
 * signalled. It also sits in a fixed slot below the chip instead of floating over the
 * picture, which is the thing the parent actually wants to look at when an alert arrives.
 */
@Composable
private fun AlertBanner(alert: Alert?, modifier: Modifier = Modifier) {
    alert ?: return
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.large,
        ),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = alert.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(Space.xs))
            Text(
                text = "just now",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BottomBar(
    name: String,
    address: String,
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    fullWidth: Boolean,
    onToggleAudio: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .safeContentPadding()
            .padding(Space.md)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(MaterialTheme.shapes.extraLarge),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (fullWidth) 1f else 0.85f),
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                MonoValue(
                    text = address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (audioSupported) {
                Spacer(Modifier.size(Space.sm))
                SoundPill(
                    on = audioOn,
                    available = audioAvailable,
                    onClick = onToggleAudio,
                )
            } else {
                Spacer(Modifier.size(Space.sm))
                // Honestly absent rather than shown disabled: browsers cannot play the raw
                // PCM this protocol carries, and a greyed control invites tapping at it.
                Text(
                    text = "No sound in browsers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Sound, as a state rather than an instruction.
 *
 * `TextButton("Sound off")` reads as a command — half of parents tap it expecting to *turn*
 * sound off, and turn it on. Filled with a lit dot means on; outlined and unlit means off.
 */
@Composable
private fun SoundPill(
    on: Boolean,
    available: Boolean,
    onClick: () -> Unit,
) {
    val live = BmpTheme.semantic.statusLive
    val border = when {
        !available -> MaterialTheme.colorScheme.outlineVariant
        on -> live
        else -> MaterialTheme.colorScheme.outline
    }
    val fill = if (on && available) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = when {
        !available -> MaterialTheme.colorScheme.onSurfaceVariant
        on -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .heightIn(min = Touch.min)
            .clip(MaterialTheme.shapes.extraLarge)
            .then(if (available) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, border, MaterialTheme.shapes.extraLarge),
        color = fill,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(border))
            Spacer(Modifier.size(Space.xs))
            Text(
                text = when {
                    !available -> "No sound"
                    on -> "Sound on"
                    else -> "Sound off"
                },
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

/** Desktop's persistent identity strip, plus where the alerts went. */
@Composable
private fun SideRail(
    name: String,
    address: String,
    history: List<Alert>,
) {
    Column(
        Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.lg),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MonoValue(
            text = address,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(Space.xxs))
        // Deliberately the address-less form. The line above already prints the address in
        // monospace, for reading aloud; repeating it here as "On your WiFi · <address>" put
        // the same string on the screen twice in two fonts, which reads as a rendering
        // fault rather than as reassurance.
        PrivacyLine(null)

        Spacer(Modifier.height(Space.xl))
        SectionLabel("Recent alerts")
        Spacer(Modifier.height(Space.xs))
        if (history.isEmpty()) {
            Text(
                text = "Nothing yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (entry in history) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            // The no-recording promise covers this list too.
            text = "This session only. Nothing is written to disk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    }
}

/**
 * The endpoint with the camera's self-reported name substituted in.
 *
 * [CameraEndpoint.name] is the raw host for a manual or scanned connection, so an alert
 * notification would title itself "192.168.1.31" — true, and useless at 3am with two
 * cameras in the house. The control channel carries the real name; this uses it as soon as
 * it has arrived and falls back to whatever the endpoint already had.
 */
private fun CameraEndpoint.named(status: ControlMessage.Status?): CameraEndpoint {
    val reported = status?.deviceName?.takeIf { it.isNotBlank() } ?: return this
    return if (reported == name) this else copy(name = reported)
}

private data class Alert(val label: String, val atMillis: Long)

/**
 * Puts a motion or sound alert in front of the parent, on every surface at once.
 *
 * Both, deliberately, and not one or the other. Updating the live session changes what the
 * Lock Screen and the Dynamic Island show, but it makes no sound and no haptic — ActivityKit
 * updates are silent, and the library's bridge exposes no alert configuration — so a session
 * on its own reaches a parent who is looking and nobody else. The notification is what reaches
 * a parent who is asleep. The session then carries the same alert onward, staying current
 * after the banner has been dismissed.
 */
private fun raiseAlert(endpoint: CameraEndpoint, message: String) {
    notifyAlert(endpoint, message)
}

/** Newest first, capped — this is a glance-at list, not a log. */
private fun MutableList<Alert>.record(alert: Alert) {
    add(0, alert)
    while (size > ALERT_HISTORY_MAX) removeAt(lastIndex)
}

private fun VideoStatus.describe(): Pair<String, StatusTone> = when (this) {
    VideoStatus.CONNECTING -> "Connecting" to StatusTone.WAITING
    VideoStatus.LIVE -> "Live" to StatusTone.LIVE
    VideoStatus.RECONNECTING -> "Reconnecting" to StatusTone.WAITING
    VideoStatus.NO_PICTURE -> "Connected · no picture" to StatusTone.FAULT
    VideoStatus.FAILED -> "Camera stopped" to StatusTone.FAULT
}

private fun VideoStatus.reason(): String? = when (this) {
    VideoStatus.RECONNECTING ->
        "The camera device moved to another access point. Retrying every 2 seconds."
    VideoStatus.NO_PICTURE ->
        "The camera is answering on the control channel but sending no frames. " +
            "Sound is unaffected."
    VideoStatus.FAILED ->
        // Naming the cause points at the thing that can be fixed. "No connection" sent the
        // parent to check their WiFi, which is rarely what stopped.
        "Nothing is being sent. This view reconnects on its own once the app is opened there again."
    else -> null
}

private fun emptyPictureText(status: VideoStatus, endpoint: CameraEndpoint): String? = when (status) {
    VideoStatus.CONNECTING -> "Reaching ${endpoint.id} on this network."
    VideoStatus.NO_PICTURE -> "No frame for 8 seconds."
    VideoStatus.FAILED -> "The camera device closed BabyMonitor Pro."
    else -> null
}

/** Above this, a frame is wide enough that a portrait phone wastes most of its screen. */
private const val LANDSCAPE_THRESHOLD = 1.2f
private const val ROTATION_HINT_MILLIS = 7000L
private val RAIL_WIDTH = 300.dp
private const val ALERT_HISTORY_MAX = 20
private const val STALE_FRAME_ALPHA = 0.3f
private const val CONTROLS_TIMEOUT_MILLIS = 4000L
private const val PING_INTERVAL_MILLIS = 5000L
private const val RECONNECT_DELAY_MILLIS = 1500L
private const val ALERT_VISIBLE_MILLIS = 6000L
