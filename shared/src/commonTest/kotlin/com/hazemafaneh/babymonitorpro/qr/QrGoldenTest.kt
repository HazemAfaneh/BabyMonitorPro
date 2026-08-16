package com.hazemafaneh.babymonitorpro.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The structural tests in [QrEncoderTest] would happily pass on a code no scanner can
 * read — an early version of this encoder did exactly that, with the Reed-Solomon
 * generator polynomial indexed backwards.
 *
 * This grid was rendered to PNG and decoded by Apple's Vision framework (the decoder
 * behind the iOS camera), which read back the exact pairing URI. It is pinned here so a
 * regression in encoding, masking or module placement fails the build instead of shipping
 * a code that only looks like a QR code.
 */
class QrGoldenTest {

    @Test
    fun matchesAnExternallyVerifiedCode() {
        val matrix = assertNotNull(QrEncoder.encode(CONTENT))
        assertEquals(GOLDEN.size, matrix.size)

        for (y in GOLDEN.indices) {
            val row = (0 until matrix.size).joinToString("") { x -> if (matrix[x, y]) "#" else "." }
            assertEquals(GOLDEN[y], row, "row $y")
        }
    }

    private companion object {
        const val CONTENT = "bmpro://192.168.1.42:8080?pin=004215"

        val GOLDEN = listOf(
            "#######...#.##.#...#..#######",
            "#.....#.#.#..####.#...#.....#",
            "#.###.#.....##.##.#...#.###.#",
            "#.###.#.###.#.#######.#.###.#",
            "#.###.#..#.......#.#..#.###.#",
            "#.....#.##.##..##.#...#.....#",
            "#######.#.#.#.#.#.#.#.#######",
            ".........###..##..##.........",
            "#####.####.#...#######.#.#.#.",
            "..##......#.###....#..#.#...#",
            "...#..##..#..###..#..#....#..",
            "#....#.##...##....##...#.#.#.",
            "#.###.#.###.#.#####.##....##.",
            ".###.#.##........#.#..#.###.#",
            ".##.######.##..#.#.......##..",
            "#.#.#..#####..###...###.#....",
            "########..##...###..##.#.##.#",
            "##.#...#.##.###....#..#.#.###",
            "#...###.#.#..###..#..####.#..",
            "#.#.##.###..##.#..####.....##",
            "#.....#.###.#.####.######.##.",
            "........##.......##.#...#####",
            "#######.##.##..#..###.#.###..",
            "#.....#...##..##....#...#..#.",
            "#.###.#.#..#...###..#####.#..",
            "#.###.#.###.###..###.#.#.####",
            "#.###.#.#.#..#####...##.####.",
            "#.....#.#.#.##.##.###.###..#.",
            "#######.#...#.#.###..##.###..",
        )
    }
}
