package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.platformName
import com.hazemafaneh.babymonitorpro.qr.QrEncoder
import com.hazemafaneh.babymonitorpro.server.Broadcaster
import com.hazemafaneh.babymonitorpro.ui.theme.Space

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(Space.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
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
            .clip(MaterialTheme.shapes.medium)
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
            Canvas(Modifier.fillMaxWidth().padding(12.dp).aspectRatio(1f)) {
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
    OutlinedButton(
        onClick = {
            broadcaster?.requestStop()
            onStopped()
        },
        modifier = modifier.fillMaxWidth().heightIn(min = STOP_BUTTON_HEIGHT),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text("Stop broadcasting", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PrivacyNote(modifier: Modifier = Modifier) {
    Text(
        text = "Everything stays on your WiFi. BabyMonitor Pro has no account, no cloud " +
            "server and no recording — this ${platformName()} device talks straight to the " +
            "others in your home, and nothing is uploaded anywhere.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val STOP_BUTTON_HEIGHT = 56.dp
private val QR_LIGHT = Color(0xFFF4F1EA)
private val QR_DARK = Color(0xFF12131A)
