package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.core.supportsCameraRole
import com.hazemafaneh.babymonitorpro.ui.components.PrivacyLine
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.theme.Space

/**
 * Which end of the monitor this device is.
 *
 * The only screen where a first-time user is actually reading, so it is also the only place
 * the privacy promise gets a full sentence rather than the live status line the rest of the
 * app uses.
 */
@Composable
fun RolePickerScreen(
    lastRole: Role?,
    onPick: (Role) -> Unit,
) {
    val window = rememberWindowClass()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .padding(horizontal = window.gutter()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
            Text(
                text = "BabyMonitor Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Space.xxs))
            // The claim itself, in the accent reserved for it, right under the name.
            PrivacyLine(address = null)

            Spacer(Modifier.height(Space.xxl))

            if (supportsCameraRole) {

                RoleCard(
                    title = "Use this device as Camera",
                    subtitle = "Stays in the room you are watching. Broadcasts video and sound to your other devices.",
                    accent = true,
                    highlighted = lastRole == Role.CAMERA,
                    onClick = { onPick(Role.CAMERA) },
                )
                Spacer(Modifier.height(Space.md))
            }

            RoleCard(
                title = "Watch a camera",
                subtitle = if (supportsCameraRole) {
                    "Find the camera on this network and open the live view."
                } else {
                    // Not an apology. A browser cannot host a server, and saying so is more
                    // use than a card that fails when tapped.
                    "Browsers can only watch — run the camera on a phone, tablet or computer."
                },
                accent = false,
                highlighted = lastRole == Role.VIEWER,
                onClick = { onPick(Role.VIEWER) },
            )

            Spacer(Modifier.height(Space.xl))
            Text(
                text = "No account, no cloud, no recording. Video and audio travel directly " +
                    "between your devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    accent: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
        color = if (accent) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(if (highlighted) 0.7f else 1f),
                )
                // A labelled pill rather than a coloured dot. "Last used" is what the dot
                // was trying to say, and saying it costs one word.
                if (highlighted) {
                    Text(
                        text = "LAST USED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Keeps the cards a readable width on a desktop window rather than stretching them. */
private val CONTENT_MAX_WIDTH = 520.dp
