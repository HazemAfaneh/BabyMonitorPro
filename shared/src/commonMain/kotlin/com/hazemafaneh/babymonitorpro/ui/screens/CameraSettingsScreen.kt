package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.ui.components.SoundMeter
import com.hazemafaneh.babymonitorpro.ui.components.PlateSize
import com.hazemafaneh.babymonitorpro.ui.components.MoonButton
import com.hazemafaneh.babymonitorpro.ui.components.IconPlate
import com.hazemafaneh.babymonitorpro.ui.components.CARD_BORDER
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import com.hazemafaneh.babymonitorpro.server.BroadcastState
import com.hazemafaneh.babymonitorpro.di.BroadcasterHolder
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.components.StopBroadcastingButton
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * Everything the parent adjusts once and then forgets.
 *
 * Split out of [CameraScreen] because these nine controls were competing for vertical space
 * with the pairing code — a job that has to be finished in the first two minutes, standing
 * up, while holding a second device.
 *
 * The broadcaster is injected rather than constructed, so this drives the same running
 * instance the camera screen does — arriving here does not restart the stream, and the
 * lens switch and sensitivity slider reach the socket that is actually serving viewers.
 */
@Composable
fun CameraSettingsScreen(
    night: Boolean,
    onNightChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val broadcaster = koinInject<BroadcasterHolder>().broadcaster
    val window = rememberWindowClass()
    val tints = BmpTheme.tints

    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var sensitivity by remember { mutableStateOf(settings.motionSensitivity.toFloat()) }
    var renaming by remember { mutableStateOf(false) }

    val stateFlow = remember(broadcaster) {
        broadcaster?.state ?: MutableStateFlow(BroadcastState(running = false))
    }
    val state by stateFlow.collectAsState()

    val levelFlow = remember(broadcaster) { broadcaster?.soundLevel ?: MutableStateFlow(0f) }
    val level by levelFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = window.gutter()),
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
                text = "Camera settings",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_TEXT),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonButton(night = night, onNightChanged = onNightChanged)
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = ACTION_TEXT),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // What this device is and what it is sending — two facts, two rows, no headings.
        // The card that used to hold them opened with a text field, which made renaming the
        // device look like the first thing a parent came here to do.
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
                    label = if (state.usingFrontCamera) "Front camera" else "Back camera",
                    value = if (state.syntheticVideo) "Test pattern" else "Sending video to viewers",
                    divided = true,
                ) {
                    if (state.canSwitchCamera) {
                        RowAction(
                            label = if (state.usingFrontCamera) "Use rear" else "Use front",
                            onClick = { broadcaster?.switchCamera() },
                        )
                    }
                }

                SettingRow(
                    icon = BmpIcons.Rattle,
                    plateFill = tints.lemon.fill,
                    plateGlyph = tints.lemon.glyph,
                    label = "Microphone",
                    value = if (state.audioActive) {
                        "On · sending sound to viewers"
                    } else {
                        "No microphone on this device"
                    },
                    divided = true,
                )

                SettingRow(
                    icon = BmpIcons.Teddy,
                    plateFill = tints.lemon.fill,
                    plateGlyph = tints.lemon.glyph,
                    label = deviceName,
                    value = "The name viewers see",
                    divided = false,
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
            }
        }

        Spacer(Modifier.height(Space.sm))

        SectionCard(title = "What the room sounds like now", icon = BmpIcons.Rattle) {
            SoundMeter(level = level, sensitivity = sensitivity.toInt())

            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    // A named level, not a bare number: 62 tells a parent nothing about
                    // whether a passing car will wake them.
                    text = buildAnnotatedString {
                        append("Alert me at ")
                        withStyle(SpanStyle(color = tints.lemon.glyph)) {
                            append(sensitivityLabel(sensitivity.toInt()))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = SETTING_LABEL),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${sensitivity.toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    // The stops exist to quantise the value, not to be counted. Ten dots
                    // printed along the track invited reading it as a scale with meanings
                    // at each mark, which it does not have.
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Text(
                text = if (sensitivity.toInt() == 0) {
                    "No alerts. The stream keeps running."
                } else {
                    "Drag right and smaller movements and quieter sounds will reach you. " +
                        "The bars above are live, so you can set it against the actual room."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = HELPER_TEXT,
                    lineHeight = HELPER_LINE,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.sm))

        PrivacyCard(address = state.primaryAddress)

        Spacer(Modifier.height(Space.md))

        StopBroadcastingButton(broadcaster, onStop)

        Spacer(Modifier.height(Space.xl))
    }
}

/** One fact about this camera, and whatever can be done about it. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    plateFill: Color,
    plateGlyph: Color,
    label: String,
    value: String,
    divided: Boolean,
    action: @Composable () -> Unit = {},
) {
    Column {
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

@Composable
private fun RowAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
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
            Column {
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

private val TITLE_TEXT = 21.sp
private val ACTION_TEXT = 13.sp
private val SETTING_LABEL = 13.sp
private val SETTING_LABEL_LG = 15.sp
private val SETTING_VALUE = 11.5.sp
private val HELPER_TEXT = 12.sp
private val HELPER_LINE = 18.sp
private val PRIVACY_LINE = 17.sp
private val PRIVACY_ICON = 16.dp
private val PRIVACY_V_PADDING = 15.dp
private val ROWS_H_PADDING = 18.dp
private val ROWS_V_PADDING = 6.dp
private val ROW_HEIGHT = 60.dp
private val ROW_V_PADDING = 13.dp

private fun sensitivityLabel(value: Int): String = when {
    value == 0 -> "Off"
    value < 34 -> "Low"
    value < 67 -> "Medium"
    else -> "High"
}

private const val SENSITIVITY_STEPS = 9
