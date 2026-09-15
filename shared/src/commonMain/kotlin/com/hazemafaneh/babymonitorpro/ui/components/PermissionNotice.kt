package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space

/**
 * Something the app needs and does not have, with the one button that can fix it.
 *
 * Deliberately a card with a control rather than a line of advice. The app used to fire the
 * system dialog the instant a screen opened and then never mention it again: a parent who
 * tapped Deny — or who tapped nothing because the dialog arrived before they had read the
 * screen behind it — was left with a monitor that quietly did not work and no way back.
 * Asking is a thing the parent does here, not a thing that happens to them.
 *
 * [blocked] switches which offer is honest. Until the system has stopped answering, the
 * button raises the dialog; after that the same button would do nothing at all, so it
 * becomes a trip to Settings instead.
 */
@Composable
fun PermissionNotice(
    icon: ImageVector,
    title: String,
    explanation: String,
    blocked: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = BmpTheme.tints.lemon
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tint.fill,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(NOTICE_BORDER, tint.border),
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
            IconPlate(
                icon = icon,
                fill = MaterialTheme.colorScheme.surface,
                contentColor = tint.glyph,
                size = PlateSize.row,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = NOTICE_TITLE,
                    lineHeight = NOTICE_TITLE_LINE,
                ),
                fontWeight = FontWeight.Bold,
                color = tint.content,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                // Why, in the parent's terms, before the ask. A permission dialog with no
                // sentence in front of it is a dialog people decline on principle.
                text = explanation,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = NOTICE_BODY,
                    lineHeight = NOTICE_BODY_LINE,
                ),
                color = tint.contentMuted,
            )
            Spacer(Modifier.height(Space.sm))

            val interactionSource = remember { MutableInteractionSource() }
            Button(
                onClick = if (blocked) onOpenSettings else onRequest,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BUTTON_HEIGHT)
                    .pressScale(interactionSource, wide = true),
                shape = RoundedCornerShape(BUTTON_RADIUS),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tint.glyph,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    text = if (blocked) "Open Settings" else "Allow",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = BUTTON_TEXT),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val NOTICE_BORDER = 1.5.dp
private val NOTICE_TITLE = 16.sp
private val NOTICE_TITLE_LINE = 20.sp
private val NOTICE_BODY = 13.sp
private val NOTICE_BODY_LINE = 19.sp
private val BUTTON_HEIGHT = 50.dp
private val BUTTON_RADIUS = 16.dp
private val BUTTON_TEXT = 15.sp
