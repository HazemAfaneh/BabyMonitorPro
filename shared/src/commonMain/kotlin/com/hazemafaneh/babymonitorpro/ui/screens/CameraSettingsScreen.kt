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
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.server.BroadcastState
import com.hazemafaneh.babymonitorpro.di.BroadcasterHolder
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.ui.components.SectionCard
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.components.StopBroadcastingButton
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
    nightEnabled: Boolean,
    onNightEnabledChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
) {
    val settings = koinInject<AppSettings>()
    val broadcaster = koinInject<BroadcasterHolder>().broadcaster
    val window = rememberWindowClass()

    var deviceName by remember { mutableStateOf(settings.deviceName) }
    var sensitivity by remember { mutableStateOf(settings.motionSensitivity.toFloat()) }

    val stateFlow = remember(broadcaster) {
        broadcaster?.state ?: MutableStateFlow(BroadcastState(running = false))
    }
    val state by stateFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = window.gutter()),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Touch.min),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Camera settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onBack) { Text("Done") }
        }

        Spacer(Modifier.height(Space.sm))

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
                Spacer(Modifier.height(Space.xs))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Touch.min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
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

        Spacer(Modifier.height(Space.sm))

        SectionCard(title = "Alert sensitivity") {
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
                    color = MaterialTheme.colorScheme.secondary,
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
                text = if (sensitivity.toInt() == 0) {
                    "No alerts. The stream keeps running."
                } else {
                    "Higher means smaller movements and quieter sounds raise an alert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.sm))

        SectionCard(title = "Comfort") {
            ToggleRow(
                label = "Night dim",
                description = "Darkens surfaces after 6s. Text stays readable.",
                checked = nightEnabled,
                onCheckedChange = onNightEnabledChanged,
            )
        }

        Spacer(Modifier.height(Space.lg))

        StopBroadcastingButton(broadcaster, onStop)

        Spacer(Modifier.height(Space.xl))
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
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = SemanticsRole.Switch,
            )
            .heightIn(min = Touch.min)
            .padding(vertical = Space.xxs),
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

private fun sensitivityLabel(value: Int): String = when {
    value == 0 -> "Off"
    value < 34 -> "Low"
    value < 67 -> "Medium"
    else -> "High"
}

private const val SENSITIVITY_STEPS = 9
