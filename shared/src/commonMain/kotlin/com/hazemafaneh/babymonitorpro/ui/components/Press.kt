package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme

/**
 * Press feedback: the surface takes a small scale down and comes back.
 *
 * It is the only press feedback in the app. A ripple answers "the pixel you touched
 * registered"; a scale answers "this whole card is the thing you pressed", which is the
 * question that matters when the target *is* a whole card — a role card, a discovered
 * camera, a route out of a dead end. On a row spanning the screen a ripple starting under
 * one thumb also says nothing about the far end of the row.
 *
 * The release is slower than the press on purpose. The finger has already gone by then, but
 * the eye has not, and a symmetric bounce reads as a wobble rather than as a button.
 *
 * At night it is zero — the durations come from [BmpTheme.motion], which is where the
 * night scheme flattens press and celebrate to nothing. There is no branch here.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    wide: Boolean = false,
    role: Role? = null,
    onClickLabel: String? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, wide)
        .clickable(
            interactionSource = interactionSource,
            // The scale is the feedback. A ripple on top of it is two answers to one press,
            // and on a tinted card the ripple is the one that looks like a rendering fault.
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/**
 * The scale on its own, for a Material button that owns its own click handling.
 *
 * Pass the same [interactionSource] to the button, or it will never learn it was pressed.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    wide: Boolean = false,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = BmpTheme.motion
    val scale by animateFloatAsState(
        // Wide rows take a shallower scale: the same 3% on a full-width row moves its far
        // edge several times as far as it moves a card's, which reads as the row sliding
        // rather than as it being pressed.
        targetValue = if (pressed) {
            if (wide) motion.pressScaleWide else motion.pressScale
        } else {
            1f
        },
        animationSpec = tween(
            durationMillis = if (pressed) motion.pressInMillis else motion.pressOutMillis,
        ),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
