package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The tinted square a baby icon sits in.
 *
 * The plate is what makes an icon read as *part of* the card rather than as a bullet point
 * floating beside the text — and, at night, it is the only thing that still distinguishes
 * one card's icon from another's once the four tints have collapsed to `surface`.
 *
 * There is no `contentDescription` on purpose. Every one of these sits next to a label that
 * already says the thing, so announcing the icon as well would read the same fact twice to
 * a screen-reader user. If a plate ever needs to stand alone it needs a label first.
 */
@Composable
fun IconPlate(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = PlateSize.medium,
) {
    Box(
        modifier
            .size(size)
            // Scaled with the plate rather than fixed: a 15dp radius on a 28dp plate is
            // nearly a circle, and an 11dp radius on a 52dp one is nearly a square.
            .background(fill, RoundedCornerShape(size * RADIUS_RATIO)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

/** The three plate sizes the design uses. Anything else is a value someone invented. */
object PlateSize {
    /** Inside a row — discovered cameras, settings rows, the alert banner. */
    val small = 32.dp

    /** Card headers, and the role cards. */
    val medium = 42.dp

    /** Discovered rows, and the route cards. */
    val row = 38.dp

    /** The app mark, and the role cards. */
    val large = 52.dp

    /** The one plate that stands alone: an empty state, where there is nothing else to see. */
    val hero = 66.dp
}

private const val RADIUS_RATIO = 0.34f
private const val ICON_RATIO = 0.55f
