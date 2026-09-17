package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import com.hazemafaneh.babymonitorpro.audio.createAudioPlayer
import com.hazemafaneh.babymonitorpro.client.PlatformViewingSession
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.BatteryState
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
import com.hazemafaneh.babymonitorpro.ui.components.LevelMarker
import com.hazemafaneh.babymonitorpro.ui.components.soundLevelPercent
import com.hazemafaneh.babymonitorpro.ui.components.motionLevelPercent
import com.hazemafaneh.babymonitorpro.ui.components.thresholdForSensitivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Rotation, on this screen only.
    //
    // The camera phone is propped against a cot rail, wedged under a mattress or taped to a
    // shelf, and whichever way *it* thinks is up is frequently not the way the room is.
    // Turning the picture here costs nothing and asks nothing of the nursery device — and it
    // is the viewer, not the camera, that knows which way the parent is holding their phone.
    var spin by remember(endpoint.id) { mutableStateOf(0) }
    // Held here rather than in the pane, because a television has no pinch and drives this
    // from buttons in the rail. Same state either way, two ways of changing it.
    var zoom by remember(endpoint.id) { mutableStateOf(1f) }
    var pan by remember(endpoint.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember(endpoint.id) { mutableStateOf(IntSize.Zero) }

    // Clamped in one place, whichever control moved it. A zoom applied from a button can
    // shrink the picture out from under a pan in a way a pinch never does, so the two are
    // settled together rather than at each call site.
    fun clampedPan(candidate: Offset, atZoom: Float): Offset {
        if (atZoom <= 1f || viewport == IntSize.Zero) return Offset.Zero
        val maxX = (viewport.width * (atZoom - 1f)) / 2f
        val maxY = (viewport.height * (atZoom - 1f)) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }
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

    // Hoisted out of the control effect so the UI can send on it too — this is the channel a
    // parent changes the nursery device's settings over, from wherever they are standing.
    val outgoing = remember(endpoint.id) {
        MutableSharedFlow<ControlMessage>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    var showCameraControls by remember(endpoint.id) { mutableStateOf(false) }

    val battery = remember(cameraStatus?.batteryPercent, cameraStatus?.charging) {
        BatteryState(
            percent = cameraStatus?.batteryPercent ?: -1,
            charging = cameraStatus?.charging ?: false,
        )
    }

    val reducedMotion = LocalReducedMotion.current
    val window = rememberWindowClass()
    val audioPlayer = remember(endpoint.id) { createAudioPlayer(AudioConfig()) }

    // One client for the life of the screen, not one per toggle. "Until quiet" turns the
    // sound on and off by itself now, and each cycle used to build and tear down a whole
    // Ktor client — engine, thread pool and sockets — for a socket that lives for a minute.
    val audioClient = remember(endpoint.id) { ViewerClient(endpoint) }
    DisposableEffect(audioClient) {
        onDispose { audioClient.close() }
    }

    KeepScreenAwake(enabled = settings.keepScreenAwake)

    // The watching session on the Lock Screen and in the Dynamic Island, for exactly as long
    // as this screen is up. Keyed on the endpoint so switching cameras ends one session and
    // starts another rather than relabelling the first.
    DisposableEffect(endpoint.id) {
        liveSessions.startViewing(endpoint)
        // And the foreground service that keeps this device *allowed* to watch once the
        // parent switches app. The live session above is only a surface to look at; without
        // this the sockets behind it are frozen the moment the app leaves the screen.
        PlatformViewingSession.begin(endpoint.name)
        onDispose {
            liveSessions.stopViewing()
            PlatformViewingSession.end()
        }
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

    // The chrome no longer hides anywhere.
    //
    // It used to fade out four seconds after the last touch on a phone held in portrait, on
    // the theory that the picture is the interface. In practice the things it took with it
    // are the things a parent actually looks at: whether the feed is live, the latency, and
    // the Sound control. Wanting to know "is this still working" is the *reason* for glancing
    // at the phone, and the answer was hidden behind a tap the parent had to know to make.
    //
    // It was also the last remaining focus trap on a television, where there is no tap to
    // bring anything back.
    LaunchedEffect(lastInteraction) { controlsVisible = true }

    // Control channel: status, alerts, and a round-trip ping that doubles as the
    // connection's health check.
    LaunchedEffect(endpoint.id) {
        val client = ViewerClient(endpoint)
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
                            is ControlMessage.Status -> {
                                cameraStatus = message
                                // Remembered only once a camera has actually answered, and
                                // under the name it calls itself. An address that refused is
                                // not worth offering tomorrow, and "Nursery" is a great deal
                                // easier to recognise than 100.87.4.19 — which is exactly the
                                // address a tailnet gives you and discovery cannot find.
                                settings.rememberCamera(
                                    name = message.deviceName.ifBlank { endpoint.name },
                                    host = endpoint.host,
                                    port = endpoint.port,
                                    atMillis = nowMillis(),
                                )
                            }
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
        // Off the main thread, and this is not a nicety — it is why the app froze.
        //
        // A LaunchedEffect body runs on the composition's dispatcher, which on Android is
        // the UI thread, and `collect` runs its lambda in the *collector's* context. So
        // every audio chunk was calling AudioTrack.write() on the main thread, and in
        // MODE_STREAM that call blocks until the track has room — about a tenth of a second,
        // ten times a second, for as long as the sound was on. The RMS pass over each chunk
        // was on the main thread too. The UI thread was starved from the moment sound
        // started: the picture juddered, the remote stopped responding, and Android
        // eventually killed the app for not answering.
        //
        // It was always broken; it only became easy to hit when an alert started turning the
        // sound on by itself.
        withContext(Dispatchers.Default) {
            player.start()
            try {
                while (true) {
                    runCatching {
                        audioClient.audioChunks().collect { chunk ->
                            player.write(chunk)
                            // Measured off the chunks already on their way to the speaker,
                            // with the same detector the camera runs. It reports what *this*
                            // device is hearing, which is the honest reading for a rail on
                            // the far end of a network — and it costs one pass over a buffer
                            // that has already been decoded and copied.
                            heardMeter.submit(chunk, nowMillis())
                            // Snapshot state is safe to write from any thread; Compose
                            // schedules the recomposition itself.
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
                heardLevel = 0f
            }
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
    // Always. See the effect above.
    val chromeAlwaysVisible = true

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

    // Named apart from the video `status` above: one is the connection, this is the camera's
    // own report of itself.
    val remoteStatus = cameraStatus

    // An explicit Box, and the panel emitted *after* the picture.
    //
    // Both were siblings of whatever container the navigation host provides, and the picture
    // is a full-screen Row with a black background — so it was painted straight over the
    // panel, every time. The panel was being composed, laid out and given focus; it was
    // simply underneath. Tapping Camera looked like it did nothing at all.
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().background(Color.Black)) {
        LivePane(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            endpoint = endpoint,
            status = status,
            latencyMillis = latencyMillis,
            cameraStatus = cameraStatus,
            // Reported by the camera in every status: the nursery phone's battery is the one
            // nobody can see, because that phone is face-down in a dark room.
            battery = battery,
            failure = failure,
            alert = alert,
            audioOn = audioOn,
            audioAvailable = audioAvailable,
            audioSupported = audioPlayer != null,
            chromeVisible = chromeAlwaysVisible,
            statusTone = status.describe().second,
            suggestRotation = suggestRotation,
            videoIsSeparateLayer = videoIsSeparateLayer,
            spin = spin,
            zoom = zoom,
            pan = pan,
            onZoomChange = {
                zoom = it
                pan = clampedPan(pan, it)
            },
            onPanChange = { pan = clampedPan(it, zoom) },
            onViewport = { viewport = it },
            // The data-saver setting, asked of the camera rather than applied here.
            maxFps = settings.dataSaverFps,
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
            // Only once the camera has told us what it currently is: a panel of controls
            // showing guesses would be worse than no panel.
            onCameraControls = if (cameraStatus != null) {
                { showCameraControls = true }
            } else {
                null
            },
            onRotate = {
                spin = (spin + QUARTER_TURN) % FULL_TURN
                lastInteraction = nowMillis()
            },
            onBack = onBack,
        )

        // Above 840dp the chrome stops floating over the picture and becomes a rail beside
        // it, so nothing ever overlaps the frame.
        if (railPresent) {
            SideRail(
                status = cameraStatus,
                soundAnchor = soundAnchor,
                onCameraControls = if (cameraStatus != null) {
                    { showCameraControls = true }
                } else {
                    null
                },
                onRotate = {
                    spin = (spin + QUARTER_TURN) % FULL_TURN
                    lastInteraction = nowMillis()
                },
                zoom = zoom,
                onZoomChange = {
                    zoom = it
                    pan = clampedPan(pan, it)
                    lastInteraction = nowMillis()
                },
                onNudge = { dx, dy ->
                    // A press moves the picture by a fixed slice of the window, which is the
                    // same distance whatever the zoom — a nudge that shrank as you zoomed in
                    // would be most useless exactly where panning matters most.
                    pan = clampedPan(
                        pan + Offset(viewport.width * dx * PAN_STEP, viewport.height * dy * PAN_STEP),
                        zoom,
                    )
                    lastInteraction = nowMillis()
                },
                name = cameraStatus?.deviceName ?: endpoint.name,
                // Host only. The rail is 352dp and the port is the half nobody reads aloud;
                // the phone's bar still prints the whole thing.
                address = endpoint.host,
                battery = battery,
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

        // Over the picture, not under it.
        if (showCameraControls && remoteStatus != null) {
            RemoteCameraControls(
                status = remoteStatus,
                // Only while this device is actually listening: the meter reads the audio
                // arriving here, and with the sound off there is nothing arriving to read.
                roomLevel = heardLevel.takeIf { audioOn && audioAvailable },
                onDismiss = { showCameraControls = false },
                onChange = { change -> outgoing.tryEmit(change) },
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
    battery: BatteryState,
    failure: String?,
    alert: CameraAlert?,
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    chromeVisible: Boolean,
    statusTone: StatusTone,
    suggestRotation: Boolean,
    videoIsSeparateLayer: Boolean,
    spin: Int,
    zoom: Float,
    pan: Offset,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    onViewport: (IntSize) -> Unit,
    maxFps: Int?,
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
    onCameraControls: (() -> Unit)?,
    onRotate: () -> Unit,
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
        // Pinch to zoom, drag to move, double-tap to come back.
        //
        // A cot fills a fraction of a wide-angle phone camera's frame, and the thing a parent
        // is actually trying to see — is she breathing, is that her face or the blanket — is a
        // few hundred pixels in the middle of it. The stream is 720p; the screen is showing it
        // at a third of that. There is real detail here to magnify, and it costs nothing to
        // send because the zoom is entirely on this device.
        //
        // Bounded to 4x, which is where the JPEG blocks take over from the baby, and the pan
        // is clamped so the picture can never be dragged off its own edges — a black screen
        // you have to guess your way out of is worse than no zoom at all.
        val transform = rememberTransformableState { zoomChange, panChange, _ ->
            onZoomChange((zoom * zoomChange).coerceIn(1f, MAX_ZOOM))
            onPanChange(pan + panChange)
        }

        MjpegVideo(
            endpoint = endpoint,
            maxFps = maxFps,
            modifier = if (videoIsSeparateLayer) {
                Modifier.fillMaxSize().padding(top = 56.dp, bottom = 88.dp)
            } else {
                Modifier.fillMaxSize()
            }
                // A dead feed at full brightness reads as live. Holding the last frame back
                // keeps it as context without claiming it is current — and it is a fade,
                // not a blink, per the dark-room rule.
                .alpha(if (status == VideoStatus.RECONNECTING) STALE_FRAME_ALPHA else 1f)
                .onSizeChanged { onViewport(it) }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                    rotationZ = spin.toFloat()
                }
                // Not on a television: there is nothing to pinch, and a transformable node
                // there is one more thing for the D-pad to get caught on.
                .then(if (isTelevision) Modifier else Modifier.transformable(transform))
                .then(
                    if (isTelevision) {
                        Modifier
                    } else {
                        Modifier.pointerInput(endpoint.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // Straight back to the whole picture. The way out of a
                                    // zoom has to be one gesture a parent can make without
                                    // looking, at 3am, holding a baby.
                                    onZoomChange(1f)
                                    onPanChange(Offset.Zero)
                                },
                            )
                        }
                    },
                ),
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
                    battery = battery,
                    onCameraControls = onCameraControls,
                    onRotate = onRotate,
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
    battery: BatteryState,
    onCameraControls: (() -> Unit)?,
    onRotate: () -> Unit,
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    fullWidth: Boolean,
    onToggleAudio: () -> Unit,
    onBack: () -> Unit,
) {
    // Two rows on a phone, one on anything wider.
    //
    // The bar carries six things: the mark, the camera's name, its address, the battery, the
    // sound pill and three actions. On a 393pt screen that is more than fits on a line — the
    // name was squeezed to nothing and the battery clipped mid-word, so "0% · ch" sat where
    // the nursery's name should be. Identity on the first line, controls on the second.
    //
    // Written as two explicit rows rather than a wrapping one: a flow layout counts every
    // spacer as an item and broke the controls across three lines in an order nobody chose.
    val stacked = rememberWindowClass().isCompact

    Surface(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(horizontal = CHROME_INSET)
            .padding(bottom = BAR_BOTTOM)
            .then(if (fullWidth || stacked) Modifier.fillMaxWidth() else Modifier)
            .clip(MaterialTheme.shapes.extraLarge),
        // Cream and opaque, the same pill the status chip is. The bar is chrome laid over a
        // picture, and chrome that takes its colour from the frame behind it is chrome that
        // disappears exactly when the frame goes dark.
        color = MaterialTheme.colorScheme.background,
    ) {
        if (stacked) {
            Column(
                Modifier.padding(horizontal = BAR_H_PADDING, vertical = BAR_V_PADDING),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BarIdentity(name, address, battery, Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarControls(
                        audioOn = audioOn,
                        audioAvailable = audioAvailable,
                        audioSupported = audioSupported,
                        onToggleAudio = onToggleAudio,
                        onRotate = onRotate,
                        onCameraControls = onCameraControls,
                        onBack = onBack,
                    )
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = BAR_H_PADDING, vertical = BAR_V_PADDING),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIdentity(name, address, battery, Modifier.weight(1f))
                BarControls(
                    audioOn = audioOn,
                    audioAvailable = audioAvailable,
                    audioSupported = audioSupported,
                    onToggleAudio = onToggleAudio,
                    onRotate = onRotate,
                    onCameraControls = onCameraControls,
                    onBack = onBack,
                )
            }
        }
    }
}

/** Which camera this is, where it lives, and how much battery it has left. */
@Composable
private fun BarIdentity(
    name: String,
    address: String,
    battery: BatteryState,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconPlate(
            icon = BmpIcons.Teddy,
            fill = BmpTheme.tints.lemon.fill,
            contentColor = BmpTheme.tints.lemon.glyph,
            size = PlateSize.row,
        )
        Spacer(Modifier.size(Space.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = BAR_NAME),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Gives up space to the battery rather than taking all of it: an
                    // unweighted name left the battery a column a few pixels wide, which it
                    // filled one character per line.
                    modifier = Modifier.weight(1f, fill = false),
                )
                BatteryLabel(battery, Modifier.padding(start = Space.xs))
            }
            // The privacy line's slot on this screen — the address, on your network, right
            // now, under the name of the device it belongs to.
            Text(
                text = "On your WiFi · $address",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = BAR_ADDRESS),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Sound, rotate, the camera's own settings, and the way out. */
@Composable
private fun BarControls(
    audioOn: Boolean,
    audioAvailable: Boolean,
    audioSupported: Boolean,
    onToggleAudio: () -> Unit,
    onRotate: () -> Unit,
    onCameraControls: (() -> Unit)?,
    onBack: () -> Unit,
) {
    if (audioSupported) {
        SoundPill(on = audioOn, available = audioAvailable, onClick = onToggleAudio)
    }

    val rotateInteraction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onRotate,
        interactionSource = rotateInteraction,
        modifier = Modifier.size(BAR_ICON_BUTTON).focusRing(rotateInteraction, CircleShape),
    ) {
        Icon(
            imageVector = BmpIcons.Rotate,
            contentDescription = "Turn the picture a quarter turn",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(SOUND_ICON),
        )
    }

    if (onCameraControls != null) {
        val controlsInteraction = remember { MutableInteractionSource() }
        // A glyph, not the word. The bar already carries the camera's name, its address and
        // the sound pill; "Camera" spelled out was the straw that squeezed the name out of
        // existence on a phone, and the panel it opens is titled with that name anyway.
        IconButton(
            onClick = onCameraControls,
            interactionSource = controlsInteraction,
            modifier = Modifier.size(BAR_ICON_BUTTON).focusRing(controlsInteraction, CircleShape),
        ) {
            Icon(
                imageVector = BmpIcons.Camera,
                contentDescription = "Camera settings",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(SOUND_ICON),
            )
        }
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

/**
 * The battery of the device doing the filming.
 *
 * Beside that device's name, because it is a fact *about the camera* and not about this
 * phone — a parent glancing at a tablet in the kitchen has to be able to tell whose 12% it
 * is. Silent above a fifth: a number that is always on screen is a number nobody reads, and
 * this one only matters when it is low. Charging is called out rather than hidden, because a
 * phone at 8% on a charger is fine and a phone at 8% on a shelf is the night ending early.
 */
@Composable
private fun BatteryLabel(battery: BatteryState, modifier: Modifier = Modifier) {
    // Always, once it is known.
    //
    // It appeared only when low or charging, on the theory that a number nobody needs is
    // noise. That is wrong for this particular number: the question a parent has about the
    // nursery phone is "will it still be filming at 4am", and an indicator that shows up only
    // once the answer is already no does not let anyone act in time.
    if (!battery.known) return
    val tone = when {
        battery.low -> MaterialTheme.colorScheme.error
        battery.charging -> BmpTheme.semantic.statusLive
        // Neither draining dangerously nor filling: a plain reading, in the colour the rest
        // of this screen's metadata uses.
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BatteryGlyph(percent = battery.percent, charging = battery.charging, tone = tone)
        Spacer(Modifier.size(Space.xxs))
        Text(
            text = if (battery.charging) "${battery.percent}% · charging" else "${battery.percent}%",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = BAR_ADDRESS),
            fontWeight = FontWeight.Bold,
            color = tone,
            // One line, never wrapped. It sits beside a name of unknown length, and a battery
            // reading that breaks across lines is worse than no battery reading.
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One reading in the rail: what the room is doing, and the line that would alert on it.
 *
 * The mark is the same one the settings screen draws over its sliders, so a parent who set a
 * threshold there recognises the picture here — and can see, without doing arithmetic,
 * whether the room is anywhere near it.
 */
@Composable
private fun RailLevel(
    label: String,
    reading: Float?,
    threshold: Int?,
    absent: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = RAIL_STRIP_DETAIL),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(RAIL_LEVEL_LABEL),
        )
        if (reading == null || threshold == null) {
            // Never a mark pinned at zero: that reads as a silent, still room rather than as
            // nothing measured, and those are opposite facts.
            Text(
                text = absent,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = RAIL_STRIP_DETAIL),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            return@Row
        }
        LevelMarker(
            levelPercent = reading,
            thresholdPercent = threshold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${reading.toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = RAIL_STRIP_DETAIL,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.xxs),
        )
    }
}

/**
 * A battery, drawn rather than lettered.
 *
 * "53%" on its own is a percentage of something unstated — of the picture that changed, of the
 * room's loudness, of a dozen other numbers this app shows. The cell shape says which, in less
 * width than the word would take, and it is the one glyph every parent already knows.
 *
 * It fills to match the reading instead of being a fixed outline: at three metres the *shape*
 * carries the state and the digits merely confirm it, which is the right way round for a
 * thing read at a glance across a dark room. Charging adds a bolt, because a phone at 12%
 * filling up and a phone at 12% draining are opposite facts.
 */
@Composable
private fun BatteryGlyph(percent: Int, charging: Boolean, tone: Color) {
    Canvas(Modifier.size(width = BATTERY_WIDTH, height = BATTERY_HEIGHT)) {
        val stroke = BATTERY_STROKE.toPx()
        val nub = size.width * BATTERY_NUB_FRACTION
        val bodyWidth = size.width - nub
        val radius = CornerRadius(stroke * 1.5f, stroke * 1.5f)

        drawRoundRect(
            color = tone,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(bodyWidth - stroke, size.height - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )

        // The terminal, centred on the right-hand edge.
        drawRoundRect(
            color = tone,
            topLeft = Offset(bodyWidth, size.height * 0.3f),
            size = Size(nub, size.height * 0.4f),
            cornerRadius = CornerRadius(stroke, stroke),
        )

        // The charge itself, inset so it never touches the wall it sits in.
        val inset = stroke * 2f
        val usable = bodyWidth - stroke - inset * 2f
        val filled = usable * (percent.coerceIn(0, 100) / 100f)
        if (filled > 0f) {
            drawRoundRect(
                color = tone,
                topLeft = Offset(stroke / 2f + inset, stroke / 2f + inset),
                size = Size(filled, size.height - stroke - inset * 2f),
                cornerRadius = CornerRadius(stroke, stroke),
            )
        }

        if (charging) {
            // A bolt across the cell, drawn in the ground colour so it reads as a cut-out of
            // the charge rather than as a mark floating on top of it.
            val boltPath = Path().apply {
                moveTo(bodyWidth * 0.58f, size.height * 0.08f)
                lineTo(bodyWidth * 0.36f, size.height * 0.56f)
                lineTo(bodyWidth * 0.52f, size.height * 0.56f)
                lineTo(bodyWidth * 0.40f, size.height * 0.96f)
                lineTo(bodyWidth * 0.66f, size.height * 0.44f)
                lineTo(bodyWidth * 0.50f, size.height * 0.44f)
                close()
            }
            drawPath(boltPath, color = tone, style = Stroke(width = stroke))
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
    status: ControlMessage.Status?,
    soundAnchor: androidx.compose.ui.focus.FocusRequester,
    onCameraControls: (() -> Unit)?,
    onRotate: () -> Unit,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onNudge: (Float, Float) -> Unit,
    name: String,
    address: String,
    battery: BatteryState,
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
            // Beside the heading rather than beside the address. The address answers "which
            // device", read once; the battery answers "how long has it got", which changes
            // all night — and this is the card a parent is already watching.
            trailing = { BatteryLabel(battery) },
        ) {
            // Both readings, against the lines that would alert on them — the same picture
            // the settings screen draws, in the place a parent is actually watching.
            //
            // The sound level comes from this device's own audio when the sound is on, and
            // from the camera's report otherwise; the movement level always comes from the
            // camera, which is the only device analysing frames. Both fall back to the
            // camera's status, which arrives every couple of seconds whether or not anybody
            // is listening.
            val soundReading = if (audioOn && audioAvailable) {
                soundLevelPercent(level)
            } else {
                status?.soundLevel?.takeIf { it >= 0f }?.let(::soundLevelPercent)
            }
            val motionReading = status?.motionLevel?.takeIf { it >= 0f }?.let(::motionLevelPercent)

            // The fourteen-bar history is gone from the rail, replaced by these two marks.
            //
            // Keeping both made the card noticeably taller, and the bars were the half that
            // could be spared: they show one sensor over time, where the marks show *both*
            // against the lines that would actually alert. The settings screen still has the
            // bars, which is where a parent is choosing a threshold rather than watching a
            // room.
            RailLevel(
                label = "Sound",
                reading = soundReading,
                threshold = status?.let { thresholdForSensitivity(it.soundSensitivity) },
                absent = if (audioAvailable) {
                    "Not reported by this camera"
                } else {
                    MISSING_MIC
                },
            )
            Spacer(Modifier.height(Space.xs))
            RailLevel(
                label = "Movement",
                reading = motionReading,
                threshold = status?.let { thresholdForSensitivity(it.motionSensitivity) },
                absent = "Not reported by this camera",
            )
        }

        Spacer(Modifier.height(RAIL_GAP))

        val lemon = BmpTheme.tints.lemon
        // The card takes whatever height is left and no more.
        //
        // It used to be a fixed 168dp inside a scrolling column, which put its rounded bottom
        // edge underneath the scroll viewport's edge — the card looked cut off rather than
        // scrolled. Giving it the remaining space instead means every card is whole, the
        // controls below it never move, and the only thing that scrolls is the list of
        // alerts, which is the only thing that grows.
        RailCard(
            title = "Earlier today",
            fill = lemon.fill,
            border = lemon.border,
            labelColor = lemon.glyph,
            modifier = Modifier.weight(1f),
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
                // Oldest at the top, newest at the bottom, and the card follows the bottom.
                //
                // It read newest-first before, which is defensible on paper and wrong in the
                // hand: an event list is read the way a conversation is, and "the last one" to
                // anyone looking at it means the one at the end. Chronological order also
                // makes a run of alerts legible as a run — three in five minutes reads as a
                // baby waking up, which is not obvious when they are stacked upwards.
                //
                // Keyed on maxValue as well as size, because the new row has not been laid out
                // when the size changes and the scroll extent is still the old one. This runs
                // again the moment layout catches up.
                val historyScroll = rememberScrollState()
                LaunchedEffect(history.size, historyScroll.maxValue) {
                    if (history.isNotEmpty()) historyScroll.animateScrollTo(historyScroll.maxValue)
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(historyScroll),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    // Reversed for display rather than stored the other way round: the alert
                    // banner and the live session both want the newest first, and one list
                    // ordering has to be the canonical one.
                    for (entry in history.asReversed()) {
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

        Spacer(Modifier.height(Space.xs))

        // Zoom and pan on one row: out, the four directions, in.
        //
        // The factor used to be printed in the middle and it was not worth the width — a
        // parent can see how far in they are by looking at the picture, which is the thing
        // they are already looking at. Six squares across the rail instead.
        //
        // The arrows are always present, dimmed at 1x rather than hidden. A control that
        // appears and disappears under the remote is how focus gets lost, and the rail has
        // been bitten by that before; a dim arrow also says "there is panning here, once you
        // zoom in", which an absent one cannot.
        Row(
            Modifier.fillMaxWidth().padding(bottom = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canPan = zoom > 1f
            RailIconButton(
                icon = BmpIcons.ZoomOut,
                description = "Zoom out",
                enabled = zoom > 1f,
                modifier = Modifier.weight(1f).height(RAIL_BUTTON_HEIGHT),
                onClick = { onZoomChange((zoom - ZOOM_STEP).coerceAtLeast(1f)) },
            )
            RailIconButton(
                icon = BmpIcons.ArrowLeft,
                description = "Move the picture left",
                enabled = canPan,
                modifier = Modifier.weight(1f).height(RAIL_BUTTON_HEIGHT),
                onClick = { onNudge(1f, 0f) },
            )
            RailIconButton(
                icon = BmpIcons.ArrowUp,
                description = "Move the picture up",
                enabled = canPan,
                modifier = Modifier.weight(1f).height(RAIL_BUTTON_HEIGHT),
                onClick = { onNudge(0f, 1f) },
            )
            RailIconButton(
                icon = BmpIcons.ArrowDown,
                description = "Move the picture down",
                enabled = canPan,
                modifier = Modifier.weight(1f).height(RAIL_BUTTON_HEIGHT),
                onClick = { onNudge(0f, -1f) },
            )
            RailIconButton(
                icon = BmpIcons.ArrowRight,
                description = "Move the picture right",
                enabled = canPan,
                modifier = Modifier.weight(1f).height(RAIL_BUTTON_HEIGHT),
                onClick = { onNudge(-1f, 0f) },
            )
            RailIconButton(
                icon = BmpIcons.ZoomIn,
                description = "Zoom in",
                enabled = zoom < MAX_ZOOM,
                modifier = Modifier.weight(1f).height(RAIL_BUTTON_HEIGHT),
                onClick = { onZoomChange((zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM)) },
            )
        }

        // One row: turn the picture, the camera's own settings, sound, and out.
        //
        // Sound takes the width the other three do not, because it is the only one whose
        // label has to say which way it is set — "Sound on" and "Sound off" are different
        // facts, where the other three are the same control whatever the state. Close is a
        // cross rather than the word for the same reason it is a cross everywhere else: at
        // this size the word is three buttons wide.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RailIconButton(
                icon = BmpIcons.Rotate,
                description = "Turn the picture a quarter turn",
                onClick = onRotate,
            )
            if (onCameraControls != null) {
                RailIconButton(
                    icon = BmpIcons.Camera,
                    description = "Camera settings",
                    onClick = onCameraControls,
                )
            }
            if (audioSupported) {
                RailSoundButton(
                    on = audioOn,
                    available = audioAvailable,
                    onClick = onToggleAudio,
                    // Where the remote lands when the live view opens. Sound is the control a
                    // parent reaches for on this screen, and the way out sits beside it.
                    modifier = Modifier.weight(1f).focusAnchor(soundAnchor),
                )
            }
            RailIconButton(
                icon = BmpIcons.Close,
                description = "Close the live view",
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun RailCard(
    title: String,
    fill: Color,
    border: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    /** Drawn at the right of the card's heading row — a reading that belongs with the title. */
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = fill,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, border),
    ) {
        Column(
            Modifier.padding(
                horizontal = RAIL_CARD_H,
                vertical = if (isTelevision) RAIL_CARD_V_TV else RAIL_CARD_V,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = RAIL_LABEL,
                        letterSpacing = RAIL_TRACKING,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = labelColor,
                )
                // The card's own heading row is the one piece of horizontal space in the rail
                // that is always present and never full — the right place for a reading that
                // has to be visible without competing with anything.
                trailing?.invoke()
            }
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
                // One line. Sharing the row with three other controls left it a column two
                // words wide, so it wrapped to "Sound / off" — which reads as a two-line
                // heading rather than as the state of a switch.
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * A square control in the rail's action row — rotate, or the camera's own settings.
 *
 * Square rather than labelled, because the row holds Sound, these two and Close, and on a
 * 352dp rail four words do not fit. Each still takes the full focus ring, so the remote can
 * see it coming.
 */
@Composable
private fun RailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    // Square by default. Losing the width made an unweighted one expand to the whole row and
    // push Sound, the camera panel and Close off the rail entirely.
    modifier: Modifier = Modifier.size(RAIL_BUTTON_HEIGHT),
) {
    val shape = RoundedCornerShape(RAIL_BUTTON_RADIUS)
    Surface(
        modifier = modifier
            .clip(shape)
            .pressable(onClick = onClick, enabled = enabled, focusShape = shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                // A control at the end of its range is dimmed rather than removed: a button
                // that vanishes takes the remote's focus with it.
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(SOUND_ICON),
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

/** The same meter, trimmed for a rail that has more to fit on it. */
private val RAIL_METER_TV = 24.dp

/** Card padding on a television, where the rail's vertical budget is tightest. */
private val RAIL_CARD_V_TV = 11.dp
/**
 * Four rows' worth. Enough to see that a quiet night was quiet and a bad one was not, without
 * the card taking a third of the rail.
 */
private val RAIL_STRIP_HEIGHT = 168.dp

private val RAIL_STRIP_DETAIL = 11.sp

/** Wide enough for "Movement", so the two marks line up under each other. */
private val RAIL_LEVEL_LABEL = 72.dp
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

/** Square enough to be a comfortable thumb target without taking a word's width. */
private val BAR_ICON_BUTTON = 44.dp

/** A cell a little wider than it is tall, at the size a line of metadata can carry. */
private val BATTERY_WIDTH = 20.dp
private val BATTERY_HEIGHT = 11.dp
private val BATTERY_STROKE = 1.4.dp
private const val BATTERY_NUB_FRACTION = 0.1f

private const val QUARTER_TURN = 90
private const val FULL_TURN = 360

/** Mark, identity, then the controls wrap to the next line. */
private const val STACKED_BAR_ITEMS = 3

/** Keeps a long camera name from pushing the controls off a phone's second line. */
private val STACKED_NAME_WIDTH = 210.dp

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
/** Where JPEG blocks take over from the baby. */
private const val MAX_ZOOM = 4f

/** A press is half a turn of a pinch, so four presses cross the whole range. */
private const val ZOOM_STEP = 0.5f

/** A fifth of the window per press: enough to be worth pressing, small enough to aim with. */
private const val PAN_STEP = 0.2f

private const val STALE_FRAME_ALPHA = 0.3f
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
