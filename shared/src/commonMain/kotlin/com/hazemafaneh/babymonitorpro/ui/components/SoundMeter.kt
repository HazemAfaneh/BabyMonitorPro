package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazemafaneh.babymonitorpro.detect.SoundDetector
import kotlin.math.sqrt
import com.hazemafaneh.babymonitorpro.ui.theme.BmpTheme

/**
 * What the room sounds like, right now.
 *
 * Fourteen bars, oldest on the left, driven by the RMS value the sound detector already
 * computes for every audio chunk — so this costs no new capture path and nothing extra
 * measured. Bars that cleared the sensitivity threshold take marigold; the rest take the
 * joy accent, which is exactly the reading a parent needs while setting that threshold:
 * *these* are the noises that would have woken you.
 *
 * It sits directly above the slider for the same reason. A sensitivity control set against
 * a number is a guess; set against the room it is a decision.
 *
 * This is the only thing in the app that animates at night. Every bar interpolates over
 * [BmpMotion.meterMillis] toward its new height, because fourteen bars snapping between
 * frames of an RMS buffer is static — a parent cannot tell a quiet room from a noisy one by
 * looking at it. Interpolated, a quiet room breathes.
 */
@Composable
fun SoundMeter(
    level: Float,
    sensitivity: Int,
    modifier: Modifier = Modifier,
    height: Dp = METER_HEIGHT,
) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }

    // Keyed on the level so every published chunk shifts the history along once, and a
    // recomposition for any other reason does not.
    LaunchedEffect(level) {
        history.removeAt(0)
        history.add(level)
    }

    val threshold = SoundDetector.thresholdFor(sensitivity)
    val tints = BmpTheme.tints
    val loud = BmpTheme.semantic.statusLive
    val quiet = tints.joy

    Row(
        modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (index in 0 until BARS) {
            val reading = history[index]
            Bar(
                fraction = barHeight(reading),
                color = if (reading >= threshold) loud else quiet,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier) {
    val height by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(BmpTheme.motion.meterMillis),
        label = "meterBar",
    )
    // A full-height column that the bar grows up inside, so every bar shares one baseline
    // and a rising level never re-lays-out the row.
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(height)
                .background(color, RoundedCornerShape(BAR_RADIUS)),
        )
    }
}

/**
 * Fourteen. Enough that a rising room reads as a shape rather than a jump, few enough that
 * each bar is wide enough to see on a phone.
 */
private const val BARS = 14

/**
 * How tall a bar stands for a given RMS reading.
 *
 * Square-rooted, and against a ceiling a quarter of the old one. Measured on a phone in a
 * normal room, the level sits between 0.0005 and 0.04 — against the previous linear scale to
 * 0.4 that is a fraction between 0.001 and 0.1, all of it below [MIN_BAR], so all fourteen
 * bars sat on the floor and the meter looked broken rather than quiet. The square root is
 * what loudness does perceptually: it spends the height where the readings actually are.
 */
private fun barHeight(level: Float): Float =
    sqrt((level / DISPLAY_CEILING).coerceIn(0f, 1f)).coerceIn(MIN_BAR, 1f)

/**
 * The level a full-height bar means. Far below 1.0: a nursery at cot distance never comes
 * close to full scale, and a meter scaled to the theoretical maximum is flat all night.
 */
private const val DISPLAY_CEILING = 0.25f

/**
 * A silent room still shows fourteen bars.
 *
 * Not a hairline either. At the floor the meter has to read as *quiet* — a shape a parent
 * recognises as the room being still — and a row of one-pixel marks reads as a component
 * that failed to load.
 */
private const val MIN_BAR = 0.14f

private val METER_HEIGHT = 44.dp
private val BAR_GAP = 3.dp
private val BAR_RADIUS = 2.dp
