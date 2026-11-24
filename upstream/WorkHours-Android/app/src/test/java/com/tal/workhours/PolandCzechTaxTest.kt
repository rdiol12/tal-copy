package com.tal.workhours

import java.math.BigDecimal
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PolandCzechTaxTest {
    private fun pl() = AppSettings(taxCountry = "PL", foreignProfileCountry = "PL",
        annualTaxableBase = 100000.0, foreignMonthlyDeductions = 2000.0)
    private fun cz() = AppSettings(taxCountry = "CZ", foreignProfileCountry = "CZ", foreignMonthlyDeductions = 4600.0)
    private fun net(gross: Double, s: AppSettings) = estimateNet(gross, gross, s, 2026)!!
    private fun czBefore(annual: String) = czechAnnualTaxBeforeCredit(BigDecimal(annual))

    @Test fun polishBasicReductionIsNonRefundableAndAlreadyIncludesTheTaxFreeAmount() {
        for ((base, tax, credit) in listOf(Triple(0.0, 0.0, 0.0), Triple(10000.0, 0.0, 1200.0),
            Triple(30000.0, 0.0, 3600.0), Triple(40000.0, 1200.0, 3600.0))) {
            val s = pl().copy(annualTaxableBase = base, foreignMonthlyDeductions = 100.0)
            val n = net(100.0 + base / 12, s)
            assertEquals(tax / 12, n.incomeTax, .00001)
            assertEquals(credit / 12, n.personalCredit, .00001)
        }
    }
    @Test fun polishPublishedRatesSwitchOnlyOnIncomeAbove120000() {
        for ((base, before) in listOf(0.0 to 0.0, 30000.0 to 3600.0, 120000.0 to 14400.0,
            150000.0 to 24000.0, 1000000.0 to 296000.0))
            assertEquals(before, polishAnnualTaxBeforeCredit(base), .00001)
        val n = net(15000.0, pl().copy(annualTaxableBase = 150000.0, foreignMonthlyDeductions = 3000.0))
        assertEquals(1700.0, n.incomeTax, .00001)
        assertEquals(10300.0, n.net, .00001)
    }
    @Test fun polishAnnualBaseAndTaxUseWholePlnHalfUpRounding() {
        assertEquals(14400.0, polishAnnualTaxBeforeCredit(120001.49), .00001)
        assertEquals(14401.0, polishAnnualTaxBeforeCredit(120001.50), .00001)
        assertEquals(3600.0, polishAnnualTaxBeforeCredit(30004.49), .00001)
        assertEquals(3601.0, polishAnnualTaxBeforeCredit(30004.50), .00001)
    }
    @Test fun polishVerifiedBaseIsNotReducedAgainByCashHealthOrTaxFreeAmount() {
        val n = net(10000.0, pl())
        assertEquals(700.0, n.incomeTax, .00001)
        assertEquals(7300.0, n.net, .00001)
        assertEquals(2000.0, n.otherDeductions, .00001)
        assertEquals(0.0, n.healthInsurance, .00001) // included in actual cash, no inferred rate
        val extraCash = net(10000.0, pl().copy(foreignMonthlyDeductions = 2100.0))
        assertEquals(n.incomeTax, extraCash.incomeTax, .00001)
        assertEquals(n.net - 100, extraCash.net, .00001)
    }
    @Test fun polishMissingInconsistentBasesAndSolidarityCasesAreUnavailable() {
        for (base in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 120001.0, 92399.0))
            assertNull(estimateNet(10000.0, 10000.0, pl().copy(annualTaxableBase = base), 2026))
        assertNotNull(estimateNet(10000.0, 10000.0, pl().copy(annualTaxableBase = 92400.0), 2026))
        assertNull(estimateNet(83334.0, 83334.0, pl().copy(annualTaxableBase = 900000.0, foreignMonthlyDeductions = 10000.0), 2026))
        assertNotNull(estimateNet(83333.0, 83333.0, pl().copy(annualTaxableBase = 900000.0, foreignMonthlyDeductions = 10000.0), 2026))
    }
    @Test fun czechAnnualRoundingUsesHundredsDownThenWholeCrownTaxUp() {
        assertEquals(0.0, czBefore("99.99"), .00001)
        assertEquals(15.0, czBefore("100.00"), .00001)
        assertEquals(15.0, czBefore("199.99"), .00001)
        assertEquals(30.0, czBefore("200"), .00001)
        assertEquals(264420.0, czBefore("1762812"), .00001)
        assertEquals(264443.0, czBefore("1762900"), .00001)
        assertEquals(318976.0, czBefore("2000000"), .00001)
    }
    @Test fun czechBasicCreditAndActualInsuranceAreAppliedOnlyOnce() {
        val n = net(40000.0, cz())
        assertEquals(3430.0, n.incomeTax, .00001)
        assertEquals(2570.0, n.personalCredit, .00001)
        assertEquals(31970.0, n.net, .00001)
        val after = net(40000.0, cz().copy(foreignMonthlyDeductions = 4700.0))
        assertEquals(n.incomeTax, after.incomeTax, .00001)
        assertEquals(n.net - 100, after.net, .00001)
        // An irrelevant saved foreign base is never silently used for Czech regular salary.
        assertEquals(n, net(40000.0, cz().copy(annualTaxableBase = 1.0)))
    }
    @Test fun czechHighRateAndLowIncomesHaveNoInventedRefundOrSalaryFloor() {
        val high = net(200000.0, cz().copy(foreignMonthlyDeductions = 23000.0))
        assertEquals(31678.0, high.incomeTax, .00001)
        assertEquals(145322.0, high.net, .00001)
        val low = net(10000.0, cz().copy(foreignMonthlyDeductions = 2500.0))
        assertEquals(0.0, low.incomeTax, .00001)
        assertEquals(1500.0, low.personalCredit, .00001)
        assertEquals(7500.0, low.net, .00001)
        val zero = net(0.0, cz().copy(foreignMonthlyDeductions = 0.0))
        assertEquals(0.0, zero.net, .00001)
        assertEquals(0.0, zero.personalCredit, .00001)
    }
    @Test fun bothCountriesRequireExplicitCashYearAndCountryBoundEligibility() {
        for ((gross, s) in listOf(10000.0 to pl(), 40000.0 to cz())) {
            for (cash in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, gross + 1, gross))
                assertNull(estimateNet(gross, gross, s.copy(foreignMonthlyDeductions = cash), 2026))
            for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE))
                assertNull(estimateNet(value, value, s, 2026))
            for (confirmation in listOf("", "SG"))
                assertNull(estimateNet(gross, gross, s.copy(foreignProfileCountry = confirmation), 2026))
            for (year in listOf(2025, 2027)) {
                assertFalse(supportsTaxYear(s.taxCountry, year))
                assertNull(estimateNet(gross, gross, s.copy(taxYear = year), year))
            }
            assertNull(estimateNet(gross, gross, s.copy(taxYear = 2025), 2026))
        }
    }
    @Test fun selectorCurrenciesAndCalendarYearsAreIndependentOfLanguage() {
        assertEquals("PLN", taxCurrency("PL"))
        assertEquals("CZK", taxCurrency("CZ"))
        assertEquals(supportedTaxCountries.size, taxCountries.size)
        assertEquals(supportedTaxCountries, taxCountries.map { it.code }.toSet())
        for (country in listOf("PL", "CZ")) {
            assertTrue(country in foreignTaxCountries)
            assertEquals(2026, taxYearFor(country, LocalDate.of(2026, 12, 31)))
            assertEquals(2027, taxYearFor(country, LocalDate.of(2027, 1, 1)))
        }
    }
    @Test fun fullSettingsAndCountryProfilesSurviveLocalAndDriveBackupRoundTrips() {
        for (s in listOf(pl(), cz())) {
            s.validate(allowUnconfigured = true)
            val original = AppData(settings = s.copy(reasons = listOf("Support")))
            assertEquals(original, decodeData(encodeData(original)))
            assertEquals(original, readBackup(backupText(original)))
        }
        assertNull(AppData().settings.annualTaxableBase)
        assertNull(AppData().settings.foreignMonthlyDeductions)
        assertEquals("", AppData().settings.foreignProfileCountry)
    }
    @Test fun legacyV10MalaysiaProfileIsReadableAndFutureVersionIsRefused() {
        val original = AppData(settings = AppSettings(taxCountry = "MY", foreignProfileCountry = "MY",
            annualTaxableBase = 47000.0, foreignMonthlyDeductions = 575.0))
        val json = JSONObject(encodeData(original)).put("version", 10)
        assertEquals(original, decodeData(json.toString()))
        assertNotNull(estimateNet(5000.0, 5000.0, decodeData(json.toString()).settings, 2026))
        assertEquals(DATA_VERSION, JSONObject(encodeData(original)).getInt("version"))
        json.put("version", DATA_VERSION + 1)
        assertThrows(IllegalArgumentException::class.java) { decodeData(json.toString()) }
    }
}
