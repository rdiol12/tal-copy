// Pure-Kotlin stand-ins for the parts of java.time that the app uses.
// Same class and method names as the JDK, so the app sources only need their
// imports rewritten (java.time.* -> compat.time.*).
package compat.time

import compat.time.format.DateTimeFormatter
import compat.time.format.TextStyle
import compat.util.Locale

open class DateTimeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private fun floorDiv(a: Long, b: Long): Long = a.floorDiv(b)
private fun floorMod(a: Long, b: Long): Long = a.mod(b)

internal fun isLeap(year: Int): Boolean = (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0)
internal fun monthLength(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeap(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** Days since 1970-01-01 for a proleptic Gregorian date. */
internal fun epochDayOf(year: Int, month: Int, day: Int): Long {
    val y = year.toLong() - if (month <= 2) 1 else 0
    val era = floorDiv(y, 400)
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    val value: Int get() = ordinal + 1
    fun plus(days: Long): DayOfWeek = entries[floorMod(ordinal + days, 7).toInt()]
    fun minus(days: Long): DayOfWeek = plus(-(days % 7))
    fun getDisplayName(style: TextStyle, locale: Locale): String =
        platformWeekdayName(value, style.width, style.standalone, locale.toLanguageTag())

    companion object {
        fun of(value: Int): DayOfWeek {
            if (value !in 1..7) throw DateTimeException("Invalid value for DayOfWeek: $value")
            return entries[value - 1]
        }
    }
}

enum class Month {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    val value: Int get() = ordinal + 1
    fun plus(months: Long): Month = entries[floorMod(ordinal + months, 12).toInt()]
    fun minus(months: Long): Month = plus(-(months % 12))
    fun length(leapYear: Boolean): Int = monthLength(if (leapYear) 2024 else 2023, value)
    fun getDisplayName(style: TextStyle, locale: Locale): String =
        platformMonthName(value, style.width, style.standalone, locale.toLanguageTag())

    companion object {
        fun of(value: Int): Month {
            if (value !in 1..12) throw DateTimeException("Invalid value for MonthOfYear: $value")
            return entries[value - 1]
        }
    }
}

class ZoneId private constructor(val id: String) {
    override fun toString(): String = id
    override fun equals(other: Any?): Boolean = other is ZoneId && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun of(id: String): ZoneId = ZoneId(id)
        fun systemDefault(): ZoneId = ZoneId(platformSystemZoneId())
    }
}

class LocalDate private constructor(val year: Int, val monthValue: Int, val dayOfMonth: Int) : Comparable<LocalDate> {
    val month: Month get() = Month.of(monthValue)
    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[floorMod(toEpochDay() + 3, 7).toInt()]
    val dayOfYear: Int get() = (toEpochDay() - epochDayOf(year, 1, 1)).toInt() + 1
    val isLeapYear: Boolean get() = isLeap(year)

    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun lengthOfYear(): Int = if (isLeap(year)) 366 else 365
    fun toEpochDay(): Long = epochDayOf(year, monthValue, dayOfMonth)

    fun plusDays(days: Long): LocalDate = if (days == 0L) this else ofEpochDay(toEpochDay() + days)
    fun minusDays(days: Long): LocalDate = plusDays(-days)
    fun plusWeeks(weeks: Long): LocalDate = plusDays(weeks * 7)
    fun minusWeeks(weeks: Long): LocalDate = plusDays(-weeks * 7)
    fun plusMonths(months: Long): LocalDate {
        if (months == 0L) return this
        val total = year * 12L + (monthValue - 1) + months
        val y = floorDiv(total, 12).toInt()
        val m = floorMod(total, 12).toInt() + 1
        return LocalDate(y, m, minOf(dayOfMonth, monthLength(y, m)))
    }
    fun minusMonths(months: Long): LocalDate = plusMonths(-months)
    fun plusYears(years: Long): LocalDate = if (years == 0L) this else withYear((year + years).toInt())
    fun minusYears(years: Long): LocalDate = plusYears(-years)

    fun withDayOfMonth(day: Int): LocalDate = if (day == dayOfMonth) this else of(year, monthValue, day)
    fun withMonth(month: Int): LocalDate {
        if (month !in 1..12) throw DateTimeException("Invalid value for MonthOfYear: $month")
        return LocalDate(year, month, minOf(dayOfMonth, monthLength(year, month)))
    }
    fun withYear(year: Int): LocalDate = LocalDate(year, monthValue, minOf(dayOfMonth, monthLength(year, monthValue)))
    fun withDayOfYear(day: Int): LocalDate = ofEpochDay(epochDayOf(year, 1, 1) + day - 1)

    fun atStartOfDay(): LocalDateTime = LocalDateTime.of(this, LocalTime.MIDNIGHT)
    fun atStartOfDay(zone: ZoneId): ZonedDateTime = atStartOfDay().atZone(zone)
    fun atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime.of(this, LocalTime.of(hour, minute))
    fun atTime(hour: Int, minute: Int, second: Int): LocalDateTime = LocalDateTime.of(this, LocalTime.of(hour, minute, second))
    fun atTime(time: LocalTime): LocalDateTime = LocalDateTime.of(this, time)

    fun isBefore(other: LocalDate): Boolean = compareTo(other) < 0
    fun isAfter(other: LocalDate): Boolean = compareTo(other) > 0
    fun isEqual(other: LocalDate): Boolean = compareTo(other) == 0
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun compareTo(other: LocalDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (monthValue != other.monthValue) return monthValue.compareTo(other.monthValue)
        return dayOfMonth.compareTo(other.dayOfMonth)
    }
    override fun equals(other: Any?): Boolean = other is LocalDate && compareTo(other) == 0
    override fun hashCode(): Int = (year and -0x800) xor ((year shl 11) + (monthValue shl 6) + dayOfMonth)
    override fun toString(): String {
        val y = when {
            year in 0..9999 -> year.toString().padStart(4, '0')
            year < 0 -> "-" + (-year).toString().padStart(4, '0')
            else -> "+$year"
        }
        return y + "-" + monthValue.toString().padStart(2, '0') + "-" + dayOfMonth.toString().padStart(2, '0')
    }

    companion object {
        private val ISO = Regex("""([+-]?\d{4,9})-(\d{2})-(\d{2})""")

        fun of(year: Int, month: Int, day: Int): LocalDate {
            if (month !in 1..12) throw DateTimeException("Invalid value for MonthOfYear (valid values 1 - 12): $month")
            if (day < 1 || day > monthLength(year, month)) throw DateTimeException("Invalid date: $year-$month-$day")
            return LocalDate(year, month, day)
        }
        fun of(year: Int, month: Month, day: Int): LocalDate = of(year, month.value, day)
        fun ofEpochDay(epochDay: Long): LocalDate {
            val z = epochDay + 719468
            val era = floorDiv(z, 146097)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return LocalDate(y, m, d)
        }
        fun parse(text: CharSequence): LocalDate {
            val match = ISO.matchEntire(text) ?: throw DateTimeParseException("Text '$text' could not be parsed", text, 0)
            try {
                return of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            } catch (e: DateTimeParseException) {
                throw e
            } catch (e: Exception) {
                throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0)
            }
        }
        fun parse(text: CharSequence, formatter: DateTimeFormatter): LocalDate = formatter.parseDate(text)
        fun now(): LocalDate = LocalDateTime.now().toLocalDate()
        fun now(zone: ZoneId): LocalDate = LocalDateTime.now(zone).toLocalDate()
        fun from(temporal: LocalDateTime): LocalDate = temporal.toLocalDate()
    }
}

class LocalTime private constructor(val hour: Int, val minute: Int, val second: Int, val nano: Int) : Comparable<LocalTime> {
    fun toSecondOfDay(): Int = hour * 3600 + minute * 60 + second
    fun toNanoOfDay(): Long = toSecondOfDay() * 1_000_000_000L + nano
    fun withHour(hour: Int): LocalTime = of(hour, minute, second, nano)
    fun withMinute(minute: Int): LocalTime = of(hour, minute, second, nano)
    fun withSecond(second: Int): LocalTime = of(hour, minute, second, nano)
    fun withNano(nano: Int): LocalTime = of(hour, minute, second, nano)
    fun plusSeconds(seconds: Long): LocalTime = ofNanoOfDay(floorMod(toNanoOfDay() + (seconds % 86400) * 1_000_000_000L, NANOS_PER_DAY))
    fun plusMinutes(minutes: Long): LocalTime = plusSeconds((minutes % 1440) * 60)
    fun plusHours(hours: Long): LocalTime = plusSeconds((hours % 24) * 3600)
    fun minusMinutes(minutes: Long): LocalTime = plusMinutes(-(minutes % 1440))
    fun minusHours(hours: Long): LocalTime = plusHours(-(hours % 24))
    fun isBefore(other: LocalTime): Boolean = compareTo(other) < 0
    fun isAfter(other: LocalTime): Boolean = compareTo(other) > 0
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun compareTo(other: LocalTime): Int = toNanoOfDay().compareTo(other.toNanoOfDay())
    override fun equals(other: Any?): Boolean = other is LocalTime && toNanoOfDay() == other.toNanoOfDay()
    override fun hashCode(): Int = toNanoOfDay().hashCode()
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(hour.toString().padStart(2, '0')).append(':').append(minute.toString().padStart(2, '0'))
        if (second > 0 || nano > 0) {
            sb.append(':').append(second.toString().padStart(2, '0'))
            if (nano > 0) {
                sb.append('.')
                when {
                    nano % 1_000_000 == 0 -> sb.append((nano / 1_000_000 + 1000).toString().substring(1))
                    nano % 1000 == 0 -> sb.append((nano / 1000 + 1_000_000).toString().substring(1))
                    else -> sb.append((nano + 1_000_000_000).toString().substring(1))
                }
            }
        }
        return sb.toString()
    }

    companion object {
        internal const val NANOS_PER_DAY = 86_400_000_000_000L
        val MIDNIGHT: LocalTime = LocalTime(0, 0, 0, 0)
        val MIN: LocalTime = MIDNIGHT
        val NOON: LocalTime = LocalTime(12, 0, 0, 0)
        private val ISO = Regex("""(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?""")

        fun of(hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalTime {
            if (hour !in 0..23) throw DateTimeException("Invalid value for HourOfDay (valid values 0 - 23): $hour")
            if (minute !in 0..59) throw DateTimeException("Invalid value for MinuteOfHour (valid values 0 - 59): $minute")
            if (second !in 0..59) throw DateTimeException("Invalid value for SecondOfMinute (valid values 0 - 59): $second")
            if (nano !in 0..999_999_999) throw DateTimeException("Invalid value for NanoOfSecond: $nano")
            return LocalTime(hour, minute, second, nano)
        }
        fun ofSecondOfDay(seconds: Long): LocalTime = ofNanoOfDay(seconds * 1_000_000_000L)
        fun ofNanoOfDay(nanos: Long): LocalTime {
            if (nanos !in 0 until NANOS_PER_DAY) throw DateTimeException("Invalid value for NanoOfDay: $nanos")
            val seconds = (nanos / 1_000_000_000L).toInt()
            return LocalTime(seconds / 3600, seconds / 60 % 60, seconds % 60, (nanos % 1_000_000_000L).toInt())
        }
        fun parse(text: CharSequence): LocalTime {
            val match = ISO.matchEntire(text) ?: throw DateTimeParseException("Text '$text' could not be parsed", text, 0)
            try {
                val g = match.groupValues
                return of(g[1].toInt(), g[2].toInt(), g[3].ifEmpty { "0" }.toInt(), g[4].padEnd(9, '0').ifEmpty { "0" }.toInt())
            } catch (e: DateTimeParseException) {
                throw e
            } catch (e: Exception) {
                throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0)
            }
        }
        fun now(): LocalTime = LocalDateTime.now().toLocalTime()
        fun now(zone: ZoneId): LocalTime = LocalDateTime.now(zone).toLocalTime()
    }
}

class LocalDateTime private constructor(private val date: LocalDate, private val time: LocalTime) : Comparable<LocalDateTime> {
    val year: Int get() = date.year
    val monthValue: Int get() = date.monthValue
    val month: Month get() = date.month
    val dayOfMonth: Int get() = date.dayOfMonth
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
    val dayOfYear: Int get() = date.dayOfYear
    val hour: Int get() = time.hour
    val minute: Int get() = time.minute
    val second: Int get() = time.second
    val nano: Int get() = time.nano

    fun toLocalDate(): LocalDate = date
    fun toLocalTime(): LocalTime = time

    fun withYear(year: Int): LocalDateTime = LocalDateTime(date.withYear(year), time)
    fun withMonth(month: Int): LocalDateTime = LocalDateTime(date.withMonth(month), time)
    fun withDayOfMonth(day: Int): LocalDateTime = LocalDateTime(date.withDayOfMonth(day), time)
    fun withHour(hour: Int): LocalDateTime = LocalDateTime(date, time.withHour(hour))
    fun withMinute(minute: Int): LocalDateTime = LocalDateTime(date, time.withMinute(minute))
    fun withSecond(second: Int): LocalDateTime = LocalDateTime(date, time.withSecond(second))
    fun withNano(nano: Int): LocalDateTime = LocalDateTime(date, time.withNano(nano))

    fun plusDays(days: Long): LocalDateTime = LocalDateTime(date.plusDays(days), time)
    fun minusDays(days: Long): LocalDateTime = plusDays(-days)
    fun plusWeeks(weeks: Long): LocalDateTime = plusDays(weeks * 7)
    fun minusWeeks(weeks: Long): LocalDateTime = plusDays(-weeks * 7)
    fun plusMonths(months: Long): LocalDateTime = LocalDateTime(date.plusMonths(months), time)
    fun minusMonths(months: Long): LocalDateTime = plusMonths(-months)
    fun plusYears(years: Long): LocalDateTime = LocalDateTime(date.plusYears(years), time)
    fun minusYears(years: Long): LocalDateTime = plusYears(-years)
    fun plusNanos(nanos: Long): LocalDateTime {
        if (nanos == 0L) return this
        val total = time.toNanoOfDay() + nanos % LocalTime.NANOS_PER_DAY
        val days = nanos / LocalTime.NANOS_PER_DAY + floorDiv(total, LocalTime.NANOS_PER_DAY)
        return LocalDateTime(date.plusDays(days), LocalTime.ofNanoOfDay(floorMod(total, LocalTime.NANOS_PER_DAY)))
    }
    fun plusSeconds(seconds: Long): LocalDateTime {
        if (seconds == 0L) return this
        val total = time.toSecondOfDay() + seconds % 86400
        val days = seconds / 86400 + floorDiv(total, 86400)
        return LocalDateTime(date.plusDays(days), LocalTime.ofNanoOfDay(floorMod(total, 86400) * 1_000_000_000L + time.nano))
    }
    fun minusSeconds(seconds: Long): LocalDateTime = plusSeconds(-seconds)
    fun plusMinutes(minutes: Long): LocalDateTime = plusSeconds(minutes * 60)
    fun minusMinutes(minutes: Long): LocalDateTime = plusSeconds(-minutes * 60)
    fun plusHours(hours: Long): LocalDateTime = plusSeconds(hours * 3600)
    fun minusHours(hours: Long): LocalDateTime = plusSeconds(-hours * 3600)

    /** Seconds since the epoch if this local date-time were in UTC. */
    internal fun toUtcEpochSecond(): Long = date.toEpochDay() * 86400 + time.toSecondOfDay()

    /** Same resolution rules as the JDK: gaps shift forward, overlaps take the earlier offset. */
    fun atZone(zone: ZoneId): ZonedDateTime {
        val local = toUtcEpochSecond()
        val before = platformZoneOffsetSeconds(zone.id, local - 86400)
        val after = platformZoneOffsetSeconds(zone.id, local + 86400)
        var best: Long? = null
        var bestOffset = 0
        for (offset in intArrayOf(before, after)) {
            val utc = local - offset
            if (platformZoneOffsetSeconds(zone.id, utc) == offset && (best == null || utc < best)) {
                best = utc
                bestOffset = offset
            }
        }
        if (best != null) return ZonedDateTime(this, zone, bestOffset)
        // Gap: no such local time. Use the offset before the transition, which moves the clock forward.
        val utc = local - before
        val offset = platformZoneOffsetSeconds(zone.id, utc)
        return ZonedDateTime(ofEpochSecond(utc + offset, time.nano), zone, offset)
    }

    fun isBefore(other: LocalDateTime): Boolean = compareTo(other) < 0
    fun isAfter(other: LocalDateTime): Boolean = compareTo(other) > 0
    fun isEqual(other: LocalDateTime): Boolean = compareTo(other) == 0
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun compareTo(other: LocalDateTime): Int {
        val c = date.compareTo(other.date)
        return if (c != 0) c else time.compareTo(other.time)
    }
    override fun equals(other: Any?): Boolean = other is LocalDateTime && date == other.date && time == other.time
    override fun hashCode(): Int = date.hashCode() xor time.hashCode()
    override fun toString(): String = "${date}T$time"

    companion object {
        private val ISO = Regex("""([+-]?\d{4,9}-\d{2}-\d{2})T(\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?)""")

        fun of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, day), LocalTime.of(hour, minute, second, nano))
        internal fun ofEpochSecond(utcSeconds: Long, nano: Int): LocalDateTime =
            LocalDateTime(LocalDate.ofEpochDay(floorDiv(utcSeconds, 86400)),
                LocalTime.ofNanoOfDay(floorMod(utcSeconds, 86400) * 1_000_000_000L + nano))
        fun parse(text: CharSequence): LocalDateTime {
            val match = ISO.matchEntire(text) ?: throw DateTimeParseException("Text '$text' could not be parsed", text, 0)
            return LocalDateTime(LocalDate.parse(match.groupValues[1]), LocalTime.parse(match.groupValues[2]))
        }
        fun now(): LocalDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDateTime = Instant.now().atZone(zone).toLocalDateTime()
    }
}

class ZonedDateTime internal constructor(private val dateTime: LocalDateTime, val zone: ZoneId, internal val offsetSeconds: Int) {
    val year: Int get() = dateTime.year
    val monthValue: Int get() = dateTime.monthValue
    val dayOfMonth: Int get() = dateTime.dayOfMonth
    val dayOfWeek: DayOfWeek get() = dateTime.dayOfWeek
    val hour: Int get() = dateTime.hour
    val minute: Int get() = dateTime.minute
    val second: Int get() = dateTime.second

    fun toLocalDateTime(): LocalDateTime = dateTime
    fun toLocalDate(): LocalDate = dateTime.toLocalDate()
    fun toLocalTime(): LocalTime = dateTime.toLocalTime()
    fun toEpochSecond(): Long = dateTime.toUtcEpochSecond() - offsetSeconds
    // Like the JDK: date-based units move the local date, time-based units move along the instant line.
    fun plusDays(days: Long): ZonedDateTime = dateTime.plusDays(days).atZone(zone)
    fun minusDays(days: Long): ZonedDateTime = plusDays(-days)
    fun plusMonths(months: Long): ZonedDateTime = dateTime.plusMonths(months).atZone(zone)
    fun minusMonths(months: Long): ZonedDateTime = plusMonths(-months)
    fun plusSeconds(seconds: Long): ZonedDateTime = Instant.ofEpochSecond(toEpochSecond() + seconds, dateTime.nano.toLong()).atZone(zone)
    fun minusSeconds(seconds: Long): ZonedDateTime = plusSeconds(-seconds)
    fun plusMinutes(minutes: Long): ZonedDateTime = plusSeconds(minutes * 60)
    fun minusMinutes(minutes: Long): ZonedDateTime = plusSeconds(-minutes * 60)
    fun plusHours(hours: Long): ZonedDateTime = plusSeconds(hours * 3600)
    fun minusHours(hours: Long): ZonedDateTime = plusSeconds(-hours * 3600)
    fun withZoneSameInstant(zone: ZoneId): ZonedDateTime = toInstant().atZone(zone)
    fun toInstant(): Instant = Instant.ofEpochSecond(toEpochSecond(), dateTime.nano.toLong())
    fun format(formatter: DateTimeFormatter): String = formatter.format(dateTime)
    override fun equals(other: Any?): Boolean =
        other is ZonedDateTime && dateTime == other.dateTime && zone == other.zone && offsetSeconds == other.offsetSeconds
    override fun hashCode(): Int = dateTime.hashCode() xor zone.hashCode() xor offsetSeconds
    override fun toString(): String {
        val abs = if (offsetSeconds < 0) -offsetSeconds else offsetSeconds
        val offset = if (offsetSeconds == 0) "Z" else (if (offsetSeconds < 0) "-" else "+") +
            (abs / 3600).toString().padStart(2, '0') + ":" + (abs / 60 % 60).toString().padStart(2, '0')
        return "$dateTime$offset[${zone.id}]"
    }

    companion object {
        fun now(zone: ZoneId): ZonedDateTime = Instant.now().atZone(zone)
        fun now(): ZonedDateTime = now(ZoneId.systemDefault())
    }
}

class Instant private constructor(val epochSecond: Long, val nano: Int) : Comparable<Instant> {
    fun toEpochMilli(): Long = epochSecond * 1000 + nano / 1_000_000
    fun atZone(zone: ZoneId): ZonedDateTime {
        val offset = platformZoneOffsetSeconds(zone.id, epochSecond)
        return ZonedDateTime(LocalDateTime.ofEpochSecond(epochSecond + offset, nano), zone, offset)
    }
    fun plusMillis(millis: Long): Instant = ofEpochMilli(toEpochMilli() + millis)
    fun plusSeconds(seconds: Long): Instant = Instant(epochSecond + seconds, nano)
    fun minusMillis(millis: Long): Instant = plusMillis(-millis)
    fun minusSeconds(seconds: Long): Instant = plusSeconds(-seconds)
    fun isBefore(other: Instant): Boolean = compareTo(other) < 0
    fun isAfter(other: Instant): Boolean = compareTo(other) > 0

    override fun compareTo(other: Instant): Int {
        val c = epochSecond.compareTo(other.epochSecond)
        return if (c != 0) c else nano.compareTo(other.nano)
    }
    override fun equals(other: Any?): Boolean = other is Instant && epochSecond == other.epochSecond && nano == other.nano
    override fun hashCode(): Int = epochSecond.hashCode() xor nano
    override fun toString(): String {
        val local = LocalDateTime.ofEpochSecond(epochSecond, nano)
        val time = local.toLocalTime()
        val text = if (time.second == 0 && time.nano == 0) "$time:00" else time.toString()
        return "${local.toLocalDate()}T${text}Z"
    }

    companion object {
        val EPOCH: Instant = Instant(0, 0)
        private val ISO = Regex("""([+-]?\d{4,9}-\d{2}-\d{2})T(\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?)(Z|z|[+-]\d{2}:\d{2})""")

        fun ofEpochMilli(millis: Long): Instant = Instant(floorDiv(millis, 1000), (floorMod(millis, 1000) * 1_000_000).toInt())
        fun ofEpochSecond(seconds: Long, nanoAdjustment: Long = 0): Instant =
            Instant(seconds + floorDiv(nanoAdjustment, 1_000_000_000L), floorMod(nanoAdjustment, 1_000_000_000L).toInt())
        fun now(): Instant = ofEpochMilli(platformCurrentTimeMillis())
        fun parse(text: CharSequence): Instant {
            val match = ISO.matchEntire(text) ?: throw DateTimeParseException("Text '$text' could not be parsed", text, 0)
            val local = LocalDateTime.of(LocalDate.parse(match.groupValues[1]), LocalTime.parse(match.groupValues[2]))
            val zone = match.groupValues[3]
            val offset = if (zone == "Z" || zone == "z") 0 else {
                val sign = if (zone[0] == '-') -1 else 1
                sign * (zone.substring(1, 3).toInt() * 3600 + zone.substring(4, 6).toInt() * 60)
            }
            return Instant(local.toUtcEpochSecond() - offset, local.nano)
        }
    }
}

class YearMonth private constructor(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    val month: Month get() = Month.of(monthValue)
    val isLeapYear: Boolean get() = isLeap(year)

    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun lengthOfYear(): Int = if (isLeap(year)) 366 else 365
    fun atDay(day: Int): LocalDate = LocalDate.of(year, monthValue, day)
    fun atEndOfMonth(): LocalDate = LocalDate.of(year, monthValue, lengthOfMonth())
    fun isValidDay(day: Int): Boolean = day in 1..lengthOfMonth()
    fun plusMonths(months: Long): YearMonth {
        if (months == 0L) return this
        val total = year * 12L + (monthValue - 1) + months
        return YearMonth(floorDiv(total, 12).toInt(), floorMod(total, 12).toInt() + 1)
    }
    fun minusMonths(months: Long): YearMonth = plusMonths(-months)
    fun plusYears(years: Long): YearMonth = YearMonth((year + years).toInt(), monthValue)
    fun minusYears(years: Long): YearMonth = plusYears(-years)
    fun withMonth(month: Int): YearMonth = of(year, month)
    fun withYear(year: Int): YearMonth = of(year, monthValue)
    fun isBefore(other: YearMonth): Boolean = compareTo(other) < 0
    fun isAfter(other: YearMonth): Boolean = compareTo(other) > 0
    fun format(formatter: DateTimeFormatter): String = formatter.format(atDay(1))

    override fun compareTo(other: YearMonth): Int =
        if (year != other.year) year.compareTo(other.year) else monthValue.compareTo(other.monthValue)
    override fun equals(other: Any?): Boolean = other is YearMonth && year == other.year && monthValue == other.monthValue
    override fun hashCode(): Int = year xor (monthValue shl 27)
    override fun toString(): String {
        val y = when {
            year in 0..9999 -> year.toString().padStart(4, '0')
            year < 0 -> "-" + (-year).toString().padStart(4, '0')
            else -> "+$year"
        }
        return y + "-" + monthValue.toString().padStart(2, '0')
    }

    companion object {
        private val ISO = Regex("""([+-]?\d{4,9})-(\d{2})""")

        fun of(year: Int, month: Int): YearMonth {
            if (month !in 1..12) throw DateTimeException("Invalid value for MonthOfYear (valid values 1 - 12): $month")
            return YearMonth(year, month)
        }
        fun of(year: Int, month: Month): YearMonth = YearMonth(year, month.value)
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.monthValue)
        fun from(dateTime: LocalDateTime): YearMonth = YearMonth(dateTime.year, dateTime.monthValue)
        fun parse(text: CharSequence): YearMonth {
            val match = ISO.matchEntire(text) ?: throw DateTimeParseException("Text '$text' could not be parsed", text, 0)
            try {
                return of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            } catch (e: DateTimeParseException) {
                throw e
            } catch (e: Exception) {
                throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0)
            }
        }
        fun now(): YearMonth = from(LocalDate.now())
        fun now(zone: ZoneId): YearMonth = from(LocalDate.now(zone))
    }
}

class DateTimeParseException(message: String, val parsedString: CharSequence = "", val errorIndex: Int = 0) :
    DateTimeException(message)

// ---- Platform hooks (the only parts that are not pure Kotlin) ----

/** UTC offset in seconds of the given IANA zone at the given instant. */
expect fun platformZoneOffsetSeconds(zoneId: String, epochSecond: Long): Int
expect fun platformSystemZoneId(): String
expect fun platformCurrentTimeMillis(): Long
/** width: 0 = full, 1 = short, 2 = narrow. */
expect fun platformMonthName(month: Int, width: Int, standalone: Boolean, languageTag: String): String
/** day: 1 = Monday ... 7 = Sunday. width: 0 = full, 1 = short, 2 = narrow. */
expect fun platformWeekdayName(day: Int, width: Int, standalone: Boolean, languageTag: String): String
