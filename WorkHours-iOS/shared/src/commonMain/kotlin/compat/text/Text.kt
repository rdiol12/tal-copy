// String helpers that exist only on the JVM in the Kotlin standard library:
// String.format, Charsets, locale-aware case conversion, plus small
// java.text look-alikes (NumberFormat, Collator, Normalizer).
package compat.text

import compat.util.Currency
import compat.util.Locale

class Charset internal constructor(val name: String) {
    override fun toString(): String = name
}

object Charsets {
    val UTF_8: Charset = Charset("UTF-8")
}

fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray = encodeToByteArray()
fun ByteArray.toString(charset: Charset): String = decodeToString()
fun String(bytes: ByteArray, charset: Charset = Charsets.UTF_8): String = bytes.decodeToString()

fun String.lowercase(locale: Locale): String = lowercase()
fun String.uppercase(locale: Locale): String = uppercase()

fun String.format(vararg args: Any?): String = formatString(this, args)
fun String.format(locale: Locale?, vararg args: Any?): String = formatString(this, args)
fun String.Companion.format(format: String, vararg args: Any?): String = formatString(format, args)
fun String.Companion.format(locale: Locale?, format: String, vararg args: Any?): String = formatString(format, args)

private val SPECIFIER = Regex("""%(\d+\$)?([-#+ 0,(]*)(\d+)?(\.\d+)?([a-zA-Z%])""")

/**
 * java.util.Formatter subset: %s %S %d %x %X %f %e %b %c %n %% with flags
 * - 0 + , space, a width, a precision and explicit argument indexes (%1$s).
 * Output always uses ASCII digits, '.' as the decimal separator and ',' for grouping.
 */
internal fun formatString(format: String, args: Array<out Any?>): String {
    var ordinary = 0
    return SPECIFIER.replace(format) { match ->
        val (indexText, flags, widthText, precisionText, conversionText) = match.destructured
        val conversion = conversionText[0]
        if (conversion == '%') return@replace "%"
        if (conversion == 'n') return@replace "\n"
        val index = if (indexText.isNotEmpty()) indexText.dropLast(1).toInt() - 1 else ordinary++
        if (index !in args.indices) throw IllegalArgumentException("Format specifier '${match.value}' has no argument")
        val arg = args[index]
        val precision = if (precisionText.isEmpty()) -1 else precisionText.substring(1).toInt()
        val width = if (widthText.isEmpty()) 0 else widthText.toInt()
        val left = '-' in flags
        val zero = '0' in flags
        var body: String
        var numeric = false
        when (conversion) {
            's', 'S' -> {
                body = arg.toString()
                if (precision >= 0 && body.length > precision) body = body.substring(0, precision)
                if (conversion == 'S') body = body.uppercase()
            }
            'b', 'B' -> body = (if (arg is Boolean) arg else arg != null).toString()
            'c', 'C' -> body = when (arg) {
                is Char -> arg.toString()
                is Number -> arg.toInt().toChar().toString()
                else -> arg.toString()
            }
            'd' -> {
                numeric = true
                val value = when (arg) {
                    is Number -> arg.toLong()
                    else -> throw IllegalArgumentException("%d needs an integer argument, got $arg")
                }
                val digits = (if (value < 0) -(value + 1) else value).let { if (value < 0) (it.toULong() + 1UL).toString() else it.toString() }
                body = signed(value < 0, if (',' in flags) group(digits) else digits, flags)
            }
            'x', 'X' -> {
                body = when (arg) {
                    is Byte -> (arg.toInt() and 0xFF).toString(16)
                    is Short -> (arg.toInt() and 0xFFFF).toString(16)
                    is Int -> arg.toUInt().toString(16)
                    is Long -> arg.toULong().toString(16)
                    else -> throw IllegalArgumentException("%x needs an integer argument, got $arg")
                }
                if (conversion == 'X') body = body.uppercase()
                numeric = true
            }
            'f' -> {
                numeric = true
                val value = (arg as? Number)?.toDouble() ?: throw IllegalArgumentException("%f needs a number, got $arg")
                body = if (value.isNaN()) "NaN" else if (value.isInfinite()) (if (value < 0) "-Infinity" else "Infinity") else {
                    val fixed = fixedDecimal(if (value < 0) -value else value, if (precision < 0) 6 else precision)
                    val grouped = if (',' in flags) fixed.substringBefore('.').let { group(it) } + (if ('.' in fixed) "." + fixed.substringAfter('.') else "") else fixed
                    signed(value < 0 || (value == 0.0 && 1.0 / value < 0), grouped, flags)
                }
            }
            'e', 'E' -> {
                numeric = true
                val value = (arg as? Number)?.toDouble() ?: throw IllegalArgumentException("%e needs a number, got $arg")
                body = scientific(value, if (precision < 0) 6 else precision)
                if (conversion == 'E') body = body.uppercase()
            }
            else -> throw IllegalArgumentException("Unsupported format conversion '%$conversion'")
        }
        if (body.length >= width) body
        else if (left) body.padEnd(width)
        else if (zero && numeric) {
            val signLength = if (body.startsWith("-") || body.startsWith("+") || body.startsWith(" ")) 1 else 0
            body.substring(0, signLength) + body.substring(signLength).padStart(width - signLength, '0')
        } else body.padStart(width)
    }
}

private fun signed(negative: Boolean, digits: String, flags: String): String = when {
    negative -> if ('(' in flags) "($digits)" else "-$digits"
    '+' in flags -> "+$digits"
    ' ' in flags -> " $digits"
    else -> digits
}

private fun group(digits: String): String {
    val sb = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

/** Exact HALF_UP rounding of the shortest decimal form of the value, like java.util.Formatter. */
internal fun fixedDecimal(value: Double, decimals: Int): String =
    compat.math.BigDecimal.valueOf(value).setScale(decimals, compat.math.RoundingMode.HALF_UP).toPlainString()

private fun scientific(value: Double, precision: Int): String {
    if (value == 0.0) return fixedDecimal(0.0, precision) + "e+00"
    val negative = value < 0
    val exact = compat.math.BigDecimal.valueOf(if (negative) -value else value)
    val plain = exact.stripTrailingZeros()
    var exponent = plain.precision() - plain.scale() - 1
    var mantissa = exact.movePointLeft(exponent).setScale(precision, compat.math.RoundingMode.HALF_UP)
    if (mantissa.compareTo(compat.math.BigDecimal.TEN) >= 0) {
        exponent += 1
        mantissa = exact.movePointLeft(exponent).setScale(precision, compat.math.RoundingMode.HALF_UP)
    }
    val exp = (if (exponent < 0) "-" else "+") + (if (exponent < 0) -exponent else exponent).toString().padStart(2, '0')
    return (if (negative) "-" else "") + mantissa.toPlainString() + "e" + exp
}

/** Removes accents and other combining marks: "São" -> "Sao". */
fun stripDiacritics(value: String): String = platformStripDiacritics(value)

object Normalizer {
    enum class Form { NFC, NFD, NFKC, NFKD }
    fun normalize(value: CharSequence, form: Form): String = platformNormalize(value.toString(), form.name)
}

class Collator private constructor(private val locale: Locale) {
    fun compare(a: String, b: String): Int = platformCollate(a, b, locale.toLanguageTag())

    companion object {
        fun getInstance(locale: Locale): Collator = Collator(locale)
        fun getInstance(): Collator = Collator(Locale.getDefault())
    }
}

class NumberFormat private constructor(private val locale: Locale, private val style: Int) {
    var currency: Currency? = null
    var minimumFractionDigits: Int = if (style == CURRENCY) 2 else 0
    var maximumFractionDigits: Int = if (style == CURRENCY) 2 else 3
    var isGroupingUsed: Boolean = true

    fun format(value: Double): String = when (style) {
        CURRENCY -> platformFormatCurrency(value, (currency ?: Currency.getInstance("USD")).currencyCode,
            minimumFractionDigits, maximumFractionDigits, locale.toLanguageTag())
        else -> platformFormatNumber(value, minimumFractionDigits, maximumFractionDigits, isGroupingUsed, locale.toLanguageTag())
    }
    fun format(value: Long): String = format(value.toDouble())

    companion object {
        private const val NUMBER = 0
        private const val CURRENCY = 1
        fun getCurrencyInstance(locale: Locale): NumberFormat = NumberFormat(locale, CURRENCY)
        fun getNumberInstance(locale: Locale): NumberFormat = NumberFormat(locale, NUMBER)
        fun getInstance(locale: Locale): NumberFormat = NumberFormat(locale, NUMBER)
    }
}

// ---- Platform hooks ----
expect fun platformStripDiacritics(value: String): String
expect fun platformNormalize(value: String, form: String): String
expect fun platformCollate(a: String, b: String, languageTag: String): Int
expect fun platformFormatCurrency(value: Double, currencyCode: String, minFraction: Int, maxFraction: Int, languageTag: String): String
expect fun platformFormatNumber(value: Double, minFraction: Int, maxFraction: Int, grouping: Boolean, languageTag: String): String
