package compat.time

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName

private val zones = HashMap<String, NSTimeZone>()

private fun zone(id: String): NSTimeZone = zones.getOrPut(id) {
    NSTimeZone.timeZoneWithName(id) ?: NSTimeZone.timeZoneWithName("UTC") ?: NSTimeZone.localTimeZone
}

actual fun platformZoneOffsetSeconds(zoneId: String, epochSecond: Long): Int =
    zone(zoneId).secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochSecond.toDouble())).toInt()

actual fun platformSystemZoneId(): String = NSTimeZone.localTimeZone.name

actual fun platformCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

/** Month and weekday names per language: [format/standalone][width] -> names. */
private class Names(tag: String) {
    private val formatter = NSDateFormatter().apply { locale = NSLocale(localeIdentifier = tag) }
    private fun strings(list: List<*>?): List<String> = list?.map { it as? String ?: "" } ?: emptyList()

    val months: List<List<String>> = listOf(
        strings(formatter.monthSymbols), strings(formatter.shortMonthSymbols), strings(formatter.veryShortMonthSymbols),
        strings(formatter.standaloneMonthSymbols), strings(formatter.shortStandaloneMonthSymbols), strings(formatter.veryShortStandaloneMonthSymbols))
    // Foundation lists weekdays starting with Sunday.
    val weekdays: List<List<String>> = listOf(
        strings(formatter.weekdaySymbols), strings(formatter.shortWeekdaySymbols), strings(formatter.veryShortWeekdaySymbols),
        strings(formatter.standaloneWeekdaySymbols), strings(formatter.shortStandaloneWeekdaySymbols), strings(formatter.veryShortStandaloneWeekdaySymbols))
}

private val names = HashMap<String, Names>()
private fun namesFor(tag: String): Names = names.getOrPut(tag) { Names(if (tag.isEmpty() || tag == "und") "en_US_POSIX" else tag) }

actual fun platformMonthName(month: Int, width: Int, standalone: Boolean, languageTag: String): String =
    namesFor(languageTag).months[(if (standalone) 3 else 0) + width.coerceIn(0, 2)].getOrNull(month - 1) ?: month.toString()

actual fun platformWeekdayName(day: Int, width: Int, standalone: Boolean, languageTag: String): String =
    namesFor(languageTag).weekdays[(if (standalone) 3 else 0) + width.coerceIn(0, 2)].getOrNull(day % 7) ?: day.toString()
