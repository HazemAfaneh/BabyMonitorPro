package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.core.supportsCameraRole
import com.hazemafaneh.babymonitorpro.core.supportsNotifications
import com.hazemafaneh.babymonitorpro.detect.MotionDetector
import com.hazemafaneh.babymonitorpro.di.BroadcasterHolder
import com.hazemafaneh.babymonitorpro.server.BroadcastState
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.store.FRAME_RATES
import com.hazemafaneh.babymonitorpro.store.ListenOnAlert
import com.hazemafaneh.babymonitorpro.store.VIDEO_HEIGHTS
import com.hazemafaneh.babymonitorpro.ui.components.CARD_BORDER
import com.hazemafaneh.babymonitorpro.ui.components.IconPlate
import com.hazemafaneh.babymonitorpro.ui.components.MoonButton
import com.hazemafaneh.babymonitorpro.ui.components.PlateSize
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.components.SoundMeter
import com.hazemafaneh.babymonitorpro.ui.components.StopBroadcastingButton
import com.hazemafaneh.babymonitorpro.ui.components.MoveFocusWhen
import com.hazemafaneh.babymonitorpro.ui.components.focusAnchor
import com.hazemafaneh.babymonitorpro.ui.components.focusRing
import com.hazemafaneh.babymonitorpro.ui.components.initialFocus
import com.hazemafaneh.babymonitorpro.ui.components.rememberFocusAnchor
import com.hazemafaneh.babymonitorpro.ui.components.pressable
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.layout.tvOverscan
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * Every setting in the app, on one screen, whichever end of the monitor this device is.
 *
 * It used to be reachable only from the camera screen and to hold only the camera's own
 * controls, which left the settings a *watching* device decides with nowhere to live at all —
 * a viewer-only device never opens the camera screen, so it never saw a settings screen.
 *
 * So the sections are the two roles rather than the current one. Both are always shown, even
 * on a device doing neither right now. A tablet in the kitchen is configured on Sunday
 * afternoon and used at 2am, and a settings screen whose contents depend on what happens to
 * be running is a settings screen a parent cannot prepare with.
 *
 * [onBack] is null when this is a tab on the home screen, where there is nothing to go back
 * to; the camera screen passes one, because there it is a place you arrive at and leave.
 */
@Composable
fun SettingsScreen(
    night: Boolean,
    onNightChanged: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = koinInject<AppSettings>()
    val broadcaster = koinInject<BroadcasterHolder>().broadcaster
    val window = rememberWindowClass()
    val tints = BmpTheme.tints

    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var motion by remember { mutableStateOf(settings.motionSensitivity.toFloat()) }
    var sound by remember { mutableStateOf(settings.soundSensitivity.toFloat()) }
    var renaming by remember { mutableStateOf(false) }
    var videoHeight by remember { mutableStateOf(settings.videoHeight) }
    var frameRate by remember { mutableStateOf(settings.frameRate) }
    var port by remember { mutableStateOf(settings.port) }
    var editingPort by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    // Read once into state and written straight through on every change. They are switches,
    // not a form: there is no Save button, and a setting that needed one would be a setting a
    // parent could lose by backing out of the screen.
    var startWithSound by remember { mutableStateOf(settings.startWithSound) }
    var listenOnAlert by remember { mutableStateOf(settings.listenOnAlert) }
    var motionAlerts by remember { mutableStateOf(settings.motionAlerts) }
    var soundAlerts by remember { mutableStateOf(settings.soundAlerts) }
    var keepAwake by remember { mutableStateOf(settings.keepScreenAwake) }
    var rememberedHost by remember { mutableStateOf(settings.lastManualHost) }

    val stateFlow = remember(broadcaster) {
        broadcaster?.state ?: MutableStateFlow(BroadcastState(running = false))
    }
    val state by stateFlow.collectAsState()

    val levelFlow = remember(broadcaster) { broadcaster?.soundLevel ?: MutableStateFlow(0f) }
    val level by levelFlow.collectAsState()

    // Both detectors are told at once, because the broadcaster takes them together — and
    // whichever one the parent just moved, the other has to arrive at its current value
    // rather than at the one it had when the screen opened.
    val pushSensitivity: () -> Unit = {
        settings.motionSensitivity = motion.toInt()
        settings.soundSensitivity = sound.toInt()
        broadcaster?.setSensitivity(motion.toInt(), sound.toInt())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = window.gutter())
            .padding(vertical = tvOverscan()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.xxs, bottom = Space.sm)
                .heightIn(min = Touch.min),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_TEXT),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonButton(night = night, onNightChanged = onNightChanged)
                if (onBack != null) {
                    RowAction(label = "Done", onClick = onBack)
                }
            }
        }

        if (supportsCameraRole) {
            SettingsSectionLabel("When this device is the camera")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = ROWS_H_PADDING, vertical = ROWS_V_PADDING)) {
                    SettingRow(
                        icon = BmpIcons.Camera,
                        plateFill = tints.sky.fill,
                        plateGlyph = tints.sky.glyph,
                        label = if (settings.useFrontCamera) "Front camera" else "Back camera",
                        // Two different silences. A camera that is not running has not failed
                        // — it is simply not this device's job right now — and saying
                        // "sending video" on a device sitting in a drawer would be a lie.
                        value = when {
                            !state.running -> "The lens this device starts on"
                            state.syntheticVideo -> "Test pattern"
                            else -> "Sending video to viewers"
                        },
                        divided = true,
                        modifier = Modifier.initialFocus(),
                    ) {
                        RowAction(
                            label = if (settings.useFrontCamera) "Use rear" else "Use front",
                            onClick = {
                                // Remembered as well as applied. The switch used to live only
                                // in the running broadcaster, so a parent who set it every
                                // night was answering the same question every night.
                                settings.useFrontCamera = !settings.useFrontCamera
                                deviceName = deviceName // nudge recomposition of the label
                                if (state.running) broadcaster?.switchCamera()
                            },
                        )
                    }

                    // Only while there is a camera bound to turn it on. A torch row on a
                    // device that is not broadcasting is a switch wired to nothing.
                    if (state.running && !state.syntheticVideo) {
                        SwitchRow(
                            icon = BmpIcons.Moon,
                            plateFill = tints.lemon.fill,
                            plateGlyph = tints.lemon.glyph,
                            label = "Torch",
                            value = if (torchOn) {
                                "On — the nursery light is coming from this phone"
                            } else {
                                "Off"
                            },
                            checked = torchOn,
                            divided = true,
                            onCheckedChange = {
                                torchOn = it
                                broadcaster?.setTorch(it)
                            },
                        )
                    }

                    SettingRow(
                        icon = BmpIcons.Rattle,
                        plateFill = tints.lemon.fill,
                        plateGlyph = tints.lemon.glyph,
                        label = "Microphone",
                        value = when {
                            !state.running -> "Used while broadcasting"
                            state.audioActive -> "On · sending sound to viewers"
                            else -> "No microphone on this device"
                        },
                        divided = true,
                    )

                    SettingRow(
                        icon = BmpIcons.Teddy,
                        plateFill = tints.lemon.fill,
                        plateGlyph = tints.lemon.glyph,
                        label = deviceName,
                        value = "The name viewers see",
                        divided = true,
                    ) {
                        RowAction(label = if (renaming) "Done" else "Rename") { renaming = !renaming }
                    }

                    if (renaming) {
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = {
                                deviceName = it
                                settings.deviceName = it
                            },
                            label = { Text("Device name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                        )
                    }

                    SettingRow(
                        icon = BmpIcons.House,
                        plateFill = tints.sky.fill,
                        plateGlyph = tints.sky.glyph,
                        label = "Port $port",
                        value = if (state.running) {
                            "Takes effect the next time broadcasting starts"
                        } else {
                            "Change it when something else holds 8080"
                        },
                        divided = false,
                    ) {
                        RowAction(label = if (editingPort) "Done" else "Change") {
                            editingPort = !editingPort
                        }
                    }

                    if (editingPort) {
                        OutlinedTextField(
                            value = port.toString(),
                            onValueChange = { text ->
                                val parsed = text.filter { it.isDigit() }.take(5).toIntOrNull()
                                if (parsed != null) {
                                    port = parsed
                                    // Only a usable port is stored. Below 1024 needs root on
                                    // every platform this runs on, and the setter clamps —
                                    // so writing an in-progress "8" would silently become
                                    // 1024 and stay there.
                                    if (parsed in 1024..65535) settings.port = parsed
                                }
                            },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = {
                                Text(
                                    if (port in 1024..65535) {
                                        "Viewers will need this number with the address."
                                    } else {
                                        "Pick a number between 1024 and 65535."
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))

            SectionCard(title = "Picture quality", icon = BmpIcons.Camera) {
                Text(
                    text = "Lower is less WiFi traffic and a cooler phone in the nursery. A " +
                        "television upscales whatever it is sent.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = HELPER_TEXT,
                        lineHeight = HELPER_LINE,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                ChoiceRow(
                    options = VIDEO_HEIGHTS.map { it.label to it.height },
                    selected = videoHeight,
                    onSelect = {
                        videoHeight = it
                        settings.videoHeight = it
                    },
                )
                Spacer(Modifier.height(Space.sm))
                ChoiceRow(
                    options = FRAME_RATES.map { "$it fps" to it },
                    selected = frameRate,
                    onSelect = {
                        frameRate = it
                        settings.frameRate = it
                    },
                )
                if (state.running) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        // Said plainly rather than restarting the stream underneath a parent
                        // who is mid-pairing. Capture geometry is fixed when the camera binds.
                        text = "Takes effect the next time broadcasting starts.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = HELPER_TEXT),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Space.sm))

            // Two sliders, not one. They were a single control writing the same number to
            // both detectors, so a nursery on a noisy street could not have calm sound and
            // alert movement — and the two thresholds do not even mean the same thing: one
            // is a fraction of the frame, the other is loudness.
            SectionCard(title = "Movement alerts", icon = BmpIcons.Teddy) {
                SensitivityHeader(
                    label = "Alert me at",
                    value = motion.toInt(),
                    tint = tints.sky.glyph,
                )
                SensitivitySlider(
                    value = motion,
                    onValueChange = { motion = it },
                    onCommit = pushSensitivity,
                )
                Text(
                    text = if (motion.toInt() == 0) {
                        "No movement alerts. The picture keeps running."
                    } else {
                        "Fires when about ${motionPercent(motion.toInt())} of the picture " +
                            "changes between frames."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = HELPER_TEXT,
                        lineHeight = HELPER_LINE,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Space.sm))

            SectionCard(title = "Sound alerts", icon = BmpIcons.Rattle) {
                // The meter belongs with the sound slider and nowhere else: it is the
                // evidence for *this* threshold.
                if (state.running && state.audioActive) {
                    SoundMeter(level = level, sensitivity = sound.toInt())
                    Spacer(Modifier.height(Space.sm))
                } else {
                    Text(
                        text = "The live meter runs here while this device is broadcasting. " +
                            "The setting applies either way.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = HELPER_TEXT,
                            lineHeight = HELPER_LINE,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.sm))
                }
                SensitivityHeader(
                    label = "Alert me at",
                    value = sound.toInt(),
                    tint = tints.lemon.glyph,
                )
                SensitivitySlider(
                    value = sound,
                    onValueChange = { sound = it },
                    onCommit = pushSensitivity,
                )
                Text(
                    text = if (sound.toInt() == 0) {
                        "No sound alerts. Listening still works."
                    } else {
                        "Drag right and quieter sounds will reach you. The bars above are " +
                            "live, so you can set it against the actual room."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = HELPER_TEXT,
                        lineHeight = HELPER_LINE,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Space.md))
        }

        SettingsSectionLabel("When this device is watching")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = ROWS_H_PADDING, vertical = ROWS_V_PADDING)) {
                SwitchRow(
                    icon = BmpIcons.Rattle,
                    plateFill = tints.lemon.fill,
                    plateGlyph = tints.lemon.glyph,
                    label = "Start with sound on",
                    value = if (startWithSound) {
                        "Opening a camera plays the room straight away"
                    } else {
                        "Opening a camera starts muted"
                    },
                    checked = startWithSound,
                    divided = true,
                    onCheckedChange = {
                        startWithSound = it
                        settings.startWithSound = it
                    },
                    modifier = if (supportsCameraRole) Modifier else Modifier.initialFocus(),
                )

                // Three states, not two: whether an alert turns the sound on, and whether it
                // turns itself off again afterwards, are one decision a parent makes once.
                SettingRow(
                    icon = BmpIcons.Rattle,
                    plateFill = tints.lemon.fill,
                    plateGlyph = tints.lemon.glyph,
                    label = "Listen when alerted",
                    value = listenOnAlert.description,
                    divided = false,
                )
                Row(Modifier.padding(start = LISTEN_CHOICE_INSET, bottom = Space.sm)) {
                    ChoiceRow(
                        options = ListenOnAlert.entries.map { it.label to it },
                        selected = listenOnAlert,
                        onSelect = {
                            listenOnAlert = it
                            settings.listenOnAlert = it
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (supportsNotifications) {
                    SwitchRow(
                        icon = BmpIcons.Teddy,
                        plateFill = tints.sky.fill,
                        plateGlyph = tints.sky.glyph,
                        label = "Movement notifications",
                        value = if (motionAlerts) {
                            "Movement reaches you outside the app"
                        } else {
                            "Movement shows in the app only"
                        },
                        checked = motionAlerts,
                        divided = true,
                        onCheckedChange = {
                            motionAlerts = it
                            settings.motionAlerts = it
                        },
                    )

                    SwitchRow(
                        icon = BmpIcons.Rattle,
                        plateFill = tints.sky.fill,
                        plateGlyph = tints.sky.glyph,
                        label = "Sound notifications",
                        value = if (soundAlerts) {
                            "Sound reaches you outside the app"
                        } else {
                            "Sound shows in the app only"
                        },
                        checked = soundAlerts,
                        divided = true,
                        onCheckedChange = {
                            soundAlerts = it
                            settings.soundAlerts = it
                        },
                    )
                }

                SwitchRow(
                    icon = BmpIcons.Moon,
                    plateFill = tints.grape.fill,
                    plateGlyph = tints.grape.glyph,
                    label = "Keep the screen on",
                    value = if (keepAwake) {
                        "The live view never dims while it is open"
                    } else {
                        "The screen sleeps as usual"
                    },
                    checked = keepAwake,
                    divided = true,
                    onCheckedChange = {
                        keepAwake = it
                        settings.keepScreenAwake = it
                    },
                )

                SettingRow(
                    icon = BmpIcons.House,
                    plateFill = tints.leaf.fill,
                    plateGlyph = tints.leaf.glyph,
                    label = rememberedHost.ifBlank { "No remembered camera" },
                    value = if (rememberedHost.isBlank()) {
                        "The last address you typed appears here"
                    } else {
                        "Filled in for you on the Find a camera screen"
                    },
                    divided = false,
                ) {
                    if (rememberedHost.isNotBlank()) {
                        RowAction(label = "Forget") {
                            rememberedHost = ""
                            settings.lastManualHost = ""
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.sm))

        PrivacyCard(address = state.primaryAddress)

        Spacer(Modifier.height(Space.sm))

        ResetCard(
            onReset = {
                settings.reset()
                // Everything on screen comes back from the store, so the screen agrees with
                // the device immediately rather than after a trip out and back.
                deviceName = settings.deviceName
                motion = settings.motionSensitivity.toFloat()
                sound = settings.soundSensitivity.toFloat()
                videoHeight = settings.videoHeight
                frameRate = settings.frameRate
                port = settings.port
                startWithSound = settings.startWithSound
                listenOnAlert = settings.listenOnAlert
                motionAlerts = settings.motionAlerts
                soundAlerts = settings.soundAlerts
                keepAwake = settings.keepScreenAwake
                rememberedHost = settings.lastManualHost
                onNightChanged(settings.nightMode)
                broadcaster?.setSensitivity(motion.toInt(), sound.toInt())
            },
        )

        // Only while there is something to stop. On a viewer-only device this button used to
        // be a dead control for a server that was never running.
        if (state.running) {
            Spacer(Modifier.height(Space.md))
            StopBroadcastingButton(broadcaster, onStop)
        }

        Spacer(Modifier.height(Space.xl))
    }
}

/** The one-line heading above a group of cards. Sets the two roles apart without a divider. */
@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = SECTION_LABEL),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xxs, bottom = Space.xs),
    )
}

/** The named level and the number, above a sensitivity slider. */
@Composable
private fun SensitivityHeader(label: String, value: Int, tint: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            // A named level, not a bare number: 62 tells a parent nothing about whether a
            // passing car will wake them.
            text = buildAnnotatedString {
                append("$label ")
                withStyle(SpanStyle(color = tint)) { append(sensitivityLabel(value)) }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = SETTING_LABEL),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$value%",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SensitivitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onCommit,
        // Ten stops rather than a continuous sweep. The detector cannot tell 61 from 62, so
        // offering that precision only invites fiddling with it — and on a remote, ten
        // left-presses is a settable range where a hundred is not.
        steps = SENSITIVITY_STEPS,
        valueRange = 0f..100f,
        interactionSource = interaction,
        modifier = Modifier.focusRing(interaction, MaterialTheme.shapes.large),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            // The stops exist to quantise the value, not to be counted. Ten dots printed
            // along the track invited reading it as a scale with meanings at each mark,
            // which it does not have.
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/**
 * A small set of mutually exclusive values, as pills.
 *
 * Pills rather than a dropdown: three options do not justify hiding two of them behind a
 * menu, and a menu on a television is a focus trap — the remote has to open it, walk it, and
 * find its way back out.
 */
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        for ((label, value) in options) {
            val isSelected = value == selected
            Surface(
                modifier = Modifier
                    .heightIn(min = Touch.min)
                    .pressable(
                        onClick = { onSelect(value) },
                        role = SemanticsRole.RadioButton,
                        focusShape = MaterialTheme.shapes.extraLarge,
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (isSelected) scheme.secondaryContainer else scheme.surface,
                border = BorderStroke(
                    CARD_BORDER,
                    if (isSelected) scheme.secondary else scheme.outlineVariant,
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            scheme.onSecondaryContainer
                        } else {
                            scheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** One fact about this device, and whatever can be done about it. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    plateFill: Color,
    plateGlyph: Color,
    label: String,
    value: String,
    divided: Boolean,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = ROW_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = icon,
                fill = plateFill,
                contentColor = plateGlyph,
                size = PlateSize.small,
            )
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = SETTING_LABEL_LG),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = SETTING_VALUE),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action()
        }
        // Hairlines between rows, not around them. A card per row would turn six facts into
        // six objects.
        if (divided) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * A setting with two states.
 *
 * The whole row is the target, not just the switch. A 32dp switch is a fine thumb target and
 * an impossible one from a sofa: the remote focuses the row, and the middle button toggles
 * it — which is also the behaviour a phone user gets for free.
 */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    plateFill: Color,
    plateGlyph: Color,
    label: String,
    value: String,
    checked: Boolean,
    divided: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.pressable(
            onClick = { onCheckedChange(!checked) },
            wide = true,
            role = SemanticsRole.Switch,
            focusShape = MaterialTheme.shapes.medium,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(vertical = ROW_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = icon,
                fill = plateFill,
                contentColor = plateGlyph,
                size = PlateSize.small,
            )
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = SETTING_LABEL_LG),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = SETTING_VALUE),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Space.xs))
            // Null handler: the row above owns the click, and a switch that also handled it
            // would toggle twice on a tap that landed on the switch itself.
            Switch(checked = checked, onCheckedChange = null)
        }
        if (divided) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RowAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = Touch.min)
            .focusRing(interaction, MaterialTheme.shapes.medium),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The privacy promise, as a card rather than a line.
 *
 * It gets a card here and a line everywhere else because this is the screen a parent opens
 * when they are wondering what the app is doing, rather than the screen they glance at to
 * check the baby. The leaf tint is the one the shield carries everywhere; it is decoration,
 * not a state, and the address inside it is the part that can actually be checked.
 */
@Composable
private fun PrivacyCard(address: String?) {
    val leaf = BmpTheme.tints.leaf
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = leaf.fill,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, leaf.border),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = PRIVACY_V_PADDING),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = BmpIcons.Shield,
                contentDescription = null,
                tint = leaf.glyph,
                modifier = Modifier.size(PRIVACY_ICON).padding(top = 1.dp),
            )
            Spacer(Modifier.width(Space.xs))
            Column(Modifier.widthIn(max = PRIVACY_MAX_WIDTH)) {
                Text(
                    text = if (address == null) {
                        "On your WiFi only"
                    } else {
                        "On your WiFi · $address"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = SETTING_LABEL),
                    fontWeight = FontWeight.Bold,
                    color = leaf.content,
                )
                Text(
                    text = "Nothing is recorded and nothing is uploaded. Close the app and " +
                        "the stream stops.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = SETTING_VALUE,
                        lineHeight = PRIVACY_LINE,
                    ),
                    color = leaf.contentMuted,
                )
            }
        }
    }
}

/**
 * Back to defaults, behind a confirmation.
 *
 * Two presses rather than one, because this is the only control on the screen that undoes
 * work a parent did — and on a remote, where focus walks past every row on its way down, a
 * single-press reset is a row you can destroy your settings with by leaning on the D-pad.
 */
@Composable
private fun ResetCard(onReset: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val tints = BmpTheme.tints
    // Not requested on appear: this screen's focus starts at the top, not on a reset button.
    val cancelAnchor = rememberFocusAnchor(requestOnAppear = false)
    MoveFocusWhen(trigger = confirming, requester = cancelAnchor)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = ROWS_H_PADDING, vertical = ROWS_V_PADDING)) {
            SettingRow(
                icon = BmpIcons.Sliders,
                plateFill = tints.grape.fill,
                plateGlyph = tints.grape.glyph,
                label = "Reset all settings",
                value = if (confirming) {
                    "This cannot be undone"
                } else {
                    "Everything on this screen, back to how it shipped"
                },
                divided = false,
            ) {
                if (confirming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Cancel takes the focus the "Reset" row gave up when it was replaced
                        // — and it is the safe half of the pair, so a remote that arrives here
                        // by accident is one press from backing out rather than one press
                        // from wiping the settings.
                        RowAction(
                            label = "Cancel",
                            modifier = Modifier.focusAnchor(cancelAnchor),
                            onClick = { confirming = false },
                        )
                        ResetConfirmAction {
                            confirming = false
                            onReset()
                        }
                    }
                } else {
                    RowAction(label = "Reset") { confirming = true }
                }
            }
        }
    }
}

/** The one destructive word on the screen, in the error colour and nowhere else. */
@Composable
private fun ResetConfirmAction(onConfirm: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onConfirm,
        interactionSource = interaction,
        modifier = Modifier
            .heightIn(min = Touch.min)
            .focusRing(interaction, MaterialTheme.shapes.medium),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(
            text = "Reset",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
            fontWeight = FontWeight.Bold,
        )
    }
}

private val TITLE_TEXT = 21.sp
private val ACTION_TEXT = 13.sp
private val SECTION_LABEL = 12.sp
private val SETTING_LABEL = 13.sp
private val SETTING_LABEL_LG = 15.sp
private val SETTING_VALUE = 11.5.sp
private val HELPER_TEXT = 12.sp
private val HELPER_LINE = 18.sp
private val PRIVACY_LINE = 17.sp
private val PRIVACY_ICON = 16.dp
private val PRIVACY_V_PADDING = 15.dp
private val PRIVACY_MAX_WIDTH = 640.dp
private val ROWS_H_PADDING = 18.dp
private val ROWS_V_PADDING = 6.dp
private val ROW_HEIGHT = 60.dp
private val ROW_V_PADDING = 13.dp

/** Lines the pills up under the row's text rather than under its icon plate. */
private val LISTEN_CHOICE_INSET = 48.dp

private fun sensitivityLabel(value: Int): String = when {
    value == 0 -> "Off"
    value < 34 -> "Low"
    value < 67 -> "Medium"
    else -> "High"
}

/** The motion threshold as a percentage of the frame, so the slider says what it will do. */
private fun motionPercent(sensitivity: Int): String {
    val fraction = MotionDetector.thresholdFor(sensitivity)
    return if (fraction >= 0.01f) "${(fraction * 100).toInt()}%" else "under 1%"
}

private const val SENSITIVITY_STEPS = 9
