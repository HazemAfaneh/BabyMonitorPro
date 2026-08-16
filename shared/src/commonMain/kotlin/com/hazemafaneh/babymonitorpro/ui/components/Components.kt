package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
        Column(Modifier.padding(20.dp)) {
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

private val QR_LIGHT = Color(0xFFF4F1EA)
private val QR_DARK = Color(0xFF12131A)
