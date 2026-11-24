package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PayrollTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun millis(date: String, hour: Double): Long = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() + (hour * HOUR_MS).toLong()
    private fun shift(date: String = "2026-10-20", from: Double, until: Double, location: String = OFFICE) =
        Shift(start = millis(date, from), end = millis(date, until), location = location, workDate = date)
    private fun near(expected: Double, actual: Double, delta: Double = 0.000001) = assertEquals(expected, actual, delta)

    @Test fun splitDaySharesOvertimeThresholds() {
        val day = calculateDays(listOf(shift(from = 8.0, until = 16.0), shift(from = 18.0, until = 21.0, location = HOME)), AppSettings()).single()
        near(8.0, day.regularMs / HOUR_MS)
        near(2.0, day.tierMs[0] / HOUR_MS)
        near(1.0, day.tierMs[1] / HOUR_MS)
        near(11.0, day.totalMs / HOUR_MS)
    }
    @Test fun thresholdsAndThirdUnlimitedTier() {
        val s = AppSettings(tiers = listOf(OvertimeTier(2.0, 125.0), OvertimeTier(2.0, 150.0), OvertimeTier(null, 200.0)))
        val d = calculateDays(listOf(shift(from = 1.0, until = 16.0)), s).single()
        assertEquals(listOf(2.0, 2.0, 3.0), d.tierMs.map { it / HOUR_MS })
        val exact = calculateDays(listOf(shift(from = 8.0, until = 16.0)), s).single()
        near(0.0, exact.tierMs.sum())
    }
    @Test fun breakOncePerDayVersusPerSegment() {
        val entries = listOf(shift(from = 8.0, until = 16.0), shift(from = 18.0, until = 20.0, location = HOME))
        val s = AppSettings(breakMode = "DAILY", breakMinutes = 30, deductHomeBreak = true)
        near(9.5, calculateDays(entries, s).single().totalMs / HOUR_MS)
        near(9.0, calculateDays(entries, s.copy(breakMode = "SEGMENT")).single().totalMs / HOUR_MS)
    }
    @Test fun homeCanBeExcludedFromBreaks() {
        val entries = listOf(shift(from = 8.0, until = 10.0, location = HOME), shift(from = 12.0, until = 20.0))
        val day = calculateDays(entries, AppSettings(breakMode = "SEGMENT", deductHomeBreak = false)).single()
        near(2.0, day.homeMs / HOUR_MS)
        near(7.5, day.officeMs / HOUR_MS)
    }
    @Test fun dailyBreakCarriesAcrossShortEligibleSegments() {
        val entries = listOf(shift(from = 8.0, until = 8.25), shift(from = 12.0, until = 13.0))
        val day = calculateDays(entries, AppSettings(breakMode = "DAILY", breakMinutes = 30)).single()
        near(.75, day.totalMs / HOUR_MS)
        near(0.0, day.parts[0].netMs)
        near(.75, day.parts[1].netMs / HOUR_MS)
    }
    @Test fun disabledBreakNeverDeducts() {
        val d = calculateDays(listOf(shift(from = 8.0, until = 20.0, location = HOME)), AppSettings(breakMode = "NONE", deductHomeBreak = true)).single()
        near(12.0, d.totalMs / HOUR_MS)
        near(2.0, d.tierMs[1] / HOUR_MS)
    }
    @Test fun payrollPeriodEdgesAndShortMonths() {
        assertEquals(LocalDate.parse("2026-09-25"), periodFor(LocalDate.parse("2026-10-24"), 25).from)
        assertEquals(LocalDate.parse("2026-10-25"), periodFor(LocalDate.parse("2026-10-25"), 25).from)
        assertEquals(LocalDate.parse("2026-11-24"), periodFor(LocalDate.parse("2026-10-25"), 25).end)
        assertEquals(LocalDate.parse("2026-02-28"), periodFor(LocalDate.parse("2026-03-01"), 31).from)
        assertEquals(LocalDate.parse("2026-03-30"), periodFor(LocalDate.parse("2026-03-01"), 31).end)
    }
    @Test fun monthlyAndDailyShortageDiffer() {
        val entries = listOf(shift(from = 8.0, until = 15.0), shift(date = "2026-10-21", from = 8.0, until = 18.0))
        val data = AppData(shifts = entries, settings = AppSettings(monthlyHours = 20.0))
        val p = periodFor(LocalDate.parse("2026-10-20"), 25)
        val s = summarize(data, p)
        near(3.0, s.monthlyShortageMs / HOUR_MS)
        near(1.0, s.dailyShortageMs / HOUR_MS)
    }
    @Test fun midnightShiftBelongsToStartDay() {
        val d = calculateDays(listOf(shift(date = "2026-10-24", from = 20.0, until = 27.0)), AppSettings()).single()
        assertEquals(LocalDate.parse("2026-10-24"), d.day)
        near(7.0, d.totalMs / HOUR_MS)
    }
    @Test fun pensionPointsAndInsuranceUse2026Tables() {
        val settings = AppSettings(creditPoints = 2.25, pensionPercent = 6.0)
        val estimate = estimateNet(7010.0, 7010.0, settings, 2026)!!
        near(420.6, estimate.pension)
        near(147.21, estimate.pensionCredit)
        near(9.29, estimate.incomeTax)
        near(7010.0 * .0104, estimate.nationalInsurance)
        near(7010.0 * .0323, estimate.healthInsurance)
        assertNull(estimateNet(7010.0, 7010.0, settings, 2027))
        assertNull(estimateNet(7010.0, 7010.0, settings.copy(creditPoints = null), 2026))
    }
    @Test fun insuranceCappedAndPensionCreditCapped() {
        val s = AppSettings(creditPoints = 0.0, pensionPercent = 6.0)
        val n = estimateNet(100000.0, 100000.0, s, 2026)!!
        near(7703 * .0104 + (51910 - 7703) * .07, n.nationalInsurance)
        near(237.65, n.pensionCredit)
        near(38377.65, n.incomeTax, .01)
    }
    @Test fun overlapsAreRejectedButAdjacentSegmentsAllowed() {
        val a = shift(from = 8.0, until = 16.0)
        AppData(shifts = listOf(a, shift(from = 16.0, until = 18.0))).validate()
        try { AppData(shifts = listOf(a, shift(from = 15.0, until = 18.0))).validate(); fail("overlap accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun globalSalaryAddsFullOvertimePayment() {
        val settings = AppSettings(globalSalary = true, monthlySalary = 10000.0, monthlyHours = 200.0, hourlyWage = 50.0)
        val data = AppData(shifts = listOf(shift(from = 8.0, until = 16.0), shift(from = 18.0, until = 21.0, location = HOME)), settings = settings, setupComplete = true)
        val period = periodFor(LocalDate.parse("2026-10-20"), 1)
        val summary = summarize(data, period)
        near(200.0, summary.overtimePay)
        near(10200.0, summary.gross)
        near(600.0, summarize(data.copy(settings = settings.copy(globalSalary = false)), period).gross)
    }
}
