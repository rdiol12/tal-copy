package compat.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private fun style(width: Int, standalone: Boolean): TextStyle = when (width) {
    0 -> if (standalone) TextStyle.FULL_STANDALONE else TextStyle.FULL
    1 -> if (standalone) TextStyle.SHORT_STANDALONE else TextStyle.SHORT
    else -> if (standalone) TextStyle.NARROW_STANDALONE else TextStyle.NARROW
}

actual fun platformZoneOffsetSeconds(zoneId: String, epochSecond: Long): Int =
    ZoneId.of(zoneId).rules.getOffset(Instant.ofEpochSecond(epochSecond)).totalSeconds
actual fun platformSystemZoneId(): String = ZoneId.systemDefault().id
actual fun platformCurrentTimeMillis(): Long = java.lang.System.currentTimeMillis()
actual fun platformMonthName(month: Int, width: Int, standalone: Boolean, languageTag: String): String =
    java.time.Month.of(month).getDisplayName(style(width, standalone), Locale.forLanguageTag(languageTag))
actual fun platformWeekdayName(day: Int, width: Int, standalone: Boolean, languageTag: String): String =
    java.time.DayOfWeek.of(day).getDisplayName(style(width, standalone), Locale.forLanguageTag(languageTag))
