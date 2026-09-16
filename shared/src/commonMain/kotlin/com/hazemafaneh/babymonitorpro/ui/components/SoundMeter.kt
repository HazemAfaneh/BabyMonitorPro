package com.hazemafaneh.babymonitorpro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
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
import com.hazemafaneh.babymonitorpro.detect.MotionDetector
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

/**
 * Where the room is, right now, on a sensitivity slider's own scale.
 *
 * The slider asks for a percentage and the room produces an RMS reading, and nothing on the
 * screen connected the two — so "alert me at 50%" was a number a parent could only set by
 * trial and error, at night, on a sleeping baby. This runs the live measurement back through
 * the detector's own curve (see [SoundDetector.sensitivityFor]) and draws it as a line at the
 * matching position: *this* noise would raise an alert at 62% and above.
 *
 * Two marks, because a room is not one number:
 *
 * - **The live line** tracks the current chunk. It moves constantly, which is the point — a
 *   still line would be a room nobody is in.
 * - **The peak line** holds the loudest reading of the last few seconds and decays back down,
 *   because the noise worth setting a threshold against — a cough, a door, one cry — is over
 *   long before a parent has looked up at the screen.
 *
 * Drawn against the same track geometry the slider uses, so the two read as one control.
 */
@Composable
fun LevelMarker(
    /** Where the room is now, 0..100 on the same scale the slider shows. */
    levelPercent: Float,
    /** Where the slider is. The room alerts when it reaches or passes this. */
    thresholdPercent: Int,
    modifier: Modifier = Modifier,
) {
    val live = levelPercent / 100f
    var peak by remember { mutableStateOf(0f) }

    // Keyed on the level so the peak follows the audio rather than the frame rate: every
    // published chunk either raises the peak or lets it fall a little.
    LaunchedEffect(levelPercent) {
        peak = maxOf(live, peak - PEAK_DECAY)
    }

    val livePosition by animateFloatAsState(
        targetValue = live.coerceIn(0f, 1f),
        animationSpec = tween(BmpTheme.motion.meterMillis),
        label = "soundNow",
    )
    val peakPosition by animateFloatAsState(
        targetValue = peak.coerceIn(0f, 1f),
        animationSpec = tween(BmpTheme.motion.meterMillis),
        label = "soundPeak",
    )

    val loud = BmpTheme.semantic.statusLive
    val quiet = BmpTheme.tints.joy
    // Marigold once the room has reached the line, which is the moment an alert fires.
    val armed = levelPercent >= thresholdPercent
    val trackColour = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(MARKER_HEIGHT)) {
        // The thumb's radius is dead space at both ends of a Material slider, so the marks
        // line up with the track rather than with the edge of the card.
        val inset = THUMB_INSET.toPx()
        val usable = (size.width - inset * 2).coerceAtLeast(1f)
        val baseline = size.height - BASELINE_GAP.toPx()

        drawLine(
            color = trackColour,
            start = Offset(inset, baseline),
            end = Offset(inset + usable, baseline),
            strokeWidth = TRACK_STROKE.toPx(),
        )

        // Peak first, so the live mark draws over it when they meet.
        val peakX = inset + usable * peakPosition
        drawLine(
            color = quiet,
            start = Offset(peakX, baseline),
            end = Offset(peakX, baseline - PEAK_HEIGHT.toPx()),
            strokeWidth = MARK_STROKE.toPx(),
        )

        val liveX = inset + usable * livePosition
        drawLine(
            color = if (armed) loud else quiet,
            start = Offset(liveX, baseline),
            end = Offset(liveX, baseline - LIVE_HEIGHT.toPx()),
            strokeWidth = MARK_STROKE.toPx(),
        )
    }
}

/** How fast the peak mark falls back, per audio chunk — about three seconds from full. */
private const val PEAK_DECAY = 0.035f

private val MARKER_HEIGHT = 22.dp
private val BASELINE_GAP = 2.dp
private val LIVE_HEIGHT = 18.dp
private val PEAK_HEIGHT = 11.dp
private val TRACK_STROKE = 1.5.dp
private val MARK_STROKE = 2.5.dp

/** Material's slider leaves a thumb radius of dead track at each end. */
private val THUMB_INSET = 10.dp

/**
 * The room's loudness as a percentage, on the scale the slider shows.
 *
 * **Louder is a bigger number**, which is the whole point of this conversion. The detectors
 * think in *sensitivity* — how little it takes to set them off — and that runs the other way:
 * a more sensitive camera has a lower threshold. Showing a parent a number that goes *down*
 * as the room gets louder is how you get "the fan reads 60, so I set 70 to ignore it" and an
 * app that then alerts on the fan constantly. So the reading and the control are both stated
 * in loudness, and the inversion happens once, here, where it can be seen.
 */
fun soundLevelPercent(level: Float): Float = SoundDetector.levelPercent(level)

/** The same conversion for movement: more of the picture changing is a bigger number. */
fun motionLevelPercent(ratio: Float): Float = MotionDetector.levelPercent(ratio)

/** The threshold as the detector wants it, from the number the parent set. */
fun sensitivityForThreshold(thresholdPercent: Int): Int = 100 - thresholdPercent.coerceIn(0, 100)

/** And back again, for showing a stored setting on the slider. */
fun thresholdForSensitivity(sensitivity: Int): Int = 100 - sensitivity.coerceIn(0, 100)

/** The sound flavour: the room's loudness against the sound threshold. */
@Composable
fun SoundLevelMarker(level: Float, thresholdPercent: Int, modifier: Modifier = Modifier) {
    LevelMarker(soundLevelPercent(level), thresholdPercent, modifier)
}

/** The movement flavour: how much of the picture is changing, against the motion threshold. */
@Composable
fun MotionLevelMarker(level: Float, thresholdPercent: Int, modifier: Modifier = Modifier) {
    LevelMarker(motionLevelPercent(level), thresholdPercent, modifier)
}

