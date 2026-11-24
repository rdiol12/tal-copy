// Pure-Kotlin stand-in for java.math.BigDecimal (arbitrary precision, base 10).
// Covers what the tax tables use: exact add/subtract/multiply, setScale with
// the JDK rounding modes, and plain-string output.
package compat.math

enum class RoundingMode { UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY }

class BigDecimal private constructor(
    private val negative: Boolean,
    /** Little-endian base-10 digits without leading zeros; empty means zero. */
    private val digits: IntArray,
    private val scaleValue: Int
) : Comparable<BigDecimal> {

    constructor(text: String) : this(parse(text))
    private constructor(other: BigDecimal) : this(other.negative, other.digits, other.scaleValue)

    fun scale(): Int = scaleValue
    fun signum(): Int = if (digits.isEmpty()) 0 else if (negative) -1 else 1
    fun precision(): Int = if (digits.isEmpty()) 1 else digits.size
    fun negate(): BigDecimal = make(!negative, digits, scaleValue)
    fun abs(): BigDecimal = make(false, digits, scaleValue)

    fun add(other: BigDecimal): BigDecimal {
        val scale = maxOf(scaleValue, other.scaleValue)
        val a = shifted(digits, scale - scaleValue)
        val b = shifted(other.digits, scale - other.scaleValue)
        if (negative == other.negative) return make(negative, addMag(a, b), scale)
        val c = compareMag(a, b)
        return when {
            c == 0 -> make(false, EMPTY, scale)
            c > 0 -> make(negative, subMag(a, b), scale)
            else -> make(other.negative, subMag(b, a), scale)
        }
    }
    fun subtract(other: BigDecimal): BigDecimal = add(other.negate())
    fun multiply(other: BigDecimal): BigDecimal =
        make(negative != other.negative, mulMag(digits, other.digits), scaleValue + other.scaleValue)

    /** Result keeps this number's scale, like the two-argument JDK overload. */
    fun divide(other: BigDecimal, mode: RoundingMode): BigDecimal = divide(other, scaleValue, mode)
    fun divide(other: BigDecimal, scale: Int, mode: RoundingMode): BigDecimal {
        if (other.digits.isEmpty()) throw ArithmeticException("Division by zero")
        // Compute (this / other) with two guard digits beyond the requested scale,
        // tracking any remainder as a sticky digit so rounding stays exact.
        val shift = scale + 2 - scaleValue + other.scaleValue
        var numerator = digits
        var denominator = other.digits
        if (shift >= 0) numerator = shifted(numerator, shift) else denominator = shifted(denominator, -shift)
        val (quotient, remainder) = divMag(numerator, denominator)
        var q = quotient
        if (remainder.isNotEmpty()) {
            // Make the guard digits non-zero-terminated so HALF_* and UP/DOWN decisions see the remainder.
            q = addMag(shifted(q, 1), intArrayOf(1))
            return make(negative != other.negative, q, scale + 3).setScale(scale, mode)
        }
        return make(negative != other.negative, q, scale + 2).setScale(scale, mode)
    }

    fun min(other: BigDecimal): BigDecimal = if (compareTo(other) <= 0) this else other
    fun max(other: BigDecimal): BigDecimal = if (compareTo(other) >= 0) this else other

    fun movePointLeft(n: Int): BigDecimal {
        if (n == 0) return this
        val moved = make(negative, digits, scaleValue + n)
        return if (moved.scaleValue < 0) moved.setScale(0, RoundingMode.UNNECESSARY) else moved
    }
    fun movePointRight(n: Int): BigDecimal {
        if (n == 0) return this
        val moved = make(negative, digits, scaleValue - n)
        return if (moved.scaleValue < 0) moved.setScale(0, RoundingMode.UNNECESSARY) else moved
    }

    fun setScale(newScale: Int): BigDecimal = setScale(newScale, RoundingMode.UNNECESSARY)
    fun setScale(newScale: Int, mode: RoundingMode): BigDecimal {
        if (newScale == scaleValue) return this
        if (newScale > scaleValue) return make(negative, shifted(digits, newScale - scaleValue), newScale)
        val drop = scaleValue - newScale
        if (digits.isEmpty()) return make(false, EMPTY, newScale)
        val kept = if (drop >= digits.size) EMPTY else digits.copyOfRange(drop, digits.size)
        // Classify the discarded fraction: 0 = exactly zero, 1 = below half, 2 = exactly half, 3 = above half.
        var anyBelow = false
        for (i in 0 until minOf(drop - 1, digits.size)) if (digits[i] != 0) { anyBelow = true; break }
        val first = if (drop - 1 < digits.size) digits[drop - 1] else 0
        val fraction = when {
            first == 0 && !anyBelow -> 0
            first < 5 -> 1
            first == 5 && !anyBelow -> 2
            else -> 3
        }
        val lastKeptOdd = kept.isNotEmpty() && kept[0] % 2 == 1
        val increment = when (mode) {
            RoundingMode.UNNECESSARY -> if (fraction != 0) throw ArithmeticException("Rounding necessary") else false
            RoundingMode.UP -> fraction != 0
            RoundingMode.DOWN -> false
            RoundingMode.CEILING -> fraction != 0 && !negative
            RoundingMode.FLOOR -> fraction != 0 && negative
            RoundingMode.HALF_UP -> fraction >= 2
            RoundingMode.HALF_DOWN -> fraction == 3
            RoundingMode.HALF_EVEN -> fraction == 3 || (fraction == 2 && lastKeptOdd)
        }
        return make(negative, if (increment) addMag(kept, intArrayOf(1)) else kept, newScale)
    }

    fun stripTrailingZeros(): BigDecimal {
        if (digits.isEmpty()) return ZERO
        var zeros = 0
        while (zeros < digits.size && digits[zeros] == 0) zeros++
        return if (zeros == 0) this else make(negative, digits.copyOfRange(zeros, digits.size), scaleValue - zeros)
    }

    fun toPlainString(): String {
        val sb = StringBuilder()
        if (negative && digits.isNotEmpty()) sb.append('-')
        val text = unscaledText()
        when {
            scaleValue <= 0 -> { sb.append(text); if (digits.isNotEmpty()) repeat(-scaleValue) { sb.append('0') } }
            text.length > scaleValue -> sb.append(text, 0, text.length - scaleValue).append('.').append(text, text.length - scaleValue, text.length)
            else -> { sb.append("0."); repeat(scaleValue - text.length) { sb.append('0') }; sb.append(text) }
        }
        return sb.toString()
    }

    /** Canonical JDK form: plain when scale >= 0 and the exponent is >= -6, otherwise scientific. */
    override fun toString(): String {
        val text = unscaledText()
        val adjusted = -scaleValue.toLong() + (text.length - 1)
        if (scaleValue >= 0 && adjusted >= -6) return toPlainString()
        val sb = StringBuilder()
        if (negative && digits.isNotEmpty()) sb.append('-')
        sb.append(text[0])
        if (text.length > 1) sb.append('.').append(text, 1, text.length)
        if (adjusted != 0L) { sb.append('E'); if (adjusted > 0) sb.append('+'); sb.append(adjusted) }
        return sb.toString()
    }

    fun toDouble(): Double = toString().toDouble()
    fun toLong(): Long = setScale(0, RoundingMode.DOWN).toPlainString().toLong()
    fun toInt(): Int = toLong().toInt()

    operator fun plus(other: BigDecimal): BigDecimal = add(other)
    operator fun minus(other: BigDecimal): BigDecimal = subtract(other)
    operator fun times(other: BigDecimal): BigDecimal = multiply(other)
    operator fun div(other: BigDecimal): BigDecimal = divide(other, RoundingMode.HALF_EVEN)
    operator fun unaryMinus(): BigDecimal = negate()

    override fun compareTo(other: BigDecimal): Int {
        val a = signum()
        val b = other.signum()
        if (a != b) return a.compareTo(b)
        if (a == 0) return 0
        val scale = maxOf(scaleValue, other.scaleValue)
        val c = compareMag(shifted(digits, scale - scaleValue), shifted(other.digits, scale - other.scaleValue))
        return if (negative) -c else c
    }
    /** Like the JDK: equal only when both value and scale match (2.0 != 2.00). */
    override fun equals(other: Any?): Boolean =
        other is BigDecimal && scaleValue == other.scaleValue && negative == other.negative && digits.contentEquals(other.digits)
    override fun hashCode(): Int = 31 * (digits.contentHashCode() * (if (negative) -1 else 1)) + scaleValue

    private fun unscaledText(): String {
        if (digits.isEmpty()) return "0"
        val sb = StringBuilder(digits.size)
        for (i in digits.indices.reversed()) sb.append(('0' + digits[i]))
        return sb.toString()
    }

    companion object {
        private val EMPTY = IntArray(0)
        val ZERO: BigDecimal = BigDecimal(false, EMPTY, 0)
        val ONE: BigDecimal = BigDecimal(false, intArrayOf(1), 0)
        val TEN: BigDecimal = BigDecimal(false, intArrayOf(0, 1), 0)

        fun valueOf(value: Long): BigDecimal = BigDecimal(value.toString())
        fun valueOf(unscaled: Long, scale: Int): BigDecimal = BigDecimal(unscaled.toString()).let { make(it.negative, it.digits, scale) }
        /** Uses the canonical string form of the double, exactly like BigDecimal.valueOf(double). */
        fun valueOf(value: Double): BigDecimal {
            if (value.isNaN() || value.isInfinite()) throw NumberFormatException("Infinite or NaN")
            return BigDecimal(value.toString())
        }

        private fun make(negative: Boolean, digits: IntArray, scale: Int): BigDecimal {
            var n = digits.size
            while (n > 0 && digits[n - 1] == 0) n--
            val trimmed = if (n == digits.size) digits else digits.copyOf(n)
            return BigDecimal(negative && n > 0, trimmed, scale)
        }

        private fun parse(text: String): BigDecimal {
            var i = 0
            val n = text.length
            if (n == 0) throw NumberFormatException("Empty string")
            var negative = false
            if (text[0] == '+' || text[0] == '-') { negative = text[0] == '-'; i++ }
            val mantissa = StringBuilder()
            var scale = 0
            var seenDot = false
            var seenDigit = false
            while (i < n) {
                val c = text[i]
                if (c in '0'..'9') { mantissa.append(c); seenDigit = true; if (seenDot) scale++ }
                else if (c == '.' && !seenDot) seenDot = true
                else break
                i++
            }
            if (!seenDigit) throw NumberFormatException("For input string: \"$text\"")
            if (i < n) {
                if (text[i] != 'e' && text[i] != 'E') throw NumberFormatException("For input string: \"$text\"")
                val exponent = text.substring(i + 1).let { if (it.startsWith("+")) it.substring(1) else it }.toIntOrNull()
                    ?: throw NumberFormatException("For input string: \"$text\"")
                scale -= exponent
            }
            val digits = IntArray(mantissa.length) { mantissa[mantissa.length - 1 - it] - '0' }
            return make(negative, digits, scale)
        }

        /** Multiplies by 10^n (n >= 0). */
        private fun shifted(digits: IntArray, n: Int): IntArray {
            if (n == 0 || digits.isEmpty()) return digits
            val out = IntArray(digits.size + n)
            digits.copyInto(out, n)
            return out
        }

        private fun compareMag(a: IntArray, b: IntArray): Int {
            var an = a.size; while (an > 0 && a[an - 1] == 0) an--
            var bn = b.size; while (bn > 0 && b[bn - 1] == 0) bn--
            if (an != bn) return an.compareTo(bn)
            for (i in an - 1 downTo 0) if (a[i] != b[i]) return a[i].compareTo(b[i])
            return 0
        }

        private fun addMag(a: IntArray, b: IntArray): IntArray {
            val out = IntArray(maxOf(a.size, b.size) + 1)
            var carry = 0
            for (i in out.indices) {
                val sum = (if (i < a.size) a[i] else 0) + (if (i < b.size) b[i] else 0) + carry
                out[i] = sum % 10
                carry = sum / 10
            }
            return out
        }

        /** a - b, requires a >= b. */
        private fun subMag(a: IntArray, b: IntArray): IntArray {
            val out = IntArray(a.size)
            var borrow = 0
            for (i in a.indices) {
                var diff = a[i] - borrow - (if (i < b.size) b[i] else 0)
                if (diff < 0) { diff += 10; borrow = 1 } else borrow = 0
                out[i] = diff
            }
            return out
        }

        private fun mulMag(a: IntArray, b: IntArray): IntArray {
            if (a.isEmpty() || b.isEmpty()) return EMPTY
            val out = IntArray(a.size + b.size)
            for (i in a.indices) {
                var carry = 0
                val ai = a[i]
                if (ai == 0) continue
                for (j in b.indices) {
                    val cur = out[i + j] + ai * b[j] + carry
                    out[i + j] = cur % 10
                    carry = cur / 10
                }
                var k = i + b.size
                while (carry > 0) {
                    val cur = out[k] + carry
                    out[k] = cur % 10
                    carry = cur / 10
                    k++
                }
            }
            return out
        }

        /** Schoolbook long division; returns quotient and remainder magnitudes. */
        private fun divMag(a: IntArray, b: IntArray): Pair<IntArray, IntArray> {
            val quotient = IntArray(a.size)
            var remainder = EMPTY
            for (i in a.size - 1 downTo 0) {
                // remainder = remainder * 10 + a[i]
                val next = IntArray(remainder.size + 1)
                remainder.copyInto(next, 1)
                next[0] = a[i]
                remainder = next
                var q = 0
                while (compareMag(remainder, b) >= 0) { remainder = subMag(remainder, b); q++ }
                quotient[i] = q
            }
            var n = remainder.size
            while (n > 0 && remainder[n - 1] == 0) n--
            return quotient to remainder.copyOf(n)
        }
    }
}
