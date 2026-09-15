package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.qr.QrEncoder
import com.hazemafaneh.babymonitorpro.server.Broadcaster
import com.hazemafaneh.babymonitorpro.ui.theme.Space

/**
 * A card with a heading.
 *
 * White on the cream ground with a 1.5dp border and no shadow — the border is the whole of
 * this app's depth model, and a shadow under a white card on cream is a grey smudge rather
 * than a lift.
 *
 * [icon] is drawn inline at label size rather than on a plate. A plate here outweighed the
 * heading it was labelling: the card's subject is the address or the control inside it, not
 * the two words naming the group.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = CARD_H_PADDING, vertical = CARD_V_PADDING)) {
            SectionLabel(title, icon = icon)
            Spacer(Modifier.height(Space.xs))
            content()
        }
    }
}

/**
 * Renders a pairing code. Drawn as plain rectangles on a Compose canvas so the same
 * code path runs on every target, including the browser.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) { QrEncoder.encode(content) }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(QR_LIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = QR_DARK,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            Canvas(Modifier.fillMaxWidth().padding(QR_QUIET_ZONE).aspectRatio(1f)) {
                val moduleSize = size.minDimension / matrix.size
                for (y in 0 until matrix.size) {
                    for (x in 0 until matrix.size) {
                        if (!matrix[x, y]) continue
                        drawRect(
                            color = QR_DARK,
                            topLeft = Offset(x * moduleSize, y * moduleSize),
                            // A hair of overlap avoids seams between modules on fractional scales.
                            size = Size(moduleSize + 0.5f, moduleSize + 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Takes the camera offline, and then leaves the screen. In that order, and never one without
 * the other.
 *
 * It lives here rather than in either screen because it is on both of them, and when the two
 * spelled the action out separately one of them navigated away while the server kept running
 * — the parent saw the role picker, every viewer kept its picture, and the camera stayed
 * reachable to anyone on the WiFi. Stopping is the whole point of the control; it cannot be
 * left to the call site to remember.
 *
 * Outlined and error-toned, never filled: this is the one destructive control in the app, and
 * an outline reads as deliberate where a filled error button reads as the obvious next step.
 */
@Composable
fun StopBroadcastingButton(
    broadcaster: Broadcaster?,
    onStopped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = {
            broadcaster?.requestStop()
            onStopped()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = STOP_BUTTON_HEIGHT)
            .pressScale(interactionSource, wide = true),
        shape = RoundedCornerShape(STOP_BUTTON_RADIUS),
        border = BorderStroke(CARD_BORDER, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(
            text = "Stop broadcasting",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
        )
    }
}

private val STOP_BUTTON_HEIGHT = 56.dp
private val STOP_BUTTON_RADIUS = 18.dp

/** Every card border in the app, and the stop button's outline. */
internal val CARD_BORDER = 1.5.dp
private val CARD_H_PADDING = 20.dp
private val CARD_V_PADDING = 18.dp
/** The code's own margin. Any less and a scanner loses the finder patterns. */
private val QR_QUIET_ZONE = 8.dp

private val QR_LIGHT = Color(0xFFF4F1EA)
private val QR_DARK = Color(0xFF12131A)
