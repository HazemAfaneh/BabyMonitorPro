package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.ui.components.pressable
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.core.supportsCameraRole
import com.hazemafaneh.babymonitorpro.ui.components.IconPlate
import com.hazemafaneh.babymonitorpro.ui.components.PlateSize
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Tint

/**
 * Which end of the monitor this device is.
 *
 * The only screen where a first-time user is actually reading, so it is also the only place
 * the privacy promise gets a full sentence rather than the live status line the rest of the
 * app uses.
 *
 * The two cards are tinted rather than one-accented-and-one-plain. A parent making this
 * choice has no idea yet which is which, and two cards of equal weight in two different
 * colours say "these are two kinds of thing" faster than any amount of copy — while an
 * accent on one of them would say "this is the right one", which is not true; it depends
 * entirely on which room the device is going to sit in.
 */
@Composable
fun RolePickerScreen(
    lastRole: Role?,
    onPick: (Role) -> Unit,
) {
    val tints = BmpTheme.tints

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            // Its own gutter rather than the window's. This screen is a centred column of
            // two cards with nothing beside them, so it can afford the wider inset that
            // would cost the camera screen part of its picture.
            .padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The app mark. The joy accent is spent here and on the sound meter, and
                // nowhere else — it is the one colour in the app allowed to mean nothing.
                IconPlate(
                    icon = BmpIcons.Teddy,
                    fill = tints.joy,
                    contentColor = MARK_CONTENT,
                    size = PlateSize.large,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    // Two lines by design: on a 393pt phone the one-line form sets at a
                    // size that leaves the mark beside it looking like a bullet point.
                    text = "BabyMonitor\nPro",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 27.sp,
                        lineHeight = 30.sp,
                        letterSpacing = (-0.02).em,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(Space.sm))

            // The claim in a full sentence — the one place it is prose rather than the live
            // address line every other screen carries.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = BmpIcons.Shield,
                    contentDescription = null,
                    tint = BmpTheme.semantic.privacy,
                    modifier = Modifier.width(15.dp).height(15.dp),
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = "Everything stays on your WiFi",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = BmpTheme.semantic.privacy,
                )
            }

            Spacer(Modifier.height(Space.md))

            if (supportsCameraRole) {
                RoleCard(
                    icon = BmpIcons.Camera,
                    tint = tints.sky,
                    title = "Use this device\nas Camera",
                    description = "Stays in the nursery. Broadcasts video and sound to " +
                        "your other devices.",
                    highlighted = lastRole == Role.CAMERA,
                    onClick = { onPick(Role.CAMERA) },
                )
                Spacer(Modifier.height(Space.md))
            }

            RoleCard(
                icon = BmpIcons.Moon,
                tint = tints.grape,
                title = "Watch a camera",
                description = if (supportsCameraRole) {
                    "Find the nursery camera on this network and open the live view."
                } else {
                    // Not an apology. A browser cannot host a server, and saying so is more
                    // use than a card that fails when tapped.
                    "Browsers can only watch — run the camera on a phone, tablet or computer."
                },
                highlighted = lastRole == Role.VIEWER,
                onClick = { onPick(Role.VIEWER) },
            )

            Spacer(Modifier.height(Space.lg))
            Text(
                text = "No account, no cloud, no recording. Video and audio travel directly " +
                    "between your devices.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleCard(
    icon: ImageVector,
    tint: Tint,
    title: String,
    description: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .pressable(onClick = onClick),
        color = tint.fill,
        shape = MaterialTheme.shapes.extraLarge,
        // The border, not a shadow. Depth in this app is ground → surface → tint, and a
        // shadow under a cream card on a cream ground is a grey smudge, not a lift.
        border = BorderStroke(CARD_BORDER, tint.border),
    ) {
        Column(Modifier.padding(horizontal = Space.xl, vertical = CARD_PADDING_VERTICAL)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // White, so the glyph reads as sitting on the card rather than being
                // printed into it.
                IconPlate(
                    icon = icon,
                    fill = MaterialTheme.colorScheme.surface,
                    contentColor = tint.glyph,
                    size = PlateSize.medium,
                )
                Spacer(Modifier.width(Space.sm))
                // A flow, not a row: the title keeps the full column width and the badge
                // sits beside it only when it fits, otherwise it drops beneath. A row gave
                // the badge its width first and left the title breaking mid-word.
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = tint.content,
                    )
                    // A labelled badge rather than a coloured dot. "Last used" is what the
                    // dot was trying to say, and saying it costs one word.
                    if (highlighted) LastUsedBadge()
                }
            }
            Spacer(Modifier.height(DESCRIPTION_GAP))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                color = tint.contentMuted,
            )
        }
    }
}

/**
 * Which card the parent chose last time.
 *
 * In the joy accent, which is the one colour in the app that carries no state — and this is
 * not a state: it is a memory, and a device that was the camera last night is not thereby
 * the camera tonight. Nothing auto-starts from it.
 */
@Composable
private fun LastUsedBadge() {
    Text(
        text = "LAST USED",
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
        fontWeight = FontWeight.Bold,
        color = MARK_CONTENT,
        modifier = Modifier
            .background(BmpTheme.tints.joy, RoundedCornerShape(BADGE_RADIUS))
            .padding(horizontal = Space.xs, vertical = Space.xxs),
    )
}

/** Dark enough to read on the joy accent, warm enough not to look like ink on it. */
private val MARK_CONTENT = Color(0xFF5A4415)

private val CARD_BORDER = 1.5.dp
private val CARD_PADDING_VERTICAL = 22.dp
private val DESCRIPTION_GAP = 10.dp
private val BADGE_RADIUS = 7.dp

/** Keeps the cards a readable width on a desktop window rather than stretching them. */
private val CONTENT_MAX_WIDTH = 520.dp
