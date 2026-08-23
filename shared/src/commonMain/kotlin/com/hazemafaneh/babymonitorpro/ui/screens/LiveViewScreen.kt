package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.audio.createAudioPlayer
import com.hazemafaneh.babymonitorpro.client.ViewerClient
import com.hazemafaneh.babymonitorpro.core.AudioConfig
import com.hazemafaneh.babymonitorpro.core.CameraEndpoint
import com.hazemafaneh.babymonitorpro.core.nowMillis
import com.hazemafaneh.babymonitorpro.notify.notifyAlert
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.ui.KeepScreenAwake
import com.hazemafaneh.babymonitorpro.ui.theme.LocalReducedMotion
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
 * The browser is the exception — see [videoRendersBehindUi] below.
 */
@Composable
fun LiveViewScreen(
    endpoint: CameraEndpoint,
    pin: String?,
    onBack: () -> Unit,
) {
    var status by remember { mutableStateOf(VideoStatus.CONNECTING) }
    var cameraStatus by remember { mutableStateOf<ControlMessage.Status?>(null) }
    var latencyMillis by remember { mutableStateOf<Long?>(null) }
    var alert by remember { mutableStateOf<Alert?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var audioOn by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(nowMillis()) }

    val reducedMotion = LocalReducedMotion.current
    val audioPlayer = remember(endpoint.id) { createAudioPlayer(AudioConfig()) }

    KeepScreenAwake(enabled = true)

    LaunchedEffect(lastInteraction) {
        controlsVisible = true
        delay(CONTROLS_TIMEOUT_MILLIS)
        controlsVisible = false
    }

    // Control channel: status, alerts, and a round-trip ping that doubles as the
    // connection's health check.
    LaunchedEffect(endpoint.id, pin) {
        val client = ViewerClient(endpoint, pin)
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
                                alert = Alert("Movement detected", message.atMillis)
                                notifyAlert(endpoint.name, "Movement detected")
                            }
                            is ControlMessage.SoundEvent -> {
                                alert = Alert("Sound detected", message.atMillis)
                                notifyAlert(endpoint.name, "Sound detected")
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
    LaunchedEffect(audioOn, endpoint.id, pin) {
        val player = audioPlayer
        if (!audioOn || player == null) return@LaunchedEffect
        val client = ViewerClient(endpoint, pin)
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
    val chromeAlwaysVisible = videoIsSeparateLayer || controlsVisible

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { lastInteraction = nowMillis() },
    ) {
        MjpegVideo(
            endpoint = endpoint,
            pin = pin,
            modifier = if (videoIsSeparateLayer) {
                Modifier.fillMaxSize().padding(top = 56.dp, bottom = 88.dp)
            } else {
                Modifier.fillMaxSize()
            },
            onStatus = { status = it },
            onFrame = { },
            onError = { failure = it },
        )

        AnimatedVisibility(
            visible = alert != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier
                    .safeContentPadding()
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .clip(MaterialTheme.shapes.large),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = alert?.label.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = chromeAlwaysVisible,
            enter = if (reducedMotion) fadeIn(snap()) else fadeIn(),
            exit = if (reducedMotion) fadeOut(snap()) else fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .safeContentPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.fillMaxWidth(0.75f)) {
                    StatusChip(status = status, latencyMillis = latencyMillis)
                    // A black picture with no explanation is unactionable. Once the stream
                    // is live the reason is stale, so it only shows while it is still true.
                    val reason = failure
                    if (reason != null && status != VideoStatus.LIVE) {
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                TextButton(onClick = onBack) { Text("Close") }
            }
        }

        AnimatedVisibility(
            visible = chromeAlwaysVisible,
            enter = if (reducedMotion) fadeIn(snap()) else fadeIn(),
            exit = if (reducedMotion) fadeOut(snap()) else fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .safeContentPadding()
                    .padding(16.dp)
                    .then(if (videoIsSeparateLayer) Modifier.fillMaxWidth() else Modifier)
                    .clip(MaterialTheme.shapes.extraLarge),
                color = MaterialTheme.colorScheme.surface.copy(
                    alpha = if (videoIsSeparateLayer) 1f else 0.85f,
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.fillMaxWidth(if (audioPlayer == null) 1f else 0.6f)) {
                        Text(
                            text = cameraStatus?.deviceName ?: endpoint.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = endpoint.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (audioPlayer != null) {
                        Spacer(Modifier.size(12.dp))
                        TextButton(
                            onClick = {
                                audioOn = !audioOn
                                lastInteraction = nowMillis()
                            },
                            enabled = cameraStatus?.audioAvailable != false,
                        ) {
                            Text(if (audioOn) "Sound on" else "Sound off")
                        }
                    }
                }
            }
        }
    }
}

private data class Alert(val label: String, val atMillis: Long)

@Composable
private fun StatusChip(
    status: VideoStatus,
    latencyMillis: Long?,
) {
    val label = when (status) {
        VideoStatus.CONNECTING -> "Connecting…"
        VideoStatus.LIVE -> "Live"
        VideoStatus.RECONNECTING -> "Reconnecting…"
        VideoStatus.UNAUTHORIZED -> "PIN required"
        VideoStatus.FAILED -> "No connection"
    }
    val dot = when (status) {
        VideoStatus.LIVE -> MaterialTheme.colorScheme.primary
        VideoStatus.UNAUTHORIZED, VideoStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (status == VideoStatus.LIVE && latencyMillis != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "${latencyMillis.coerceAtLeast(0)} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val CONTROLS_TIMEOUT_MILLIS = 4000L
private const val PING_INTERVAL_MILLIS = 5000L
private const val RECONNECT_DELAY_MILLIS = 2000L
private const val ALERT_VISIBLE_MILLIS = 6000L
