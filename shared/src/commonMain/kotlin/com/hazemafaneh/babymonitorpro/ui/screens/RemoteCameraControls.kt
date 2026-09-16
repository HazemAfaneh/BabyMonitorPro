package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.protocol.ControlMessage
import com.hazemafaneh.babymonitorpro.store.FRAME_RATES
import com.hazemafaneh.babymonitorpro.store.VIDEO_HEIGHTS
import com.hazemafaneh.babymonitorpro.ui.components.CARD_BORDER
import com.hazemafaneh.babymonitorpro.ui.components.focusRing
import com.hazemafaneh.babymonitorpro.ui.components.initialFocus
import com.hazemafaneh.babymonitorpro.ui.components.MotionLevelMarker
import com.hazemafaneh.babymonitorpro.ui.components.SoundLevelMarker
import com.hazemafaneh.babymonitorpro.ui.components.motionLevelPercent
import com.hazemafaneh.babymonitorpro.ui.components.sensitivityForThreshold
import com.hazemafaneh.babymonitorpro.ui.components.soundLevelPercent
import com.hazemafaneh.babymonitorpro.ui.components.thresholdForSensitivity
import com.hazemafaneh.babymonitorpro.ui.components.pressable
import com.hazemafaneh.babymonitorpro.ui.components.skipDpadFocus
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch

/**
 * The nursery device's settings, changed from wherever the parent is standing.
 *
 * The case this exists for is the ordinary one: the camera phone is propped against the cot
 * in a dark room with a sleeping baby in front of it, and walking in to tap its screen is the
 * one thing nobody wants to do. Everything here is sent over the control channel the viewer
 * already holds open, and every change comes back to *all* viewers as a fresh status — two
 * parents watching the same cot must not disagree about which way the lens is pointing.
 *
 * Reads from [status] rather than from local state, so the panel shows what the camera says
 * is true rather than what this device last asked for. A request that the camera refuses —
 * a lens it does not have, a torch on a device without one — simply never changes the
 * reading, which is the honest outcome.
 *
 * The three at the bottom restart capture, so they are grouped and labelled as such: a parent
 * who drops the picture size to save mobile data should know the picture will blink first.
 */
@Composable
fun RemoteCameraControls(
    status: ControlMessage.Status,
    /**
     * What this device is hearing, 0..1, or null when its sound is off.
     *
     * A fallback only. The camera reports its own readings in [status], which is the right
     * source — it is the device doing the detecting — and works whether or not this viewer
     * happens to be playing the audio. This is used when an older camera sends no levels.
     */
    roomLevel: Float?,
    onDismiss: () -> Unit,
    onChange: (ControlMessage.SetCameraSettings) -> Unit,
) {
    // The sliders are the one thing held locally, because a slider that snaps back to the
    // camera's value while a thumb is still on it is unusable over a network.
    // Shown as levels, exactly as on the camera's own settings screen: bigger means the room
    // has to be louder, or moving more, before an alert fires. The camera speaks sensitivity,
    // which runs the other way, so the flip happens at this boundary and nowhere else.
    var motion by remember(status.motionSensitivity) {
        mutableStateOf(thresholdForSensitivity(status.motionSensitivity).toFloat())
    }
    var sound by remember(status.soundSensitivity) {
        mutableStateOf(thresholdForSensitivity(status.soundSensitivity).toFloat())
    }

    Box(
        Modifier
            .fillMaxSize()
            // A scrim, not a screen. The picture stays visible behind it: a parent adjusting
            // the camera is watching the effect of the adjustment.
            .background(SCRIM)
            // Tap outside to dismiss, but never a focus target: on a television this fills
            // the screen, and a screen-sized focusable would take every D-pad press before
            // the panel's own controls ever saw one. The remote leaves by Done.
            .skipDpadFocus()
            .pressable(onClick = onDismiss, focusShape = MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(Space.lg)
                .widthIn(max = PANEL_MAX_WIDTH)
                .heightIn(max = PANEL_MAX_HEIGHT),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Space.lg),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Touch.min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = status.deviceName,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "Changing the nursery device from here",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = HINT),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PanelAction("Done", Modifier.initialFocus(), onDismiss)
                }

                Spacer(Modifier.height(Space.sm))

                if (status.canSwitchCamera) {
                    PanelRow(
                        label = if (status.usingFrontCamera) "Front camera" else "Back camera",
                        value = "The lens pointed at the cot",
                    ) {
                        PanelAction(if (status.usingFrontCamera) "Use rear" else "Use front") {
                            onChange(
                                ControlMessage.SetCameraSettings(
                                    useFrontCamera = !status.usingFrontCamera,
                                ),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                PanelSwitchRow(
                    label = "Torch",
                    value = if (status.torchOn) {
                        "Lighting the room from the camera phone"
                    } else {
                        "Off"
                    },
                    checked = status.torchOn,
                    onCheckedChange = { onChange(ControlMessage.SetCameraSettings(torchOn = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(Modifier.height(Space.sm))
                PanelLabel("Movement alerts · above ${motion.toInt()}%")
                PanelHint(
                    "The camera compares each frame with the one before and alerts when " +
                        "enough of the picture has changed. A kicked-off blanket counts.",
                )
                // The camera's own reading, so this works on every viewer — phone, tablet or
                // television — and whether or not the sound is on.
                val cameraMotion = status.motionLevel.takeIf { it >= 0f }
                if (cameraMotion != null) {
                    MotionLevelMarker(level = cameraMotion, thresholdPercent = motion.toInt())
                }
                PanelSlider(
                    value = motion,
                    onValueChange = { motion = it },
                    onCommit = {
                        onChange(
                            ControlMessage.SetCameraSettings(
                                motionSensitivity = sensitivityForThreshold(motion.toInt()),
                            ),
                        )
                    },
                )
                PanelHint(
                    if (cameraMotion != null) {
                        "The room is at ${motionLevelPercent(cameraMotion).toInt()}% right " +
                            "now. Alerts fire when it reaches ${motion.toInt()}%."
                    } else {
                        "Alerts fire when the picture changes more than this."
                    },
                )

                PanelLabel("Sound alerts · above ${sound.toInt()}%")
                PanelHint("Loudness only — it does not tell crying from a passing lorry.")
                // The camera's reading first; this viewer's own audio only as a fallback for
                // a camera too old to send one.
                val cameraSound = status.soundLevel.takeIf { it >= 0f } ?: roomLevel
                if (cameraSound != null) {
                    SoundLevelMarker(level = cameraSound, thresholdPercent = sound.toInt())
                }
                PanelSlider(
                    value = sound,
                    onValueChange = { sound = it },
                    onCommit = {
                        onChange(
                            ControlMessage.SetCameraSettings(
                                soundSensitivity = sensitivityForThreshold(sound.toInt()),
                            ),
                        )
                    },
                )
                PanelHint(
                    if (cameraSound != null) {
                        "The room is at ${soundLevelPercent(cameraSound).toInt()}% right now. " +
                            "Alerts fire when it reaches ${sound.toInt()}%, so set the line " +
                            "above anything you want ignored."
                    } else {
                        // Said plainly rather than drawing a mark pinned at zero, which would
                        // read as a silent nursery instead of as nothing measured.
                        "This camera is not reporting a sound level."
                    },
                )

                Spacer(Modifier.height(Space.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.sm))

                // Everything below here rebinds the camera, which drops the picture for a
                // second. Said plainly rather than discovered.
                PanelLabel("Picture — changing these blinks the feed")
                Spacer(Modifier.height(Space.xs))
                PanelChoices(
                    options = VIDEO_HEIGHTS.map { it.label to it.height },
                    selected = status.videoHeight,
                    onSelect = { height ->
                        val size = VIDEO_HEIGHTS.first { it.height == height }
                        onChange(
                            ControlMessage.SetCameraSettings(
                                videoWidth = size.width,
                                videoHeight = size.height,
                            ),
                        )
                    },
                )
                Spacer(Modifier.height(Space.xs))
                PanelChoices(
                    options = FRAME_RATES.map { "$it fps" to it },
                    selected = status.frameRate,
                    onSelect = { onChange(ControlMessage.SetCameraSettings(frameRate = it)) },
                )
            }
        }
    }
}

/** A line of explanation under a control's label. */
@Composable
private fun PanelHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = HINT, lineHeight = HINT_LINE),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = LABEL),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PanelRow(label: String, value: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Touch.min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            PanelLabel(label)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = HINT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action()
    }
}

@Composable
private fun PanelSwitchRow(
    label: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .pressable(
                onClick = { onCheckedChange(!checked) },
                wide = true,
                role = SemanticsRole.Switch,
                focusShape = MaterialTheme.shapes.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            PanelLabel(label)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = HINT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.xs))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PanelSlider(value: Float, onValueChange: (Float) -> Unit, onCommit: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        // Sent on release, not on every pixel: each change is a websocket message and a
        // status broadcast to every viewer.
        onValueChangeFinished = onCommit,
        steps = SLIDER_STEPS,
        valueRange = 0f..100f,
        interactionSource = interaction,
        modifier = Modifier.focusRing(interaction, MaterialTheme.shapes.large),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

@Composable
private fun <T> PanelChoices(
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
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION),
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

@Composable
private fun PanelAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Dark enough to read the panel against a bright frame, sheer enough to keep watching. */
private val SCRIM = Color(0xCC07080C)

private val PANEL_MAX_WIDTH = 460.dp
private val PANEL_MAX_HEIGHT = 640.dp
private val TITLE = 19.sp
private val LABEL = 13.sp
private val HINT = 11.5.sp
private val HINT_LINE = 16.sp
private val ACTION = 13.sp
private const val SLIDER_STEPS = 9
