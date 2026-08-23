package com.hazemafaneh.babymonitorpro.qr

/**
 * A small QR encoder — byte mode, error-correction level L, versions 1 to 5.
 *
 * That range covers every pairing URI the app produces (`bmpro://255.255.255.255:65535` is
 * 30 bytes; version 5 holds 108), and staying inside single-block versions keeps the
 * Reed-Solomon step free of interleaving. Longer input returns null rather than a wrong code.
 *
 * Written by hand rather than pulled in as a dependency: the app needs one 40-byte code on
 * one screen, and this keeps the four targets on identical code.
 */
object QrEncoder {

    /** Square matrix of modules; true means dark. */
    class Matrix(val size: Int) {
        private val modules = BooleanArray(size * size)
        private val reserved = BooleanArray(size * size)

        operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
        operator fun set(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
        }

        fun reserve(x: Int, y: Int) {
            reserved[y * size + x] = true
        }

        fun isReserved(x: Int, y: Int): Boolean = reserved[y * size + x]
    }

    fun encode(content: String): Matrix? {
        val data = content.encodeToByteArray()
        val version = versionFor(data.size) ?: return null
        val capacity = DATA_CODEWORDS[version - 1]
        val ecCount = EC_CODEWORDS[version - 1]

        val codewords = buildCodewords(data, capacity)
        val ecc = reedSolomon(codewords, ecCount)
        val payload = codewords + ecc

        val size = 17 + version * 4
        val matrix = Matrix(size)
        matrix.drawFunctionPatterns(version)
        matrix.placeData(payload)

        val mask = (0..7).minBy { candidate ->
            val trial = matrix.copyWithMask(candidate, version)
            trial.penalty()
        }
        val masked = matrix.copyWithMask(mask, version)
        return masked
    }

    private fun versionFor(byteCount: Int): Int? =
        DATA_CODEWORDS.indexOfFirst { byteCount + HEADER_CODEWORDS <= it }
            .takeIf { it >= 0 }
            ?.plus(1)

    private fun buildCodewords(data: ByteArray, capacity: Int): IntArray {
        val bits = BitBuffer()
        bits.append(MODE_BYTE, 4)
        bits.append(data.size, 8)
        for (byte in data) bits.append(byte.toInt() and 0xFF, 8)

        val capacityBits = capacity * 8
        repeat(minOf(4, capacityBits - bits.size)) { bits.append(0, 1) }
        while (bits.size % 8 != 0) bits.append(0, 1)

        val codewords = bits.toCodewords(capacity)
        var index = bits.size / 8
        var padAlternate = true
        while (index < capacity) {
            codewords[index] = if (padAlternate) PAD_A else PAD_B
            padAlternate = !padAlternate
            index++
        }
        return codewords
    }

    // --- Reed-Solomon over GF(256), primitive polynomial 0x11D ---

    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var value = 1
        for (i in 0 until 255) {
            expTable[i] = value
            logTable[value] = i
            value = value shl 1
            if (value >= 256) value = value xor 0x11D
        }
        for (i in 255 until 512) expTable[i] = expTable[i - 255]
    }

    private fun multiply(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else expTable[logTable[a] + logTable[b]]

    /** Coefficients of the product of (x - a^i), stored lowest power first. */
    private fun generatorPolynomial(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor multiply(poly[j], expTable[i])
                next[j + 1] = next[j + 1] xor poly[j]
            }
            poly = next
        }
        return poly
    }

    private fun reedSolomon(data: IntArray, ecCount: Int): IntArray {
        val generator = generatorPolynomial(ecCount)
        val remainder = IntArray(ecCount)
        for (codeword in data) {
            val factor = codeword xor remainder[0]
            for (i in 0 until ecCount - 1) remainder[i] = remainder[i + 1]
            remainder[ecCount - 1] = 0
            // The remainder runs highest power first, the generator lowest power first,
            // and the monic leading term is implicit — hence the reversed index.
            for (i in 0 until ecCount) {
                remainder[i] = remainder[i] xor multiply(generator[ecCount - 1 - i], factor)
            }
        }
        return remainder
    }

    // --- Module placement ---

    private fun Matrix.drawFunctionPatterns(version: Int) {
        drawFinder(0, 0)
        drawFinder(size - 7, 0)
        drawFinder(0, size - 7)

        // Timing patterns.
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            this[i, 6] = dark; reserve(i, 6)
            this[6, i] = dark; reserve(6, i)
        }

        for (center in ALIGNMENT_CENTERS[version - 1]) {
            for (other in ALIGNMENT_CENTERS[version - 1]) {
                if (isNearFinder(center, other)) continue
                drawAlignment(center, other)
            }
        }

        // Dark module and the format-information areas, filled in with the mask.
        this[8, size - 8] = true
        reserve(8, size - 8)
        for (i in 0..8) {
            if (i != 6) { reserve(i, 8); reserve(8, i) }
        }
        for (i in 0..7) reserve(size - 1 - i, 8)
        for (i in 0..6) reserve(8, size - 1 - i)
    }

    private fun Matrix.isNearFinder(x: Int, y: Int): Boolean {
        val last = size - 7
        return (x <= 8 && y <= 8) || (x <= 8 && y >= last) || (x >= last && y <= 8)
    }

    private fun Matrix.drawFinder(originX: Int, originY: Int) {
        // -1 and 7 are the light separator ring around the 7x7 finder.
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = originX + dx
                val y = originY + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val insideFinder = dx in 0..6 && dy in 0..6
                val dark = insideFinder && (
                    dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4)
                    )
                this[x, y] = dark
                reserve(x, y)
            }
        }
    }

    private fun Matrix.drawAlignment(centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val x = centerX + dx
                val y = centerY + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val distance = maxOf(if (dx < 0) -dx else dx, if (dy < 0) -dy else dy)
                this[x, y] = distance != 1
                reserve(x, y)
            }
        }
    }

    private fun Matrix.placeData(payload: IntArray) {
        var bitIndex = 0
        var column = size - 1
        var upward = true

        while (column > 0) {
            if (column == 6) column-- // The vertical timing pattern occupies column 6.
            val rows = if (upward) (size - 1) downTo 0 else 0 until size
            for (row in rows) {
                for (offset in 0..1) {
                    val x = column - offset
                    if (isReserved(x, row)) continue
                    val bit = if (bitIndex < payload.size * 8) {
                        val codeword = payload[bitIndex / 8]
                        (codeword shr (7 - bitIndex % 8)) and 1
                    } else {
                        0
                    }
                    this[x, row] = bit == 1
                    bitIndex++
                }
            }
            column -= 2
            upward = !upward
        }
    }

    private fun Matrix.copyWithMask(mask: Int, version: Int): Matrix {
        val result = Matrix(size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isReserved(x, y)) {
                    result[x, y] = this[x, y]
                    result.reserve(x, y)
                } else {
                    result[x, y] = this[x, y] xor maskApplies(mask, x, y)
                }
            }
        }
        result.drawFormatInfo(mask)
        return result
    }

    private fun maskApplies(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> ((y / 2) + (x / 3)) % 2 == 0
        5 -> (x * y) % 2 + (x * y) % 3 == 0
        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
        else -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
    }

    private fun Matrix.drawFormatInfo(mask: Int) {
        val bits = formatBits(mask)
        for (i in 0..14) {
            val dark = ((bits shr i) and 1) == 1
            // Copy 1, around the top-left finder.
            when {
                i < 6 -> this[8, i] = dark
                i == 6 -> this[8, 7] = dark
                i == 7 -> this[8, 8] = dark
                i == 8 -> this[7, 8] = dark
                else -> this[14 - i, 8] = dark
            }
            // Copy 2, split between the other two finders.
            if (i < 8) {
                this[size - 1 - i, 8] = dark
            } else {
                this[8, size - 15 + i] = dark
            }
        }
        this[8, size - 8] = true
    }

    /** BCH(15,5) with the standard 0x5412 mask; level L is `01`. */
    private fun formatBits(mask: Int): Int {
        val data = (EC_LEVEL_L shl 3) or mask
        var remainder = data
        repeat(10) {
            remainder = (remainder shl 1) xor ((remainder ushr 9) * FORMAT_GENERATOR)
        }
        return ((data shl 10) or remainder) xor FORMAT_MASK
    }

    // --- Mask scoring (ISO/IEC 18004 penalty rules) ---

    private fun Matrix.penalty(): Int {
        var score = 0

        // Rule 1: runs of five or more identical modules in a row or column.
        for (i in 0 until size) {
            score += runPenalty { j -> this[j, i] }
            score += runPenalty { j -> this[i, j] }
        }

        // Rule 2: 2x2 blocks of one colour.
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val first = this[x, y]
                if (first == this[x + 1, y] && first == this[x, y + 1] && first == this[x + 1, y + 1]) {
                    score += 3
                }
            }
        }

        // Rule 3: finder-like 1:1:3:1:1 sequences.
        for (y in 0 until size) {
            for (x in 0 until size - 10) {
                if (matchesFinderRun { i -> this[x + i, y] }) score += 40
                if (matchesFinderRun { i -> this[y, x + i] }) score += 40
            }
        }

        // Rule 4: deviation from an even split of dark and light.
        var dark = 0
        for (y in 0 until size) for (x in 0 until size) if (this[x, y]) dark++
        val percent = dark * 100 / (size * size)
        score += 10 * (((percent - 50) / 5).let { if (it < 0) -it else it })

        return score
    }

    private inline fun Matrix.runPenalty(module: (Int) -> Boolean): Int {
        var score = 0
        var run = 1
        for (i in 1 until size) {
            if (module(i) == module(i - 1)) {
                run++
            } else {
                if (run >= 5) score += 3 + (run - 5)
                run = 1
            }
        }
        if (run >= 5) score += 3 + (run - 5)
        return score
    }

    private inline fun matchesFinderRun(module: (Int) -> Boolean): Boolean {
        val pattern = booleanArrayOf(true, false, true, true, true, false, true)
        for (i in pattern.indices) if (module(i) != pattern[i]) return false
        val trailing = (7..10).all { !module(it) }
        return trailing
    }

    private class BitBuffer {
        private val bits = mutableListOf<Int>()
        val size: Int get() = bits.size

        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += (value shr i) and 1
        }

        fun toCodewords(capacity: Int): IntArray {
            val codewords = IntArray(capacity)
            for (i in bits.indices) {
                if (bits[i] == 1) codewords[i / 8] = codewords[i / 8] or (1 shl (7 - i % 8))
            }
            return codewords
        }
    }

    private const val MODE_BYTE = 0b0100
    private const val HEADER_CODEWORDS = 2 // mode + character count, rounded to bytes
    private const val PAD_A = 0xEC
    private const val PAD_B = 0x11
    private const val EC_LEVEL_L = 0b01
    private const val FORMAT_GENERATOR = 0b101_0011_0111
    private const val FORMAT_MASK = 0b101_0100_0001_0010

    /** Versions 1..5, error-correction level L, one block each. */
    private val DATA_CODEWORDS = intArrayOf(19, 34, 55, 80, 108)
    private val EC_CODEWORDS = intArrayOf(7, 10, 15, 20, 26)
    private val ALIGNMENT_CENTERS = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
    )
}
