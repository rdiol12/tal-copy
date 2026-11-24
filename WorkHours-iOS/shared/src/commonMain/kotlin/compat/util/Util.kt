// java.util / java.lang look-alikes: Locale, UUID, Currency, System.
package compat.util

import compat.time.platformCurrentTimeMillis
import kotlin.math.pow

class Locale(language: String, country: String = "", val script: String = "") {
    val language: String = language.lowercase()
    val country: String = country.uppercase()

    fun toLanguageTag(): String = buildString {
        append(if (language.isEmpty()) "und" else if (language == "iw") "he" else language)
        if (script.isNotEmpty()) append('-').append(script)
        if (country.isNotEmpty()) append('-').append(country)
    }
    fun getDisplayCountry(inLocale: Locale): String = platformCountryName(country, inLocale.toLanguageTag())
    val displayCountry: String get() = getDisplayCountry(getDefault())
    fun getDisplayLanguage(inLocale: Locale): String = platformLanguageName(toLanguageTag(), inLocale.toLanguageTag())

    override fun equals(other: Any?): Boolean =
        other is Locale && other.language == language && other.country == country && other.script == script
    override fun hashCode(): Int = (language.hashCode() * 31 + country.hashCode()) * 31 + script.hashCode()
    override fun toString(): String = buildString {
        append(language)
        if (country.isNotEmpty()) append('_').append(country)
        if (script.isNotEmpty()) append("_#").append(script)
    }

    class Builder {
        private var language = ""
        private var region = ""
        private var script = ""
        fun setLanguage(value: String): Builder { language = value; return this }
        fun setRegion(value: String): Builder { region = value; return this }
        fun setScript(value: String): Builder { script = value; return this }
        fun build(): Locale = Locale(language, region, script)
    }

    companion object {
        val ROOT: Locale = Locale("")
        val ENGLISH: Locale = Locale("en")
        val US: Locale = Locale("en", "US")
        val UK: Locale = Locale("en", "GB")
        val FRENCH: Locale = Locale("fr")
        val FRANCE: Locale = Locale("fr", "FR")
        val GERMAN: Locale = Locale("de")
        val GERMANY: Locale = Locale("de", "DE")
        val ITALIAN: Locale = Locale("it")
        val JAPANESE: Locale = Locale("ja")
        val CHINESE: Locale = Locale("zh")

        /** Overridable so the app can follow its own language setting. */
        var defaultOverride: Locale? = null

        fun getDefault(): Locale = defaultOverride ?: forLanguageTag(platformLanguageTag())

        fun forLanguageTag(tag: String): Locale {
            val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts[0] == "und") return ROOT
            var script = ""
            var country = ""
            for (part in parts.drop(1)) {
                if (part.length == 4 && part.all { it.isLetter() } && script.isEmpty()) script = part.lowercase().replaceFirstChar { it.uppercase() }
                else if ((part.length == 2 && part.all { it.isLetter() }) || (part.length == 3 && part.all { it.isDigit() })) { if (country.isEmpty()) country = part }
            }
            return Locale(parts[0], country, script)
        }
    }
}

class UUID private constructor(private val text: String) {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is UUID && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        private val FORMAT = Regex("[0-9a-fA-F]{1,8}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,12}")

        fun randomUUID(): UUID = UUID(kotlin.uuid.Uuid.random().toString())
        fun fromString(name: String): UUID {
            if (!FORMAT.matches(name)) throw IllegalArgumentException("Invalid UUID string: $name")
            val p = name.split('-')
            return UUID(listOf(p[0].padStart(8, '0'), p[1].padStart(4, '0'), p[2].padStart(4, '0'), p[3].padStart(4, '0'), p[4].padStart(12, '0')).joinToString("-").lowercase())
        }
    }
}

class Currency private constructor(val currencyCode: String) {
    /** ISO 4217 minor units for the currencies that are not two-decimal. */
    val defaultFractionDigits: Int get() = when (currencyCode) {
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF" -> 0
        "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3
        "CLF", "UYW" -> 4
        else -> 2
    }
    fun getSymbol(locale: Locale): String = platformCurrencySymbol(currencyCode, locale.toLanguageTag())
    val symbol: String get() = getSymbol(Locale.getDefault())
    override fun toString(): String = currencyCode

    companion object {
        fun getInstance(code: String): Currency {
            if (code.length != 3 || !code.all { it in 'A'..'Z' }) throw IllegalArgumentException("Invalid currency code: $code")
            return Currency(code)
        }
    }
}

object System {
    fun currentTimeMillis(): Long = platformCurrentTimeMillis()
    fun nanoTime(): Long = platformNanoTime()
    fun lineSeparator(): String = "\n"
}

/** Single-threaded stand-in for the JVM intrinsic; callers here run on the main thread. */
inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

// ---- Platform hooks ----
/** BCP 47 tag of the user's preferred language, e.g. "he-IL". */
expect fun platformLanguageTag(): String
expect fun platformCountryName(regionCode: String, languageTag: String): String
expect fun platformLanguageName(tag: String, languageTag: String): String
expect fun platformCurrencySymbol(currencyCode: String, languageTag: String): String
expect fun platformNanoTime(): Long

// ---- java.lang look-alikes that Kotlin only auto-imports on the JVM ----

object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
    fun floorMod(a: Int, b: Int): Int = a.mod(b)
    fun floorMod(a: Long, b: Long): Long = a.mod(b)
    fun floorMod(a: Long, b: Int): Int = a.mod(b)
    fun floorDiv(a: Int, b: Int): Int = a.floorDiv(b)
    fun floorDiv(a: Long, b: Long): Long = a.floorDiv(b)
    fun abs(a: Int): Int = kotlin.math.abs(a)
    fun abs(a: Long): Long = kotlin.math.abs(a)
    fun abs(a: Double): Double = kotlin.math.abs(a)
    fun max(a: Int, b: Int): Int = maxOf(a, b)
    fun max(a: Long, b: Long): Long = maxOf(a, b)
    fun max(a: Double, b: Double): Double = maxOf(a, b)
    fun min(a: Int, b: Int): Int = minOf(a, b)
    fun min(a: Long, b: Long): Long = minOf(a, b)
    fun min(a: Double, b: Double): Double = minOf(a, b)
    fun floor(a: Double): Double = kotlin.math.floor(a)
    fun ceil(a: Double): Double = kotlin.math.ceil(a)
    fun sqrt(a: Double): Double = kotlin.math.sqrt(a)
    fun pow(a: Double, b: Double): Double = powOf(a, b)
    /** Math.round: floor(x + 0.5), with NaN -> 0. */
    fun round(a: Double): Long = if (a.isNaN()) 0L else kotlin.math.floor(a + 0.5).toLong()
}

private fun powOf(base: Double, exponent: Double): Double {
    // kotlin.math.pow is an extension; kept out of the object so the names do not clash.
    return base.pow(exponent)
}

object Character {
    /** Numeric value of a digit in any script (e.g. Arabic-Indic), or -1. */
    fun digit(c: Char, radix: Int): Int = c.digitToIntOrNull(radix) ?: -1
    fun isDigit(c: Char): Boolean = c.isDigit()
    fun isLetter(c: Char): Boolean = c.isLetter()
    fun isLetterOrDigit(c: Char): Boolean = c.isLetterOrDigit()
    fun isWhitespace(c: Char): Boolean = c.isWhitespace()
}

/** Kotlin's toSortedMap is JVM-only; an insertion-ordered map sorted by key behaves the same for reads. */
fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): Map<K, V> {
    val result = LinkedHashMap<K, V>(size)
    for ((key, value) in entries.sortedBy { it.key }) result[key] = value
    return result
}
