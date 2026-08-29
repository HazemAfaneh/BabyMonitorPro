package com.hazemafaneh.babymonitorpro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A card's decorative fill and the border that goes with it.
 *
 * The pair travels together because a tint is only legible as a card when both halves
 * agree — a sky fill under a lemon border reads as a rendering fault, not as a card.
 */
@Immutable
data class Tint(
    val fill: Color,
    val border: Color,
    /** The icon on a white plate sitting on this tint — the tint's own hue, at full chroma. */
    val glyph: Color,
    /** Title text on this tint. */
    val content: Color,
    /** Description and metadata on this tint. */
    val contentMuted: Color,
)

/**
 * Decoration, and only decoration.
 *
 * **A tint may never carry state.** State is [BmpSemantic]'s job, and the whole reason that
 * layer exists is that a colour which sometimes means "camera role" and sometimes means
 * "healthy" means neither. These four exist so a parent can tell the pairing card from the
 * privacy card at a glance without ever learning what sky or lemon *stands for* — they
 * carry no information, which is precisely what makes them safe to spend on four cards.
 *
 * At night all four collapse to `surface` with an `outlineVariant` border: the tints are
 * the first thing to go, because a coloured fill in a dark room is emitted light spent on
 * decoration, and the border alone is enough structure to tell two cards apart.
 */
@Immutable
data class BmpTints(
    /** Camera role, QR and browser cards. */
    val sky: Tint,
    /** Sound, discovered rows, the event strip. */
    val lemon: Tint,
    /** Viewer role, moon surfaces. */
    val grape: Tint,
    /** Privacy card, the paired disc. */
    val leaf: Tint,
    /**
     * The joy accent — the app mark's plate, and the sound meter's bars below the
     * sensitivity mark. Never a state: it is bright by day precisely because it is the one
     * colour in the app that is allowed to mean nothing at all.
     */
    val joy: Color,
)

/**
 * Day. Four tints that tell cards apart, and one accent that tells you nothing.
 *
 * Each tint carries its own text pair rather than borrowing `onSurface`. The reason is
 * legibility of *family*, not contrast: neutral brown text on a sky card reads as text that
 * landed on the wrong card, while text a few steps darker than the fill reads as belonging
 * to it. Every pair still clears 7:1 on its own fill.
 */
internal val DayTints = BmpTints(
    sky = Tint(
        fill = Color(0xFFEAF4FB),
        border = Color(0xFFC3DFF1),
        glyph = Color(0xFF2C6B96),
        content = Color(0xFF1D3A50),
        contentMuted = Color(0xFF4A6377),
    ),
    lemon = Tint(
        fill = Color(0xFFFFF3CE),
        border = Color(0xFFF2DFA3),
        glyph = Color(0xFF8A6E22),
        content = Color(0xFF4A3A10),
        contentMuted = Color(0xFF6E6152),
    ),
    grape = Tint(
        fill = Color(0xFFF3EBFC),
        border = Color(0xFFDFCDF4),
        glyph = Color(0xFF7048B0),
        content = Color(0xFF3B2757),
        contentMuted = Color(0xFF5B4A72),
    ),
    leaf = Tint(
        fill = Color(0xFFE6F5EC),
        border = Color(0xFFC4E6D3),
        glyph = Color(0xFF1A6B4A),
        content = Color(0xFF14503A),
        contentMuted = Color(0xFF3F7A62),
    ),
    joy = Color(0xFFFFD84D),
)

/**
 * Night. All four resolve to `surface` over an `outlineVariant` border, and the joy accent
 * drops to the dim marigold the meter uses below its sensitivity mark.
 *
 * The values are repeated here rather than read off the scheme so this stays a plain
 * constant: the collapse is a design decision, not a lucky consequence of two tokens
 * happening to match.
 */
internal val NightTints = run {
    val collapsed = Tint(
        fill = Color(0xFF0C0E14),
        border = Color(0xFF23262F),
        glyph = Color(0xFFB9BECE),
        content = Color(0xFFB9BECE),
        contentMuted = Color(0xFF7B8092),
    )
    BmpTints(
        sky = collapsed,
        lemon = collapsed,
        grape = collapsed,
        leaf = collapsed,
        joy = Color(0xFF6E5228),
    )
}

val LocalBmpTints = staticCompositionLocalOf { DayTints }
