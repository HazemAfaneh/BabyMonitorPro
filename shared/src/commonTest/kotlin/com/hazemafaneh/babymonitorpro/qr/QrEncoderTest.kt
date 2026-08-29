package com.hazemafaneh.babymonitorpro.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrEncoderTest {

    @Test
    fun picksTheSmallestVersionThatFits() {
        // Version 1 holds 19 data codewords; 2 go to the header.
        assertEquals(21, QrEncoder.encode("a".repeat(17))?.size)
        assertEquals(25, QrEncoder.encode("a".repeat(18))?.size)
        assertEquals(37, QrEncoder.encode("a".repeat(100))?.size)
    }

    @Test
    fun refusesInputLongerThanVersionFive() {
        assertNull(QrEncoder.encode("a".repeat(200)))
    }

    @Test
    fun drawsTheThreeFinderPatterns() {
        val matrix = assertNotNull(QrEncoder.encode("bmpro://192.168.1.42:8080"))
        val last = matrix.size - 7

        for ((originX, originY) in listOf(0 to 0, last to 0, 0 to last)) {
            for (i in 0..6) {
                assertTrue(matrix[originX + i, originY], "top edge at $originX,$originY")
                assertTrue(matrix[originX + i, originY + 6], "bottom edge at $originX,$originY")
                assertTrue(matrix[originX, originY + i], "left edge at $originX,$originY")
                assertTrue(matrix[originX + 6, originY + i], "right edge at $originX,$originY")
            }
            // The ring immediately inside the border is light.
            assertTrue(!matrix[originX + 1, originY + 1])
            assertTrue(matrix[originX + 3, originY + 3])
        }
    }

    @Test
    fun drawsTimingPatternsAndTheDarkModule() {
        val matrix = assertNotNull(QrEncoder.encode("bmpro://10.0.0.2:8080"))

        for (i in 8 until matrix.size - 8) {
            assertEquals(i % 2 == 0, matrix[i, 6], "horizontal timing at $i")
            assertEquals(i % 2 == 0, matrix[6, i], "vertical timing at $i")
        }
        assertTrue(matrix[8, matrix.size - 8], "the dark module is always set")
    }

    @Test
    fun theSameInputAlwaysProducesTheSameCode() {
        val content = "bmpro://192.168.1.42:8080"
        val first = assertNotNull(QrEncoder.encode(content))
        val second = assertNotNull(QrEncoder.encode(content))

        for (y in 0 until first.size) {
            for (x in 0 until first.size) {
                assertEquals(first[x, y], second[x, y], "module $x,$y")
            }
        }
    }
}
