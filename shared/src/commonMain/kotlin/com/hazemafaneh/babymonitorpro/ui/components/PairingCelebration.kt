package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one celebration in the app: a code scanned, a camera paired.
 *
 * It earns its 560ms because pairing is the single moment in this product where something
 * *succeeded* — every other state is a fact being reported, and most of them are being
 * reported to someone who is tired and would rather the app said nothing at all. This
 * happens once per device, in daylight, with both phones in hand.
 *
 * It runs once and is never repeated: a retry after a failed scan goes straight through.
 * And at night it does not run at all — [BmpTheme.motion] flattens it to zero, so a parent
 * pairing a second device at 3am gets the live view immediately instead of a light show.
 */
@Composable
fun PairingCelebration(
    cameraName: String,
    onDone: () -> Unit,
) {
    val motion = BmpTheme.motion
    val leaf = BmpTheme.tints.leaf

    // Three phases, started on a clock rather than chained, so the total is exactly the
    // 560ms the spec names no matter how long the spring takes to settle.
    val code = remember { Animatable(1f) }
    val disc = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (motion.celebrateMillis == 0) {
            onDone()
            return@LaunchedEffect
        }
        launch { code.animateTo(0f, tween(CODE_OUT_MILLIS)) }
        launch {
            delay(CODE_OUT_MILLIS.toLong())
            disc.animateTo(1f, spring(dampingRatio = DISC_DAMPING, stiffness = DISC_STIFFNESS))
        }
        launch {
            delay(TICK_START_MILLIS)
            tick.animateTo(1f, tween(TICK_ON_MILLIS))
        }
        delay(motion.celebrateMillis.toLong())
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(GROUND),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(FRAME), contentAlignment = Alignment.Center) {
                // The scanner's own frame stays put through the whole thing. It is the
                // thing the parent was aiming, and pulling it out from under them would
                // make a success look like a screen change.
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(FRAME_STROKE, ON_GROUND.copy(alpha = 0.35f), RoundedCornerShape(FRAME_RADIUS)),
                )

                if (code.value > 0f) {
                    Box(
                        Modifier
                            .padding(FRAME_INSET)
                            .fillMaxSize()
                            // Shrinks to 0.86 rather than to nothing: the code is being
                            // replaced, not dismissed, and a shape that collapses to a
                            // point reads as an error.
                            .scale(CODE_END_SCALE + (1f - CODE_END_SCALE) * code.value)
                            .alpha(code.value)
                            .background(CODE_PLATE, RoundedCornerShape(INNER_RADIUS)),
                    )
                }

                if (disc.value > 0f) {
                    Box(
                        Modifier
                            .padding(FRAME_INSET)
                            .fillMaxSize()
                            .scale(DISC_START_SCALE + (1f - DISC_START_SCALE) * disc.value)
                            .alpha(disc.value.coerceIn(0f, 1f))
                            .background(leaf.fill, RoundedCornerShape(INNER_RADIUS)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Tick(progress = tick.value, color = leaf.glyph)
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                text = "Paired",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = PAIRED_TEXT),
                                fontWeight = FontWeight.Bold,
                                color = leaf.glyph,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xxl))

            // The caption crossfades rather than cutting: the sentence that was there was
            // an instruction, and an instruction that vanishes the instant it is obeyed
            // leaves the parent unsure whether it was obeyed.
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Point at the code on the nursery phone",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = CAPTION_TEXT),
                    color = ON_GROUND,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(code.value),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(disc.value.coerceIn(0f, 1f)),
                ) {
                    Text(
                        text = cameraName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = NAME_TEXT),
                        fontWeight = FontWeight.Bold,
                        color = ON_GROUND,
                    )
                    Text(
                        text = "Opening the live view…",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = SUB_TEXT,
                        ),
                        color = leaf.border,
                    )
                }
            }
        }

        Text(
            text = "The code only contains an address on your WiFi",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = FOOTER_TEXT),
            color = ON_GROUND.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Space.xl),
        )
    }
}

/** A tick that draws itself, rather than one that appears already drawn. */
@Composable
private fun Tick(progress: Float, color: Color) {
    Canvas(Modifier.size(TICK_SIZE)) {
        if (progress <= 0f) return@Canvas
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.20f, w * 0.52f)
            lineTo(w * 0.42f, w * 0.74f)
            lineTo(w * 0.80f, w * 0.28f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress, drawn, true)
        drawStroke(drawn, color, w * 0.10f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    path: Path,
    color: Color,
    width: Float,
) {
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** The scanner's ground. Dark, because a camera preview is behind it. */
private val GROUND = Color(0xFF17181F)
private val ON_GROUND = Color(0xFFFFF8EF)
private val CODE_PLATE = Color(0xFFF4F1EA)

private val FRAME = 246.dp
private val FRAME_RADIUS = 32.dp
private val FRAME_STROKE = 3.dp
private val FRAME_INSET = 22.dp
private val INNER_RADIUS = 16.dp
private val TICK_SIZE = 56.dp
private val PAIRED_TEXT = 16.sp
private val CAPTION_TEXT = 15.sp
private val NAME_TEXT = 16.sp
private val SUB_TEXT = 12.sp
private val FOOTER_TEXT = 11.5.sp

private const val CODE_OUT_MILLIS = 140
private const val CODE_END_SCALE = 0.86f
private const val DISC_START_SCALE = 0.66f
private const val DISC_DAMPING = Spring.DampingRatioMediumBouncy
private const val DISC_STIFFNESS = Spring.StiffnessMediumLow
private const val TICK_START_MILLIS = 380L
private const val TICK_ON_MILLIS = 180
