package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.core.isTelevision

/**
 * Where the remote is pointing.
 *
 * On a phone the answer is "wherever the thumb is" and nothing needs drawing. On a
 * television there is no pointer at all: the parent presses right three times and has to be
 * able to see what they are about to select before they press the middle button. Without
 * this the app is unusable from a sofa — every press is a guess, and the guess that selects
 * "Stop broadcasting" is an expensive one.
 *
 * A ring, not a fill. The focused item keeps its own colour — a focus treatment that
 * recolours the card makes a tinted card look like a different kind of card, and on this
 * screen the tint is how the two roles tell themselves apart. The ring is the app's marigold
 * because that is already the colour that means "this is the live thing", and drawn at 3dp
 * because 1.5dp — the card border weight — is invisible from three metres.
 *
 * ### Why this draws rather than using `Modifier.border`
 *
 * A border was the obvious first implementation and it was wrong twice over, in a way that
 * showed up on the TV as a ring that was half-there and never quite on the element:
 *
 *  - **It was painted underneath the card.** Modifiers draw outermost-first, and every call
 *    site here wraps a `Surface`, whose own fill is drawn by the surface *inside* the chain.
 *    So the ring went down first and the card's cream fill went straight over the top of it,
 *    leaving only the few pixels that overhung the surface's own bounds.
 *  - **It was clipped in half.** The call sites read `.clip(shape).pressable(...)`, and a
 *    stroke centred on the shape's edge loses its outer half to that clip. What survived was
 *    a 1.5dp hairline, which is precisely the weight the ring exists not to be.
 *
 * Drawing after the content fixes both, and insetting keeps the whole ring inside the bounds
 * — so it cannot be clipped by an ancestor, and it reads as a ring *around the thing* rather
 * than as a border *of* it.
 *
 * ### And why there is no scale
 *
 * The first version lifted the focused element by 2%, which looked right on a phone and was
 * still cut off on the television. The lift was the reason: a scaled node draws outside its
 * own layout bounds, and every one of these sits inside something that clips — a `Surface`
 * clipped to its shape, a card with padding, a tab strip against the overscan inset. Two per
 * cent of a full-width settings row is several pixels past the card's edge, and the card
 * clipped exactly the part of the ring the eye uses to see which row is selected.
 *
 * So the ring is the whole treatment, drawn 6dp inside the element where nothing can reach
 * it. It is also why the inset is double the stroke rather than flush: at the corners of a
 * rounded card the ring then sits visibly *within* the card's own border instead of merging
 * with it into one thick smudge.
 */
@Composable
fun Modifier.focusRing(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val show = enabled && focused
    // Faded in rather than switched on. A ring that appears instantly on every D-pad press
    // reads as flicker when the remote is held down and the focus walks a list.
    val presence by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(durationMillis = FOCUS_MILLIS),
        label = "focusRing",
    )
    val ring = MaterialTheme.colorScheme.primary
    return this.drawWithContent {
        drawContent()
        if (presence <= 0.01f) return@drawWithContent

        val stroke = FOCUS_RING.toPx()
        val inset = stroke * INSET_STROKES
        if (size.width <= inset * 2 || size.height <= inset * 2) return@drawWithContent

        val outline = shape.createOutline(
            size = Size(size.width - inset * 2, size.height - inset * 2),
            layoutDirection = layoutDirection,
            density = this,
        )
        translate(inset, inset) {
            drawOutline(
                outline = outline,
                color = ring,
                alpha = presence,
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * A control the remote can always be sent back to.
 *
 * Returned as a requester rather than applied blindly, because some screens need to *return*
 * focus here as well as start with it — see the live view, where a banner that comes and goes
 * would otherwise take the focus with it when it leaves.
 *
 * Off a television this is inert: the requester is never asked for focus, and
 * [Modifier.focusAnchor] does not even install it.
 */
@Composable
fun rememberFocusAnchor(requestOnAppear: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    if (!isTelevision || !requestOnAppear) return requester
    LaunchedEffect(requester) {
        // The node has to exist before it can hold focus; requesting it in the same frame as
        // the first composition throws.
        runCatching { requester.requestFocus() }
    }
    return requester
}

/**
 * Hands focus to [requester] whenever [trigger] becomes true.
 *
 * For a control that *replaces* the one the remote is standing on — a row that swaps its
 * single action for a confirm pair, say. The node under the remote disappears in that
 * instant, and without somewhere to send focus the D-pad stops responding.
 */
@Composable
fun MoveFocusWhen(trigger: Boolean, requester: FocusRequester) {
    if (!isTelevision) return
    LaunchedEffect(trigger) {
        if (trigger) runCatching { requester.requestFocus() }
    }
}

/** Marks the control [rememberFocusAnchor] points at. A no-op anywhere but a television. */
@Composable
fun Modifier.focusAnchor(requester: FocusRequester): Modifier =
    if (isTelevision) focusRequester(requester) else this

/**
 * Keeps a transient control out of the remote's path.
 *
 * A banner that clears itself after five seconds is *transient*, and a focusable node that
 * disappears takes the focus with it: Compose has nowhere to move it to, so the next D-pad
 * press goes nowhere and the remote reads as dead. That is exactly what happened on the live
 * view — navigation worked until an alert arrived, and afterwards there was no way down to
 * the Sound control.
 *
 * On a television such a control is not merely risky, it is pointless: the banner takes
 * itself away, so nothing needs pressing. It stays focusable everywhere else, where a
 * keyboard user has no auto-dismiss to rely on.
 */
@Composable
fun Modifier.skipDpadFocus(): Modifier =
    if (isTelevision) focusProperties { canFocus = false } else this

/**
 * Puts the remote somewhere sensible the moment a screen appears.
 *
 * Compose gives a fresh screen no focus at all until something is pressed, and on a TV the
 * first press of the D-pad then lands on whatever the focus search happens to find first —
 * frequently nothing, which reads as a frozen remote. Every screen names its own first
 * target instead.
 *
 * Only on a television. On a phone this would pop the keyboard's focus ring onto a card
 * nobody touched, and on desktop it would steal focus from a window the user is typing in.
 */
@Composable
fun Modifier.initialFocus(): Modifier {
    if (!isTelevision) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        // The node has to exist before it can hold focus; requesting it in the same frame as
        // the first composition throws.
        runCatching { requester.requestFocus() }
    }
    return this.focusRequester(requester)
}

/** 3dp reads from a sofa; the 1.5dp card border does not. */
private val FOCUS_RING = 3.dp

/**
 * How far in the ring sits, in stroke widths. Two, so its outer edge is a full stroke clear
 * of the element's own edge — far enough that a clipping ancestor cannot shave it, close
 * enough to still read as belonging to the element rather than floating inside it.
 */
private const val INSET_STROKES = 2f

private const val FOCUS_MILLIS = 120
