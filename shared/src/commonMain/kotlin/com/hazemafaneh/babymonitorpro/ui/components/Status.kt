package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch

/**
 * What the app is doing right now, in one or two words.
 *
 * A [tone] rather than a colour, because the whole point of the semantic layer is that a
 * call site cannot decide for itself that its state deserves amber.
 */
enum class StatusTone { LIVE, DEGRADED, WAITING, FAULT }

@Composable
private fun StatusTone.color(): Color = when (this) {
    StatusTone.LIVE -> BmpTheme.semantic.statusLive
    StatusTone.DEGRADED -> BmpTheme.semantic.statusDegraded
    StatusTone.WAITING -> BmpTheme.semantic.statusWaiting
    StatusTone.FAULT -> BmpTheme.semantic.statusFault
}

/**
 * The chip that answers "is this thing working" from across a dark room. One steady dot and
 * a word.
 *
 * The dot never pulses or blinks. Anything that blinks in a dark bedroom is a thing the
 * parent learns to tune out within a night, and it lights the room while it does it — so the
 * signal is the dot's *colour*, which costs no motion and no light.
 *
 * [trailing] carries a figure that qualifies the state without changing it — the live view's
 * round-trip latency, and nothing else so far.
 */
@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Surface(
        modifier = modifier,
        // Cream, opaque, and the same pill the viewer count sits in. A translucent white
        // chip took its tint from whatever the camera happened to be pointed at, so the one
        // element that has to be legible in every frame was the one that changed with them.
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(PILL_RADIUS),
    ) {
        Row(
            Modifier.padding(horizontal = PILL_H_PADDING, vertical = PILL_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tone.color()))
            Spacer(Modifier.size(Space.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = PILL_TEXT),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (trailing != null) {
                Spacer(Modifier.size(Space.xs))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The chip stripped to its dot.
 *
 * Full chrome auto-hides so the picture is the interface, but that left nothing at all on
 * screen answering "is this still live" — the one question the parent picked the app up to
 * ask. This stays put: eight pixels of colour costs no light worth speaking of and covers
 * none of the picture, and it is a colour rather than a blink for the same reason the chip's
 * dot is.
 */
@Composable
fun StatusDot(
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            // A ring of cream behind it, so the dot reads against a bright frame as well
            // as against black.
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone.color()))
    }
}

/**
 * Where the bytes are going, right now.
 *
 * This replaces the paragraph of grey prose that used to sit at the bottom of two screens.
 * A claim about privacy in marketing voice is something a parent has to take on trust; the
 * same claim rendered as live telemetry — this address, on your network, currently — is
 * something they can check against their own router. It sits in the same slot directly under
 * the connection status on every screen, drawn from the same state.
 *
 * [address] null means there is nothing to verify yet, so it says less rather than promising
 * more.
 */
@Composable
fun PrivacyLine(
    address: String?,
    modifier: Modifier = Modifier,
) {
    val privacy = BmpTheme.semantic.privacy
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Unplated, unlike the other five. This line appears over the picture as often as
        // it appears on a card, and a filled square there would be one more object between
        // the parent and the room.
        Icon(
            imageVector = BmpIcons.Shield,
            contentDescription = null,
            tint = privacy,
            modifier = Modifier.size(PRIVACY_ICON),
        )
        Spacer(Modifier.width(Space.xxs))
        Text(
            text = if (address == null) {
                "On your WiFi only · nothing uploaded"
            } else {
                "On your WiFi · $address"
            },
            style = MaterialTheme.typography.bodySmall,
            color = privacy,
        )
    }
}

/**
 * Section heading inside a card. All caps, tracked out, quiet.
 *
 * [icon] is drawn inline at label size rather than on a plate — a plate here would outweigh
 * the heading it is labelling.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LABEL_ICON),
            )
            Spacer(Modifier.width(Space.xxs))
        }
        Text(
            // Small, because caps read a size larger than they set, and this label's job is
            // to name the card without competing with the address inside it.
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = LABEL_TEXT,
                letterSpacing = TRACKING,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A value that gets read aloud across a room — the pairing address.
 *
 * Monospace for the tabular, unambiguous figures: at arm's length in the dark, a
 * proportional `1` next to an `l`, or a `0` next to an `O`, is a pairing attempt that fails
 * for no visible reason. The platform's own monospace stack, so this costs no font asset.
 */
@Composable
fun MonoValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Text(
        text = text,
        style = style.copy(fontFamily = FontFamily.Monospace),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/**
 * The `›` on a tappable row.
 *
 * Drawn rather than imported: it is the only glyph in this design that has to carry meaning
 * with no label beside it, and two lines on a Canvas is cheaper than an icon dependency for
 * one shape. Everything else here is a word or a filled circle — a labelled control beats a
 * glyph for a half-asleep user.
 */
@Composable
fun Chevron(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val x = w * 0.38f
        val stroke = w * 0.09f
        drawLine(
            color = color,
            start = Offset(x, h * 0.28f),
            end = Offset(x + w * 0.22f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(x + w * 0.22f, h * 0.5f),
            end = Offset(x, h * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The same cream pill as [StatusChip], without the dot. */
@Composable
fun OverlayPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(PILL_RADIUS),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = PILL_TEXT),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = PILL_H_PADDING, vertical = PILL_V_PADDING),
        )
    }
}

/** A row whose whole width is the target, ending in a chevron. */
@Composable
fun NavRow(
    label: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .pressable(onClick = onClick, wide = true)
            .heightIn(min = Touch.min)
            .padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Space.xs))
        Chevron()
    }
}

private val PILL_RADIUS = 20.dp
private val PILL_H_PADDING = 12.dp
private val PILL_V_PADDING = 7.dp
private val PILL_TEXT = 12.sp
private val PRIVACY_ICON = 14.dp
private val LABEL_ICON = 15.dp
private val LABEL_TEXT = 11.sp
private val TRACKING = 0.07.em
