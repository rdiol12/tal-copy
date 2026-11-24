package compat.time.format

import compat.time.DateTimeParseException
import compat.time.LocalDate
import compat.time.LocalDateTime
import compat.time.LocalTime
import compat.time.platformMonthName
import compat.time.platformWeekdayName
import compat.util.Locale

enum class TextStyle(internal val width: Int, internal val standalone: Boolean) {
    FULL(0, false), FULL_STANDALONE(0, true),
    SHORT(1, false), SHORT_STANDALONE(1, true),
    NARROW(2, false), NARROW_STANDALONE(2, true)
}

/**
 * Supports the pattern letters the app uses: y M L d E H h m s a, quoted
 * literals and plain punctuation. Digits are always ASCII, as on Android.
 */
class DateTimeFormatter private constructor(private val pattern: String, private val locale: Locale) {
    private sealed class Part {
        class Literal(val text: String) : Part()
        class Field(val letter: Char, val count: Int) : Part()
    }

    private val parts: List<Part> = compile(pattern)

    fun withLocale(locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale)

    fun format(date: LocalDate): String = render(date, null)
    fun format(time: LocalTime): String = render(null, time)
    fun format(dateTime: LocalDateTime): String = render(dateTime.toLocalDate(), dateTime.toLocalTime())

    private fun render(date: LocalDate?, time: LocalTime?): String {
        val tag = locale.toLanguageTag()
        val out = StringBuilder()
        for (part in parts) {
            when (part) {
                is Part.Literal -> out.append(part.text)
                is Part.Field -> {
                    val n = part.count
                    when (part.letter) {
                        'y', 'u' -> {
                            val d = date ?: unsupported(part.letter)
                            if (n == 2) out.append((d.year % 100).let { if (it < 0) -it else it }.toString().padStart(2, '0'))
                            else out.append(d.year.toString().padStart(n, '0'))
                        }
                        'M', 'L' -> {
                            val d = date ?: unsupported(part.letter)
                            val standalone = part.letter == 'L'
                            when {
                                n <= 2 -> out.append(d.monthValue.toString().padStart(n, '0'))
                                n == 3 -> out.append(platformMonthName(d.monthValue, 1, standalone, tag))
                                n == 4 -> out.append(platformMonthName(d.monthValue, 0, standalone, tag))
                                else -> out.append(platformMonthName(d.monthValue, 2, standalone, tag))
                            }
                        }
                        'd' -> out.append((date ?: unsupported('d')).dayOfMonth.toString().padStart(n, '0'))
                        'E' -> {
                            val d = date ?: unsupported('E')
                            val width = if (n <= 3) 1 else if (n == 4) 0 else 2
                            out.append(platformWeekdayName(d.dayOfWeek.value, width, false, tag))
                        }
                        'H' -> out.append((time ?: unsupported('H')).hour.toString().padStart(n, '0'))
                        'h' -> {
                            val h = (time ?: unsupported('h')).hour % 12
                            out.append((if (h == 0) 12 else h).toString().padStart(n, '0'))
                        }
                        'm' -> out.append((time ?: unsupported('m')).minute.toString().padStart(n, '0'))
                        's' -> out.append((time ?: unsupported('s')).second.toString().padStart(n, '0'))
                        'a' -> out.append(if ((time ?: unsupported('a')).hour < 12) "AM" else "PM")
                        else -> throw IllegalArgumentException("Unsupported pattern letter: ${part.letter}")
                    }
                }
            }
        }
        return out.toString()
    }

    private fun unsupported(letter: Char): Nothing =
        throw compat.time.DateTimeException("Unsupported field for pattern letter '$letter'")

    /** Parses numeric-only patterns such as dd/MM/yyyy. */
    internal fun parseDate(text: CharSequence): LocalDate {
        var pos = 0
        var year: Int? = null
        var month: Int? = null
        var day: Int? = null
        fun fail(): Nothing = throw DateTimeParseException("Text '$text' could not be parsed at index $pos", text, pos)
        for ((index, part) in parts.withIndex()) {
            when (part) {
                is Part.Literal -> {
                    if (!text.startsWith(part.text, pos)) fail()
                    pos += part.text.length
                }
                is Part.Field -> {
                    val adjacent = parts.getOrNull(index + 1) is Part.Field
                    val isYear = part.letter == 'y' || part.letter == 'u'
                    val min = if (part.count == 1) 1 else part.count
                    val max = when {
                        isYear && part.count == 2 -> 2
                        isYear -> if (adjacent) maxOf(part.count, 4) else 9
                        part.count == 1 && !adjacent -> 2
                        else -> part.count
                    }
                    var end = pos
                    while (end < text.length && end - pos < max && text[end] in '0'..'9') end++
                    if (end - pos < min) fail()
                    val value = text.substring(pos, end).toInt()
                    pos = end
                    when (part.letter) {
                        'y', 'u' -> year = if (part.count == 2) 2000 + value else value
                        'M', 'L' -> { if (part.count > 2) fail(); month = value }
                        'd' -> day = value
                        else -> fail()
                    }
                }
            }
        }
        if (pos != text.length || year == null || month == null || day == null) fail()
        try {
            return LocalDate.of(year, month, day)
        } catch (e: Exception) {
            throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0)
        }
    }

    override fun toString(): String = pattern

    companion object {
        val ISO_LOCAL_DATE: DateTimeFormatter = ofPattern("yyyy-MM-dd", Locale.ROOT)

        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern, Locale.getDefault())
        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale)

        private fun compile(pattern: String): List<Part> {
            val parts = ArrayList<Part>()
            val literal = StringBuilder()
            fun flush() { if (literal.isNotEmpty()) { parts.add(Part.Literal(literal.toString())); literal.clear() } }
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\'' -> {
                        if (i + 1 < pattern.length && pattern[i + 1] == '\'') { literal.append('\''); i += 2 }
                        else {
                            i++
                            while (i < pattern.length) {
                                if (pattern[i] == '\'') {
                                    if (i + 1 < pattern.length && pattern[i + 1] == '\'') { literal.append('\''); i += 2; continue }
                                    break
                                }
                                literal.append(pattern[i]); i++
                            }
                            if (i >= pattern.length) throw IllegalArgumentException("Pattern ends with an incomplete string literal: $pattern")
                            i++
                        }
                    }
                    c in 'a'..'z' || c in 'A'..'Z' -> {
                        var j = i
                        while (j < pattern.length && pattern[j] == c) j++
                        flush()
                        parts.add(Part.Field(c, j - i))
                        i = j
                    }
                    else -> { literal.append(c); i++ }
                }
            }
            flush()
            return parts
        }
    }
}
