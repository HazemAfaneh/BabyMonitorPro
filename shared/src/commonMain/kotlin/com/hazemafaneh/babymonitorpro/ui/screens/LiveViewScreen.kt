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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.hazemafaneh.babymonitorpro.ui.components.pressable
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import com.hazemafaneh.babymonitorpro.ui.components.SoundMeter
import com.hazemafaneh.babymonitorpro.detect.SoundDetector
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Dp
import com.hazemafaneh.babymonitorpro.ui.components.CARD_BORDER
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import com.hazemafaneh.babymonitorpro.audio.createAudioPlayer
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.isTelevision
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.notify.liveSessions
import com.hazemafaneh.babymonitorpro.notify.AlertKind
import com.hazemafaneh.babymonitorpro.notify.CameraAlert
import com.hazemafaneh.babymonitorpro.notify.notifyAlert
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.store.ListenOnAlert
import org.koin.compose.koinInject
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.ui.KeepScreenAwake
import com.hazemafaneh.babymonitorpro.ui.components.MoonButton
import com.hazemafaneh.babymonitorpro.ui.components.focusAnchor
import com.hazemafaneh.babymonitorpro.ui.components.focusRing
import com.hazemafaneh.babymonitorpro.ui.components.rememberFocusAnchor
import com.hazemafaneh.babymonitorpro.ui.components.skipDpadFocus
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
import com.hazemafaneh.babymonitorpro.ui.components.IconPlate
import com.hazemafaneh.babymonitorpro.ui.components.PlateSize
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
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
    night: Boolean = false,
    onNightChanged: (Boolean) -> Unit = {},
    autoAudio: Boolean = false,
) {
    // All keyed on the endpoint: the live view is now singleTop, so the same composition is
    // reused when the parent switches cameras. Unkeyed, the new camera inherited the old
    // one's status, latency and last error — which is the one lie this screen must not tell.
    var status by remember(endpoint.id) { mutableStateOf(VideoStatus.CONNECTING) }
    var cameraStatus by remember(endpoint.id) { mutableStateOf<ControlMessage.Status?>(null) }
    var latencyMillis by remember(endpoint.id) { mutableStateOf<Long?>(null) }
    val settings = koinInject<AppSettings>()
    var alert by remember(endpoint.id) { mutableStateOf<CameraAlert?>(null) }
    var failure by remember(endpoint.id) { mutableStateOf<String?>(null) }
    // The parent's standing preference, unless this view was opened *by* an alert — then the
    // app heard something and sound is the reason they are here, whatever the setting says.
    var audioOn by remember { mutableStateOf(autoAudio || settings.startWithSound) }
    // True only while the sound is on because *an alert* turned it on. It is what lets
    // "until quiet" undo its own switch-on without ever undoing the parent's: a parent who
    // pressed Sound themselves is listening deliberately, and nothing here may mute that.
    var listeningBecauseOfAlert by remember(endpoint.id) { mutableStateOf(false) }
    var lastLoudAt by remember(endpoint.id) { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(nowMillis()) }
    var frameAspect by remember(endpoint.id) { mutableStateOf(0f) }
    var rotationHintSeen by remember(endpoint.id) { mutableStateOf(false) }

    // In-session only, never written to disk — the no-recording promise covers alert
    // history as much as it covers video.
    val alertHistory = remember(endpoint.id) { mutableStateListOf<CameraAlert>() }

    // The rail's meter. Kept out of the camera's sensitivity entirely — this measures what
    // arrived down the wire, not what the nursery decided was loud enough to report.
    var heardLevel by remember(endpoint.id) { mutableStateOf(0f) }
    val heardMeter = remember(endpoint.id) { SoundDetector() }

    // The control the remote starts on, and the one it is put back on if focus is ever lost.
    val soundAnchor = rememberFocusAnchor()

    val reducedMotion = LocalReducedMotion.current
    val window = rememberWindowClass()
    val audioPlayer = remember(endpoint.id) { createAudioPlayer(AudioConfig()) }

    KeepScreenAwake(enabled = settings.keepScreenAwake)

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
            alert = alert?.oneLine,
        )
    }

    LaunchedEffect(lastInteraction, window) {
        controlsVisible = true
        // Desktop keeps its chrome: there is nothing to reveal by hiding it. So does a
        // television, and for a harder reason — auto-hiding chrome is a touch idea. It
        // assumes a tap can bring it back, and a remote has no tap: once the bar had faded
        // there was nothing focusable left on screen, so the D-pad had nowhere to go and the
        // picture became a dead end with no visible way back into the app.
        if (isTelevision || window == WindowClass.EXPANDED || videoRendersBehindUi) {
            return@LaunchedEffect
        }
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
                                val raised = CameraAlert(
                                    kind = AlertKind.MOTION,
                                    atMillis = message.atMillis,
                                    magnitude = message.intensity,
                                )
                                alert = raised
                                alertHistory.record(raised)
                                raiseAlert(
                                    endpoint.named(cameraStatus),
                                    raised,
                                    settings.motionAlerts,
                                )
                                if (settings.listenOnAlert != ListenOnAlert.OFF && !audioOn) {
                                    audioOn = true
                                    listeningBecauseOfAlert = true
                                }
                                lastLoudAt = nowMillis()
                            }
                            is ControlMessage.SoundEvent -> {
                                val raised = CameraAlert(
                                    kind = AlertKind.SOUND,
                                    atMillis = message.atMillis,
                                    magnitude = message.level,
                                )
                                alert = raised
                                alertHistory.record(raised)
                                raiseAlert(
                                    endpoint.named(cameraStatus),
                                    raised,
                                    settings.soundAlerts,
                                )
                                // The whole point of the setting: the app has just said it
                                // heard something, and hearing it is the next thing the
                                // parent wants — before they have crossed the room to the
                                // remote, by which time it has usually stopped.
                                if (settings.listenOnAlert != ListenOnAlert.OFF && !audioOn) {
                                    audioOn = true
                                    listeningBecauseOfAlert = true
                                }
                                lastLoudAt = nowMillis()
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

    // Keyed on the alert itself, so a second event replaces the first *and* restarts the
    // dwell. It clears itself after five seconds whether or not anybody presses "Got it" —
    // the banner covers the top of the picture, and on a television nobody is going to walk
    // over and dismiss it, so a banner that waits to be acknowledged is a banner sitting
    // over the cot all night. Five seconds is long enough to read two short lines.
    // The event stays in the history either way, and in the notification shade.
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(ALERT_VISIBLE_MILLIS)
            alert = null
            // Belt and braces with [skipDpadFocus] on the banner's action: whatever else may
            // have been focusable inside a banner that has just left the composition, the
            // remote ends up back on the Sound control rather than nowhere. Cheap, and the
            // failure it prevents is a television whose remote stops working entirely.
            if (isTelevision) runCatching { soundAnchor.requestFocus() }
        }
    }

    // Audio reconnects on its own like the video does: a socket dropped by a roaming phone
    // used to leave sound dead until the parent noticed and toggled it twice.
    LaunchedEffect(audioOn, endpoint.id) {
        val player = audioPlayer
        if (!audioOn || player == null) {
            heardLevel = 0f
            return@LaunchedEffect
        }
        val client = ViewerClient(endpoint)
        player.start()
        try {
            while (true) {
                runCatching {
                    client.audioChunks().collect { chunk ->
                        player.write(chunk)
                        // Measured off the chunks already on their way to the speaker, with
                        // the same detector the camera runs. It reports what *this* device
                        // is hearing, which is the honest reading for a rail on the far end
                        // of a network — and it costs one pass over a buffer that has
                        // already been decoded and copied.
                        heardMeter.submit(chunk, nowMillis())
                        heardLevel = heardMeter.lastLevel
                        // The clock "until quiet" runs against. Anything above the floor
                        // counts as the room still being awake — this is deliberately a
                        // lower bar than the camera's alert threshold, so a baby who has
                        // settled to grizzling does not get the speaker cut mid-grizzle.
                        if (heardMeter.lastLevel >= QUIET_LEVEL) lastLoudAt = nowMillis()
                    }
                }
                delay(RECONNECT_DELAY_MILLIS)
            }
        } finally {
            player.stop()
            client.close()
            heardLevel = 0f
        }
    }

    // "Until quiet": the sound an alert switched on turns itself off again once the nursery
    // has been still for a while. Only ever the sound *this* turned on — [listeningBecauseOfAlert]
    // is cleared the moment the parent touches the control, so a deliberate listen is never
    // cut short.
    LaunchedEffect(listeningBecauseOfAlert, audioOn) {
        if (!listeningBecauseOfAlert || !audioOn) return@LaunchedEffect
        if (settings.listenOnAlert != ListenOnAlert.UNTIL_QUIET) return@LaunchedEffect
        while (true) {
            delay(QUIET_CHECK_MILLIS)
            if (nowMillis() - lastLoudAt < QUIET_FOR_MILLIS) continue
            audioOn = false
            listeningBecauseOfAlert = false
            return@LaunchedEffect
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

    // Above 840dp the chrome becomes a rail beside the picture. Not on web: there the video
    // is a real DOM element behind the canvas, so a rail drawn next to it would be drawn
    // next to nothing.
    // Keyed on the device, not only on the width. A television is where the rail matters
    // most — it is the only surface with no touch at all, so every control has to be a
    // standing target the remote can reach — and TV boxes do not reliably report an EXPANDED
    // window: plenty hand back 1920x1080 at a density that measures 640dp, which put a 55"
    // screen on the phone layout with auto-hiding chrome and no rail at all.
    val railPresent = (isTelevision || window == WindowClass.EXPANDED) && !videoIsSeparateLayer
    val chromeAlwaysVisible = videoIsSeparateLayer || isTelevision ||
        window == WindowClass.EXPANDED || controlsVisible

    // A landscape camera watched on an upright phone aspect-fits into roughly a third of the
    // screen, with the rest black. Nothing is wrong and nothing is cropped — the picture is
    // simply the wrong shape for the window — so the answer is to say so once rather than to
    // crop the cot out of frame. Landscape is already permitted on iPhone, so turning the
    // phone is all it takes.
    val portraitWindow = windowHeight() > windowWidth()
    val suggestRotation = !rotationHintSeen &&
        // Nobody turns a television sideways.
        !isTelevision &&
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
            night = night,
            railPresent = railPresent,
            // Reaching for the moon is a reason to keep the chrome up, not a reason to let
            // it hide: the parent has just told us they are looking at the screen.
            onNightChanged = {
                onNightChanged(it)
                lastInteraction = nowMillis()
            },
            // "Got it" clears the banner and nothing else. The event stays in the history,
            // because a parent who acknowledged an alert has not thereby forgotten it.
            onDismissAlert = { alert = null },
            onStatus = { status = it },
            onError = { failure = it },
            onAspectRatio = { frameAspect = it },
            onTouch = { lastInteraction = nowMillis() },
            onToggleAudio = {
                audioOn = !audioOn
                // The parent has taken the decision back. Whatever an alert did, this is now
                // their setting until they change it again.
                listeningBecauseOfAlert = false
                lastInteraction = nowMillis()
            },
            onBack = onBack,
        )

        // Above 840dp the chrome stops floating over the picture and becomes a rail beside
        // it, so nothing ever overlaps the frame.
        if (railPresent) {
            SideRail(
                soundAnchor = soundAnchor,
                name = cameraStatus?.deviceName ?: endpoint.name,
                // Host only. The rail is 352dp and the port is the half nobody reads aloud;
                // the phone's bar still prints the whole thing.
                address = endpoint.host,
                history = alertHistory,
                level = heardLevel,
                audioOn = audioOn,
                audioAvailable = audioAvailable,
                audioSupported = audioPlayer != null,
                night = night,
                // A desktop window has more room than a tablet and a pointer that never
                // has to reach, so the rail takes the extra width rather than the picture.
                width = if (windowWidth() >= DESKTOP_WIDTH) DESKTOP_RAIL else RAIL_WIDTH,
                onNightChanged = onNightChanged,
                onToggleAudio = {
                    audioOn = !audioOn
                    listeningBecauseOfAlert = false
                    lastInteraction = nowMillis()
                },
                onBack = onBack,
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
    alert: CameraAlert?,
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    chromeVisible: Boolean,
    statusTone: StatusTone,
    suggestRotation: Boolean,
    videoIsSeparateLayer: Boolean,
    reducedMotion: Boolean,
    night: Boolean,
    railPresent: Boolean,
    onNightChanged: (Boolean) -> Unit,
    onDismissAlert: () -> Unit,
    onStatus: (VideoStatus) -> Unit,
    onError: (String?) -> Unit,
    onAspectRatio: (Float) -> Unit,
    onTouch: () -> Unit,
    onToggleAudio: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        // The whole picture is a tap target on touch devices — that tap is what brings the
        // auto-hidden chrome back. On a television it is worse than useless: the chrome never
        // hides there, so it reveals nothing, and being clickable makes it focusable, so it
        // becomes a screen-sized focus target that swallows the D-pad before the rail beside
        // it ever gets a turn.
        modifier.then(
            if (isTelevision) {
                Modifier
            } else {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onTouch() }
            },
        ),
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
                failure = failure,
                // A disabled pill reading "No sound" says what, not why — and "why" is the
                // difference between a parent checking the camera and a parent giving up
                // on the app.
                audioUnavailable = audioSupported && !audioAvailable,
                night = night,
                // The rail owns the moon where there is a rail. Two of them on one screen
                // is two controls for one boolean.
                showMoon = !railPresent,
                onNightChanged = onNightChanged,
            )
        }

        if (!videoIsSeparateLayer) {
            AnimatedVisibility(
                visible = alert != null,
                // Ten pixels down over 280ms, once. The slide is what catches an eye that
                // was already on the picture; anything longer or repeated becomes the thing
                // the parent watches instead of the room.
                enter = slideInVertically(
                    animationSpec = tween(BmpTheme.motion.bannerMillis),
                    initialOffsetY = { -it / BANNER_SLIDE_DIVISOR },
                ) + fadeIn(tween(BmpTheme.motion.bannerMillis)),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                AlertBanner(
                    alert = alert,
                    onDismiss = onDismissAlert,
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(top = BANNER_TOP, start = CHROME_INSET, end = CHROME_INSET),
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
                    .safeDrawingPadding()
                    .padding(Space.md),
            )
        }

        if (suggestRotation) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (videoIsSeparateLayer && alert != null) {
                    AlertBanner(
                        alert = alert,
                        onDismiss = onDismissAlert,
                        modifier = Modifier.padding(
                            start = CHROME_INSET,
                            end = CHROME_INSET,
                            bottom = Space.xs,
                        ),
                    )
                }
                if (!railPresent) BottomBar(
                    name = cameraStatus?.deviceName ?: endpoint.name,
                    address = endpoint.id,
                    audioOn = audioOn,
                    audioAvailable = audioAvailable,
                    audioSupported = audioSupported,
                    fullWidth = videoIsSeparateLayer,
                    onToggleAudio = onToggleAudio,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun TopChrome(
    status: VideoStatus,
    latencyMillis: Long?,
    failure: String?,
    audioUnavailable: Boolean,
    night: Boolean,
    showMoon: Boolean,
    onNightChanged: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            // Held off the *screen* edges, not the picture's. Portrait and landscape frames
            // then share one layout, and neither crops the chrome off with the frame.
            .padding(horizontal = CHROME_INSET, vertical = Space.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            val (label, tone) = status.describe()
            StatusChip(
                label = label,
                tone = tone,
                trailing = latencyMillis
                    ?.takeIf { status == VideoStatus.LIVE }
                    ?.let { "${it.coerceAtLeast(0)} ms" },
            )

            // A black picture with no explanation is unactionable. Once the stream is live
            // the reason is stale, so it only shows while it is still true.
            // A video fault outranks a missing microphone: if there is no picture, that is
            // the sentence the parent needs first.
            val reason = status.reason()
                ?: failure?.takeIf { status != VideoStatus.LIVE }
                ?: MISSING_MIC.takeIf { audioUnavailable }
            if (reason != null) {
                Spacer(Modifier.size(Space.xs))
                Surface(
                    color = MaterialTheme.colorScheme.background,
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
        }
        if (showMoon) {
            Spacer(Modifier.size(Space.xs))
            // The bedside device is the one most likely to be in a dark room, and the
            // parent watching it should not have to close the picture to turn the lights
            // down.
            MoonButton(night = night, onNightChanged = onNightChanged)
        }
    }
}

/**
 * The alert that just fired. Firm, and never a takeover.
 *
 * It sits in a fixed slot below the status chip — dead space above the picture — so the
 * frame is never covered: the alert and the evidence a parent needs to judge it are visible
 * in one glance.
 *
 * A berry outline on cream rather than a berry fill. A full red card at 3am is a light
 * source; a 2dp outline is legible across a room and emits almost nothing. And it slides in
 * once, 10px over 280ms, and then stops — a pulsing alert in a dark room makes a parent's
 * own heart rate the thing they end up noticing.
 */
@Composable
private fun AlertBanner(
    alert: CameraAlert?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    alert ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(BANNER_BORDER, MaterialTheme.colorScheme.error),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = BANNER_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The rattle carries sound and alerts everywhere in the app, and this is the one
            // banner it appears on — an alert is not an error, which is the surface the baby
            // set stays off.
            IconPlate(
                icon = BmpIcons.Rattle,
                fill = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                size = PlateSize.row,
            )
            Spacer(Modifier.size(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = alert.headline,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = BANNER_TITLE),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    // What was actually measured, not what the other sensor might have been
                    // doing. See [CameraAlert].
                    text = alert.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = BANNER_META),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // Blueberry, like every other affordance. Berry here would make the way out of
            // the alert look like part of the alarm.
            val dismissInteraction = remember { MutableInteractionSource() }
            TextButton(
                onClick = onDismiss,
                interactionSource = dismissInteraction,
                // The alert banner is drawn over the picture on every layout, television
                // included, so its one action needs a ring like everything else.
                modifier = Modifier
                    // The banner clears itself after five seconds. On a television that made
                    // it a focus trap with a five-second fuse: focus the button, wait, and
                    // the node it was on no longer exists.
                    .skipDpadFocus()
                    .focusRing(dismissInteraction, MaterialTheme.shapes.medium),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(
                    text = "Got it",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = BANNER_TITLE),
                    fontWeight = FontWeight.Bold,
                )
            }
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
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(horizontal = CHROME_INSET)
            .padding(bottom = BAR_BOTTOM)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(MaterialTheme.shapes.extraLarge),
        // Cream and opaque, the same pill the status chip is. The bar is chrome laid over a
        // picture, and chrome that takes its colour from the frame behind it is chrome that
        // disappears exactly when the frame goes dark.
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier.padding(horizontal = BAR_H_PADDING, vertical = BAR_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = BmpIcons.Teddy,
                fill = BmpTheme.tints.lemon.fill,
                contentColor = BmpTheme.tints.lemon.glyph,
                size = PlateSize.row,
            )
            Spacer(Modifier.size(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = BAR_NAME),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                // The privacy line's slot on this screen — the address, on your network,
                // right now, under the name of the device it belongs to.
                Text(
                    text = "On your WiFi · $address",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = BAR_ADDRESS),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (audioSupported) {
                Spacer(Modifier.size(Space.xs))
                SoundPill(
                    on = audioOn,
                    available = audioAvailable,
                    onClick = onToggleAudio,
                )
            }
            val closeInteraction = remember { MutableInteractionSource() }
            TextButton(
                onClick = onBack,
                interactionSource = closeInteraction,
                modifier = Modifier.focusRing(closeInteraction, MaterialTheme.shapes.medium),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = SOUND_TEXT),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Sound, on or off.
 *
 * **Filled is on.** It used to be a `TextButton` reading "Sound off", which a parent read as
 * an instruction — tap here to turn sound off — rather than as the state it was reporting.
 * A filled marigold pill says the sound is live in the same colour the status dot uses for
 * the picture; an outline says it is not.
 */
@Composable
private fun SoundPill(
    on: Boolean,
    available: Boolean,
    onClick: () -> Unit,
) {
    val live = BmpTheme.semantic.statusLive
    val shape = RoundedCornerShape(SOUND_RADIUS)
    val fill = if (on && available) live else Color.Transparent
    val border = when {
        !available -> MaterialTheme.colorScheme.outlineVariant
        on -> live
        else -> MaterialTheme.colorScheme.outline
    }
    val content = when {
        !available -> MaterialTheme.colorScheme.onSurfaceVariant
        on -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .heightIn(min = Touch.min)
            .clip(shape)
            .then(if (available) Modifier.pressable(onClick = onClick) else Modifier)
            .border(CARD_BORDER, border, shape),
        color = fill,
        shape = shape,
    ) {
        Row(
            Modifier.padding(horizontal = SOUND_H_PADDING, vertical = SOUND_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = BmpIcons.Rattle,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(SOUND_ICON),
            )
            Spacer(Modifier.size(Space.xxs))
            Text(
                text = when {
                    !available -> "No sound"
                    on -> "Sound on"
                    else -> "Sound off"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = SOUND_TEXT),
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

/**
 * The tablet and desktop rail: everything the floating bar carries, unstacked and beside
 * the picture rather than over it.
 *
 * Above 840dp the bar stops being the right shape. There is room here for chrome that never
 * has to hide, so it never covers the frame and never has to be tapped back into existence —
 * and the width it gains is spent on the one thing a phone cannot afford, the event strip.
 * On a phone "Earlier today" would be a scroll the parent will not do; on a tablet standing
 * on a kitchen counter it is the reason to glance over.
 *
 * Same components, same order, same type sizes. A tablet is further away, so 16sp is still
 * 16sp.
 */
@Composable
private fun SideRail(
    soundAnchor: androidx.compose.ui.focus.FocusRequester,
    name: String,
    address: String,
    history: List<CameraAlert>,
    level: Float,
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    night: Boolean,
    width: Dp,
    onNightChanged: (Boolean) -> Unit,
    onToggleAudio: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .border(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant)
            .safeDrawingPadding()
            .padding(horizontal = Space.xl, vertical = RAIL_V_PADDING),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = BmpIcons.Teddy,
                fill = BmpTheme.tints.lemon.fill,
                contentColor = BmpTheme.tints.lemon.glyph,
                size = PlateSize.medium,
            )
            Spacer(Modifier.size(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = RAIL_NAME),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "On your WiFi · $address",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = RAIL_ADDRESS),
                    fontWeight = FontWeight.SemiBold,
                    color = BmpTheme.semantic.privacy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(Space.xs))
            // A tablet left on a counter overnight is the strongest case for night mode in
            // the product — it is the one screen that stays on for eight hours.
            MoonButton(night = night, onNightChanged = onNightChanged)
        }

        Spacer(Modifier.height(RAIL_GAP))

        RailCard(
            title = "The room right now",
            fill = MaterialTheme.colorScheme.surface,
            border = MaterialTheme.colorScheme.outlineVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            if (audioOn && audioAvailable) {
                SoundMeter(level = level, sensitivity = METER_SENSITIVITY, height = RAIL_METER)
            } else {
                // The meter reads the sound this device is receiving, so with sound off
                // there is nothing to read. A flat meter here would say "quiet room" when
                // what it means is "not listening" — the one lie this screen must not tell.
                Text(
                    text = if (audioAvailable) {
                        "Turn sound on to hear the room."
                    } else {
                        MISSING_MIC
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = RAIL_STRIP_TEXT),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(RAIL_GAP))

        val lemon = BmpTheme.tints.lemon
        RailCard(
            title = "Earlier today",
            fill = lemon.fill,
            border = lemon.border,
            labelColor = lemon.glyph,
        ) {
            if (history.isEmpty()) {
                Text(
                    text = "Nothing yet.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = RAIL_STRIP_TEXT),
                    color = lemon.contentMuted,
                )
            } else {
                // A fixed height that scrolls, rather than a list that grows.
                //
                // The rail is a column: every event added here pushed the Sound and Close
                // buttons further down, and after a busy hour they were off the bottom of a
                // television screen entirely — the controls disappearing because the baby
                // moved is exactly backwards. The card now takes the same room whether it
                // holds one event or twenty, and the overflow scrolls inside it.
                Column(
                    modifier = Modifier
                        .height(RAIL_STRIP_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    // The whole history, not the first six: the card no longer grows with it,
                    // so capping the list only hid events a parent could otherwise scroll to.
                    // Newest first, so the cap costs nothing at a glance.
                    for (entry in history) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                // The rattle for sound, the teddy for movement. Every row
                                // used to carry the rattle, which made a list of six events
                                // look like six of the same thing — and the one question a
                                // parent asks this list is which kind woke them.
                                imageVector = when (entry.kind) {
                                    AlertKind.SOUND -> BmpIcons.Rattle
                                    AlertKind.MOTION -> BmpIcons.Teddy
                                },
                                contentDescription = null,
                                tint = lemon.glyph,
                                modifier = Modifier.size(RAIL_STRIP_ICON),
                            )
                            Spacer(Modifier.size(Space.xs))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.headline,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = RAIL_STRIP_TEXT,
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = lemon.content,
                                    maxLines = 1,
                                )
                                Text(
                                    text = entry.detail,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = RAIL_STRIP_DETAIL,
                                    ),
                                    color = lemon.contentMuted,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = clockTime(entry.atMillis),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = RAIL_STRIP_TIME,
                                ),
                                color = lemon.glyph,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.xs))
        Text(
            // The no-recording promise covers this list too.
            text = "This session only. Nothing is written to disk.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = RAIL_STRIP_TEXT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Pinned to the bottom, where a hand resting on the edge of a propped-up tablet
        // finds them without reaching across the picture.
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (audioSupported) {
                RailSoundButton(
                    on = audioOn,
                    available = audioAvailable,
                    onClick = onToggleAudio,
                    // Where the remote lands when the live view opens. Sound is the control a
                    // parent reaches for on this screen, and Close sits beside it, so the way
                    // out is one press away.
                    modifier = Modifier.weight(1f).focusAnchor(soundAnchor),
                )
            }
            RailCloseButton(onClick = onBack)
        }
    }
}

@Composable
private fun RailCard(
    title: String,
    fill: Color,
    border: Color,
    labelColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = fill,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, border),
    ) {
        Column(Modifier.padding(horizontal = RAIL_CARD_H, vertical = RAIL_CARD_V)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = RAIL_LABEL,
                    letterSpacing = RAIL_TRACKING,
                ),
                fontWeight = FontWeight.Bold,
                color = labelColor,
            )
            Spacer(Modifier.height(Space.sm))
            content()
        }
    }
}

/** The rail's sound control: a full-width button rather than the bar's pill. */
@Composable
private fun RailSoundButton(
    on: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = BmpTheme.semantic.statusLive
    val shape = RoundedCornerShape(RAIL_BUTTON_RADIUS)
    val fill = if (on && available) live else MaterialTheme.colorScheme.surface
    val content = when {
        !available -> MaterialTheme.colorScheme.onSurfaceVariant
        on -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .height(RAIL_BUTTON_HEIGHT)
            .clip(shape)
            .then(if (available) Modifier.pressable(onClick = onClick) else Modifier),
        color = fill,
        shape = shape,
        border = BorderStroke(
            CARD_BORDER,
            if (on && available) live else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = BmpIcons.Rattle,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(SOUND_ICON),
            )
            Spacer(Modifier.size(Space.xs))
            Text(
                text = when {
                    !available -> "No sound"
                    on -> "Sound on"
                    else -> "Sound off"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = RAIL_BUTTON_TEXT),
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

@Composable
private fun RailCloseButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(RAIL_BUTTON_RADIUS)
    Surface(
        modifier = Modifier
            .width(RAIL_CLOSE_WIDTH)
            .height(RAIL_BUTTON_HEIGHT)
            .clip(shape)
            .pressable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Close",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = RAIL_BUTTON_TEXT),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Wall-clock time for the event strip.
 *
 * The strip's whole job is to answer "when", and "3 minutes ago" stops being an answer the
 * moment a parent looks away and back — a fixed 13:42 does not go stale while unread.
 */
private fun clockTime(atMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(atMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

private fun CameraEndpoint.named(status: ControlMessage.Status?): CameraEndpoint {
    val reported = status?.deviceName?.takeIf { it.isNotBlank() } ?: return this
    return if (reported == name) this else copy(name = reported)
}

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
private fun raiseAlert(endpoint: CameraEndpoint, alert: CameraAlert, notify: Boolean) {
    // The in-app banner and the history are unconditional — they are the screen doing its
    // job. Only the system notification is a preference, because it is the only part that
    // interrupts a parent who did not ask to be interrupted.
    if (!notify) return
    notifyAlert(endpoint, alert)
}

/** Newest first, capped — this is a glance-at list, not a log. */
private fun MutableList<CameraAlert>.record(alert: CameraAlert) {
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
        "The nursery device moved to another access point. Retrying every 2 seconds."
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
    VideoStatus.FAILED -> "The nursery device closed BabyMonitor Pro."
    else -> null
}

/** Above this, a frame is wide enough that a portrait phone wastes most of its screen. */
private const val LANDSCAPE_THRESHOLD = 1.2f
private const val ROTATION_HINT_MILLIS = 7000L
private val RAIL_WIDTH = 352.dp

/** A desktop window has more room and a pointer that never has to reach. */
private val DESKTOP_RAIL = 400.dp
private val DESKTOP_WIDTH = 1200.dp

private val RAIL_V_PADDING = 26.dp
private val RAIL_GAP = 14.dp
private val RAIL_NAME = 18.sp
private val RAIL_ADDRESS = 11.5.sp
private val RAIL_CARD_H = 18.dp
private val RAIL_CARD_V = 16.dp
private val RAIL_LABEL = 10.5.sp
private val RAIL_TRACKING = 0.07.em
private val RAIL_METER = 38.dp
/**
 * Four rows' worth. Enough to see that a quiet night was quiet and a bad one was not, without
 * the card taking a third of the rail.
 */
private val RAIL_STRIP_HEIGHT = 168.dp

private val RAIL_STRIP_DETAIL = 11.sp
private val RAIL_STRIP_TEXT = 12.5.sp
private val RAIL_STRIP_TIME = 11.sp
private val RAIL_STRIP_ICON = 15.dp
private val RAIL_BUTTON_RADIUS = 18.dp
private val RAIL_BUTTON_HEIGHT = 56.dp
private val RAIL_BUTTON_TEXT = 14.sp
private val RAIL_CLOSE_WIDTH = 88.dp

/**
 * The rail's meter shows the level, never a threshold — a viewer has no say over what the
 * camera treats as loud, so every bar takes the resting colour.
 */
private const val METER_SENSITIVITY = 0

/** Chrome sits this far from the screen's edges, at every size and in both orientations. */
/** Said in one place, so the pill and the rail cannot drift apart. */
private const val MISSING_MIC =
    "This camera has no microphone available, so there is nothing to listen to."

private val CHROME_INSET = 20.dp

/** The banner's fixed slot: below the status chip, above the picture. */
private val BANNER_TOP = 112.dp
private val BANNER_BORDER = 2.dp
private val BANNER_V_PADDING = 14.dp
private val BANNER_TITLE = 15.sp
private val BANNER_META = 11.5.sp

private val BAR_BOTTOM = 26.dp
private val BAR_H_PADDING = 18.dp
private val BAR_V_PADDING = 14.dp
private val BAR_NAME = 15.sp
private val BAR_ADDRESS = 11.5.sp
private val SOUND_TEXT = 12.5.sp
private val SOUND_RADIUS = 16.dp
private val SOUND_H_PADDING = 14.dp
private val SOUND_V_PADDING = 9.dp
private val SOUND_ICON = 16.dp

/** A 10dp slide on a banner around 66dp tall. */
private const val BANNER_SLIDE_DIVISOR = 6
private const val ALERT_HISTORY_MAX = 20
private const val STALE_FRAME_ALPHA = 0.3f
private const val CONTROLS_TIMEOUT_MILLIS = 4000L
private const val PING_INTERVAL_MILLIS = 5000L
/**
 * How quiet counts as quiet, and for how long.
 *
 * Thirty seconds rather than a few: a baby who has gone back to sleep does it in fits, and a
 * speaker that cuts out after five seconds of silence and comes back on the next snuffle is
 * worse than one that simply stays on.
 */
private const val QUIET_LEVEL = 0.02f
private const val QUIET_FOR_MILLIS = 30_000L
private const val QUIET_CHECK_MILLIS = 2_000L

private const val RECONNECT_DELAY_MILLIS = 1500L
private const val ALERT_VISIBLE_MILLIS = 5_000L
