package com.hazemafaneh.babymonitorpro.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's own line icons. Six of them, and no more.
 *
 * Each is built from circles and a single path, drawn to a 24dp square at 1.8dp of stroke,
 * so they survive being rendered at 20dp on a phone held at arm's length in a dark room.
 * That constraint is why there are circles rather than detail: a teddy with a stitched nose
 * is a smudge at 20dp.
 *
 * They are never used *instead of* a label — every one of them sits beside a word. A
 * half-asleep parent reads the word; the icon is what makes the screen feel like it was
 * made for their kid rather than for a network appliance. And none of them appears on an
 * error: a fault is the one moment this app has nothing charming to say.
 *
 * Drawn in black and tinted at the call site — `Icon` lays a `ColorFilter` over the whole
 * vector, so the colours baked in here never reach the screen.
 */
object BmpIcons {

    /**
     * The app itself, and this camera.
     *
     * App mark, camera identity, the discovered-camera rows, the live-view bar — the four
     * places a parent needs to know *which device* they are looking at.
     */
    val Teddy: ImageVector by lazy {
        bmpIcon("Teddy") {
            strokePath {
                circle(12f, 14f, 6f)
                circle(6.5f, 7.5f, 2.6f)
                circle(17.5f, 7.5f, 2.6f)
                // The smile. A shallow arc rather than a curve with control points,
                // because at 20dp the difference is invisible and the arc cannot go wrong.
                moveTo(10.6f, 16.4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.8f, 0f)
            }
            // Solid, and small enough that stroking them would fill them in anyway.
            solidPath {
                circle(10f, 13f, 0.9f)
                circle(14f, 13f, 0.9f)
            }
        }
    }

    /**
     * Viewer, and night.
     *
     * The outline form is the night switch while night is off; [MoonFilled] is the same
     * crescent solid, which is what "on" looks like. Two forms of one shape rather than two
     * icons: the parent is toggling a thing, not choosing between two.
     */
    val Moon: ImageVector by lazy { moon(filled = false) }

    /** Night is on. */
    val MoonFilled: ImageVector by lazy { moon(filled = true) }

    /** Pairing — the "pair another device" card. Three toes and a heel. */
    val Footprint: ImageVector by lazy {
        bmpIcon("Footprint") {
            strokePath {
                moveTo(12f, 20.5f)
                curveToRelative(-3.6f, 0f, -6f, -1.6f, -6f, -4f)
                curveToRelative(0f, -1.7f, 1f, -2.6f, 1f, -4.4f)
                curveTo(7f, 8.4f, 9.2f, 6f, 12f, 6f)
                reflectiveCurveToRelative(5f, 2.4f, 5f, 6.1f)
                curveToRelative(0f, 1.8f, 1f, 2.7f, 1f, 4.4f)
                curveToRelative(0f, 2.4f, -2.4f, 4f, -6f, 4f)
                close()
                circle(7.2f, 4.4f, 1.5f)
                circle(11f, 3.2f, 1.5f)
                circle(14.9f, 3.6f, 1.5f)
            }
        }
    }

    /** Sound and alerts — the microphone row, the sound meter, the alert banner. */
    val Rattle: ImageVector by lazy {
        bmpIcon("Rattle") {
            strokePath {
                circle(9f, 15f, 5f)
                moveTo(12.5f, 11.5f)
                lineToRelative(5f, -5f)
                circle(19f, 5f, 2f)
                // The two ticks on the bell. They are what stop it reading as a magnifier.
                moveTo(7f, 13.5f)
                lineToRelative(1f, 1f)
                moveTo(11f, 13f)
                lineToRelative(1f, 1f)
            }
        }
    }

    /** Privacy — every "on your WiFi" line, and nowhere else. */
    val Shield: ImageVector by lazy {
        bmpIcon("Shield") {
            strokePath {
                moveTo(12f, 21f)
                curveToRelative(-4.5f, -2.6f, -7f, -5.6f, -7f, -9.4f)
                verticalLineTo(6.2f)
                lineTo(12f, 3.4f)
                lineToRelative(7f, 2.8f)
                verticalLineToRelative(5.4f)
                curveToRelative(0f, 3.8f, -2.5f, 6.8f, -7f, 9.4f)
                close()
                moveTo(9.3f, 11.9f)
                lineToRelative(2f, 2f)
                lineToRelative(3.4f, -3.6f)
            }
        }
    }

    /**
     * A camera. The role picker's Camera card, and the lens row in camera settings.
     *
     * Not a baby icon and not from Material either: the design reference draws its own
     * camcorder, which is the reason this app still has no icon dependency. The functional
     * glyphs the spec names as "Material core" — `Close`, `ContentCopy`, `VolumeUp` and the
     * rest — are not on the classpath, and the app uses words for those instead.
     */
    val Camera: ImageVector by lazy {
        bmpIcon("Camera") {
            strokePath {
                roundRect(3f, 7f, 13f, 11f, 3f)
                // The lens barrel, pointing right — the wedge is what separates a camera
                // from a rectangle with a dot in it.
                moveTo(16f, 12.5f)
                lineToRelative(4.5f, -3f)
                verticalLineToRelative(7f)
                close()
                circle(9.5f, 12.5f, 2.4f)
            }
        }
    }

    /**
     * Discovery — the find-a-camera empty state.
     *
     * The design boards label this one "Nursery", which is what it means; it is a house
     * because that is what a house looks like at 24dp.
     */
    val House: ImageVector by lazy {
        bmpIcon("House") {
            strokePath {
                moveTo(4f, 19f)
                verticalLineTo(9.5f)
                lineTo(12f, 4f)
                lineToRelative(8f, 5.5f)
                verticalLineTo(19f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5f, 1.5f)
                horizontalLineToRelative(-13f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 19f)
                close()
                // The door, left open at the top — a closed rectangle here reads as a window.
                moveTo(9.5f, 20.5f)
                verticalLineToRelative(-5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(5f)
            }
        }
    }

    /**
     * Settings — the home screen's second tab.
     *
     * Three sliders rather than a gear. A gear at 18dp on a television seen from three metres
     * is a grey disc, and this set has no teeth small enough to survive the stroke width; two
     * lines and a knob each read at any size. It also happens to be what the screen contains.
     */
    val Sliders: ImageVector by lazy {
        bmpIcon("Sliders") {
            strokePath {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 17f)
                horizontalLineTo(20f)
            }
            // The knobs, solid so they stay distinct from the rails they sit on. Staggered,
            // because three knobs in a column reads as a single vertical line.
            solidPath {
                circle(9f, 7f, 2.1f)
                circle(15f, 12f, 2.1f)
                circle(7.5f, 17f, 2.1f)
            }
        }
    }
}

private fun moon(filled: Boolean): ImageVector =
    bmpIcon(if (filled) "MoonFilled" else "Moon") {
        // The crescent, filled when night is on, so the switch reads as a state at a glance
        // rather than as a second, differently-drawn icon.
        if (filled) solidPath { crescent() } else strokePath { crescent() }
        // The spark beside it is always solid — at 1.8dp of stroke it would close into a
        // blob anyway, and it is what stops the crescent reading as a loading spinner.
        solidPath { spark() }
    }

private fun PathBuilder.crescent() {
    moveTo(19f, 14.5f)
    arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.5f, 5f)
    arcToRelative(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9.5f, 9.5f)
    close()
}

private fun PathBuilder.spark() {
    moveTo(6.8f, 4.6f)
    lineToRelative(0.7f, 1.9f)
    lineToRelative(1.9f, 0.7f)
    lineToRelative(-1.9f, 0.7f)
    lineToRelative(-0.7f, 1.9f)
    lineToRelative(-0.7f, -1.9f)
    lineTo(4.2f, 7.2f)
    lineToRelative(1.9f, -0.7f)
    close()
}

/**
 * A circle, as a subpath.
 *
 * Two half-arcs, which is the same construction an `<circle>` element compiles down to.
 * Written out here because [PathBuilder] has no circle of its own and four of the six icons
 * are mostly circles.
 */
private fun PathBuilder.circle(centreX: Float, centreY: Float, radius: Float) {
    moveTo(centreX - radius, centreY)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = false, radius * 2, 0f)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = false, -radius * 2, 0f)
    close()
}

/** A rounded rectangle, as a subpath — the shape an `<rect rx>` element compiles to. */
private fun PathBuilder.roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
    moveTo(x + radius, y)
    horizontalLineTo(x + width - radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x + width, y + radius)
    verticalLineTo(y + height - radius)
    arcTo(
        radius, radius, 0f,
        isMoreThanHalf = false, isPositiveArc = true,
        x + width - radius, y + height,
    )
    horizontalLineTo(x + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x, y + height - radius)
    verticalLineTo(y + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x + radius, y)
    close()
}

/** 24dp square — the one geometry every icon in the set is drawn to. */
private fun bmpIcon(name: String, paths: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE.dp,
        defaultHeight = ICON_SIZE.dp,
        viewportWidth = ICON_SIZE,
        viewportHeight = ICON_SIZE,
    ).apply(paths).build()

/**
 * A drawn line.
 *
 * Round caps, miter joins — the same pair the design reference uses. Round joins were the
 * obvious guess for a nursery app and they are wrong here: they round off the shield's
 * point and the roof's apex, which are the two features that make those two shapes
 * readable at all at 20dp.
 */
private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        pathBuilder = block,
    )
}

/** A filled shape — the details that would close up if they were stroked. */
private fun ImageVector.Builder.solidPath(block: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathBuilder = block)
}

private const val ICON_SIZE = 24f

/** 1.8dp at 24dp. Thin enough to read as a line drawing, thick enough to survive at 20dp. */
private const val STROKE_WIDTH = 1.8f
