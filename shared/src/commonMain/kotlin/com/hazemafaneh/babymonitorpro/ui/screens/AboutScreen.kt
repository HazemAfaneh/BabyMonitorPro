package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.core.appVersion
import com.hazemafaneh.babymonitorpro.ui.components.CARD_BORDER
import com.hazemafaneh.babymonitorpro.ui.components.IconPlate
import com.hazemafaneh.babymonitorpro.ui.components.PlateSize
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space

/**
 * Who made this, what it is, and what it will not do.
 *
 * Its own tab rather than a row at the bottom of Settings. Settings is a page a parent opens
 * with a job in mind — turn the sound on, move the threshold — and the answer to "what is this
 * app and who wrote it" has no business competing with it. It is also the page somebody opens
 * first when a phone is handed to them with the app already on it, so it carries the privacy
 * claim in full rather than as the one-line form the other screens use.
 *
 * Nothing here is interactive, deliberately: no rating prompt, no share sheet, no link out.
 * This is a monitor, and a page that sends a parent to a web browser at 3am is a page that
 * took them away from the cot.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val tints = BmpTheme.tints

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // The same wide inset the Monitor tab uses: this is a column of cards with
            // nothing beside them, and it reads better narrow than full-bleed.
            .padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
            Spacer(Modifier.height(Space.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPlate(
                    icon = BmpIcons.Crib,
                    fill = tints.joy,
                    contentColor = MARK_CONTENT,
                    size = PlateSize.large,
                )
                Spacer(Modifier.width(Space.sm))
                Column {
                    Text(
                        text = "BabyMonitor Pro",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = TITLE_TEXT,
                            letterSpacing = (-0.02).em,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "Version ${appVersion()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = META_TEXT),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Space.md))

            AboutCard(title = "What it is") {
                AboutParagraph(
                    "One phone watches the cot and another watches the phone. The picture and " +
                        "the sound travel straight from one device to the other over your own " +
                        "WiFi, and the app tells you when it hears or sees something.",
                )
            }

            Spacer(Modifier.height(Space.sm))

            AboutCard(title = "Away from home") {
                AboutParagraph(
                    "The app also works over Tailscale. Install it on both devices, sign in " +
                        "to the same tailnet, and the nursery camera can be watched from " +
                        "anywhere — still device to device, with nothing kept on a server in " +
                        "between.",
                )
                AboutParagraph(
                    "Cameras on a tailnet cannot announce themselves the way they do on your " +
                        "WiFi, so Find your camera asks for them instead. Open one by its " +
                        "Tailscale address once and it is waiting under Recently connected " +
                        "after that.",
                )
            }

            Spacer(Modifier.height(Space.sm))

            AboutCard(title = "Made by") {
                Credit("Hazem Afaneh")
                Spacer(Modifier.height(Space.xxs))
                Credit("Munes Bani Fawaz")
            }

            Spacer(Modifier.height(Space.sm))

            // The privacy claim in full. Every other screen states it in a line; this is the
            // page with room to say what the line means.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = tints.leaf.fill,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(CARD_BORDER, tints.leaf.border),
            ) {
                Row(
                    Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = BmpIcons.Shield,
                        contentDescription = null,
                        tint = tints.leaf.glyph,
                        modifier = Modifier.size(SHIELD_ICON).padding(top = 1.dp),
                    )
                    Spacer(Modifier.width(Space.xs))
                    Column {
                        Text(
                            text = "On your WiFi only",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = BODY_TEXT),
                            fontWeight = FontWeight.Bold,
                            color = tints.leaf.content,
                        )
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            text = "No account, no cloud and no recording. Video and audio go " +
                                "directly from one of your devices to the other and are never " +
                                "written to disk — not on the camera, not on the phone " +
                                "watching, not anywhere else. Close the app and the stream " +
                                "stops. The list of alerts lasts as long as the screen showing " +
                                "it.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = BODY_TEXT,
                                lineHeight = BODY_LINE,
                            ),
                            color = tints.leaf.contentMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))

            AboutCard(title = "Credits") {
                AboutParagraph(
                    "The cot mark is from Google's Material Symbols, used under the Apache " +
                        "License 2.0. Every other icon in the app was drawn for it.",
                )
            }

            Spacer(Modifier.height(Space.xl))
        }
    }
}

/** One titled card, the same shape Settings uses. */
@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = LABEL_TEXT,
                    letterSpacing = LABEL_TRACKING,
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun AboutParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = BODY_TEXT,
            lineHeight = BODY_LINE,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One name. Plated, because a list of two bare lines reads as a to-do. */
@Composable
private fun Credit(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconPlate(
            icon = BmpIcons.Crib,
            fill = BmpTheme.tints.lemon.fill,
            contentColor = BmpTheme.tints.lemon.glyph,
            size = PlateSize.small,
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = NAME_TEXT),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val CONTENT_MAX_WIDTH = 420.dp

/** The brown the cot is drawn in on its lemon plate, as on the Monitor tab. */
private val MARK_CONTENT = Color(0xFF5A4415)
private val TITLE_TEXT = 24.sp
private val NAME_TEXT = 15.sp
private val BODY_TEXT = 12.5.sp
private val BODY_LINE = 18.sp
private val META_TEXT = 12.sp
private val LABEL_TEXT = 10.5.sp
private val LABEL_TRACKING = 0.08.em
private val SHIELD_ICON = 15.dp
