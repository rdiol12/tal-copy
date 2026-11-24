package compat.time.temporal

import compat.time.LocalDate
import compat.time.LocalDateTime
import compat.time.LocalTime
import compat.time.Instant
import compat.time.YearMonth

enum class ChronoUnit(private val seconds: Long) {
    SECONDS(1), MINUTES(60), HOURS(3600), DAYS(86400), WEEKS(604800), MONTHS(0), YEARS(0);

    fun between(start: LocalDate, end: LocalDate): Long = when (this) {
        DAYS -> end.toEpochDay() - start.toEpochDay()
        WEEKS -> (end.toEpochDay() - start.toEpochDay()) / 7
        MONTHS -> monthsBetween(start, end)
        YEARS -> monthsBetween(start, end) / 12
        else -> throw UnsupportedOperationException("Unsupported unit: $this")
    }

    fun between(start: YearMonth, end: YearMonth): Long = when (this) {
        MONTHS -> (end.year * 12L + end.monthValue) - (start.year * 12L + start.monthValue)
        YEARS -> ((end.year * 12L + end.monthValue) - (start.year * 12L + start.monthValue)) / 12
        else -> throw UnsupportedOperationException("Unsupported unit: $this")
    }

    fun between(start: LocalDateTime, end: LocalDateTime): Long = when (this) {
        MONTHS, YEARS -> {
            var endDate = end.toLocalDate()
            val startDate = start.toLocalDate()
            if (endDate.isAfter(startDate) && end.toLocalTime().isBefore(start.toLocalTime())) endDate = endDate.minusDays(1)
            else if (endDate.isBefore(startDate) && end.toLocalTime().isAfter(start.toLocalTime())) endDate = endDate.plusDays(1)
            between(startDate, endDate)
        }
        else -> {
            val a = start.toLocalDate().toEpochDay() * 86400 + start.toLocalTime().toSecondOfDay()
            val b = end.toLocalDate().toEpochDay() * 86400 + end.toLocalTime().toSecondOfDay()
            var diff = b - a
            // Truncate toward zero taking the sub-second part into account.
            if (diff > 0 && end.nano < start.nano) diff -= 1 else if (diff < 0 && end.nano > start.nano) diff += 1
            diff / seconds
        }
    }

    fun between(start: LocalTime, end: LocalTime): Long = when (this) {
        SECONDS, MINUTES, HOURS -> (end.toNanoOfDay() - start.toNanoOfDay()) / (seconds * 1_000_000_000L)
        else -> throw UnsupportedOperationException("Unsupported unit: $this")
    }

    fun between(start: Instant, end: Instant): Long = when (this) {
        SECONDS, MINUTES, HOURS, DAYS -> {
            var diff = end.epochSecond - start.epochSecond
            if (diff > 0 && end.nano < start.nano) diff -= 1 else if (diff < 0 && end.nano > start.nano) diff += 1
            diff / seconds
        }
        else -> throw UnsupportedOperationException("Unsupported unit: $this")
    }

    private fun monthsBetween(start: LocalDate, end: LocalDate): Long {
        val packed1 = (start.year * 12L + start.monthValue - 1) * 32 + start.dayOfMonth
        val packed2 = (end.year * 12L + end.monthValue - 1) * 32 + end.dayOfMonth
        return (packed2 - packed1) / 32
    }
}
