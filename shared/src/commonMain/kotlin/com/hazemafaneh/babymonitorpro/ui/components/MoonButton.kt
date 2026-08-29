package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons

/**
 * Night, on or off. The only thing that changes the scheme.
 *
 * Manual, and manual only. There is no sunset schedule and no light-sensor trigger, because
 * neither one knows what this switch needs to know: a nursery with the blind down at 4pm is
 * dark, a kitchen at 11pm is not, and the phone is usually face-down on a table when either
 * would fire. A parent already standing in the room is the only reliable sensor there is,
 * and the cost of asking them is one button.
 *
 * The state it writes is **device-local**. It is deliberately not part of the broadcast
 * state: the nursery phone is in a dark room and the kitchen tablet is not, and a shared
 * flag would mean whichever one was touched last decided for both.
 *
 * Filled moon = night on. Same crescent either way — the parent is toggling one thing, and
 * two differently-drawn icons would read as two different controls.
 */
@Composable
fun MoonButton(
    night: Boolean,
    onNightChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = { onNightChanged(!night) },
        modifier = modifier
            .size(BUTTON_SIZE)
            // A plate, not a bare glyph. It sits beside a text action, and an unplated icon
            // next to a word reads as decoration on the word rather than as a second
            // control — which is how the moon got missed entirely in the first pass.
            .background(
                color = if (night) scheme.primaryContainer else scheme.surfaceVariant,
                shape = RoundedCornerShape(BUTTON_RADIUS),
            )
            .border(
                width = CARD_BORDER,
                color = if (night) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(BUTTON_RADIUS),
            ),
    ) {
        Icon(
            imageVector = if (night) BmpIcons.MoonFilled else BmpIcons.Moon,
            // Says what pressing it does, not what it currently is: the filled crescent
            // already carries the state, and a label that reads "Night mode, on" leaves a
            // screen-reader user to work out for themselves what the button will do.
            contentDescription = if (night) "Turn night mode off" else "Turn night mode on",
            tint = if (night) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

private val BUTTON_SIZE = 44.dp
private val BUTTON_RADIUS = 15.dp
private val ICON_SIZE = 22.dp
