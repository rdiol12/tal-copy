package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class HourlyMarketsTaxTest {
    private fun profile(country: String, year: Int = 2026) = AppSettings(taxCountry = country, taxYear = year,
        foreignProfileCountry = country, foreignMonthlyDeductions = 0.0)
    private fun net(gross: Double, s: AppSettings) = estimateNet(gross, gross, s, s.taxYear)!!

    @Test fun irelandAppliesStandardBandsAndBasicEmployeeCredits() {
        val n = net(2000.0, profile("IE").copy(foreignMonthlyDeductions = 120.0))
        assertEquals(800.0 / 12, n.incomeTax, .00001)
        assertEquals(299.82 / 12, n.otherTax, .00001)
        assertEquals(4000.0 / 12, n.personalCredit, .00001)
        assertEquals(1788.34833333, n.net, .00001)
        assertEquals(0.0, n.nationalInsurance, .00001) // PRSI is entered as actual cash, never a guessed rate.
        assertEquals(120.0, n.otherDeductions, .00001)
    }
    @Test fun irelandUscMatchesRevenueExamplesAndHigherIncomeBand() {
        val n = net(50000.0 / 12, profile("IE"))
        assertEquals(7200.0, n.incomeTax * 12, .00001)
        assertEquals(1032.82, n.otherTax * 12, .00001)
        assertEquals(2030.62, net(75000.0 / 12, profile("IE")).otherTax * 12, .00001)
    }
    @Test fun irelandLowIncomeCreditsAreNonRefundableAndNoArtificialMinimumBlocksTheEstimate() {
        for (annual in listOf(0.0, 5000.0, 10000.0, 12000.0)) {
            val n = net(annual / 12, profile("IE"))
            assertEquals(0.0, n.incomeTax, .00001)
            assertEquals(0.0, n.otherTax, .00001)
            assertEquals(annual / 12, n.net, .00001)
            assertTrue(n.personalCredit * 12 <= annual * .20 + .00001)
        }
    }
    @Test fun irelandUscExemptionEndsAbove13000AndTaxesFullIncome() {
        assertEquals(0.0, net(13000.0 / 12, profile("IE")).otherTax, .00001)
        assertEquals(79.8202, net(13000.01 / 12, profile("IE")).otherTax * 12, .00001)
    }
    @Test fun irelandActualPrsiAndPostTaxPensionReduceCashNotTaxOrUsc() {
        val before = net(4000.0, profile("IE"))
        val after = net(4000.0, profile("IE").copy(foreignMonthlyDeductions = 234.0))
        assertEquals(before.incomeTax, after.incomeTax, .00001)
        assertEquals(before.otherTax, after.otherTax, .00001)
        assertEquals(before.net - 234, after.net, .00001)
        assertEquals(4000.0 - after.incomeTax - after.otherTax - after.otherDeductions, after.net, .00001)
    }
    @Test fun hongKongMonthlyMpfThresholdAndCap() {
        assertEquals(0.0, net(7099.0, profile("HK")).pension, .00001)
        assertEquals(355.0, net(7100.0, profile("HK")).pension, .00001)
        assertEquals(750.0, net(15000.0, profile("HK")).pension, .00001)
        assertEquals(1500.0, net(30000.0, profile("HK")).pension, .00001)
        assertEquals(1500.0, net(50000.0, profile("HK")).pension, .00001)
        assertEquals(7099.0, net(7099.0, profile("HK")).net, .00001)
    }
    @Test fun hongKong2026AllowanceAndMandatoryMpfDeductionBothApply() {
        val n = net(50000.0, profile("HK"))
        assertEquals(56290.0 / 12, n.incomeTax, .00001)
        assertEquals(1500.0, n.pension, .00001)
        assertEquals(43809.1666667, n.net, .00001)
        assertEquals(50000.0 - n.incomeTax - n.pension, n.net, .00001)
        assertEquals(520.0 / 12, net(15000.0, profile("HK")).incomeTax, .00001)
    }
    @Test fun hongKong2025RebateNeverLeaksInto2026OrBecomesRefundable() {
        assertEquals(55500.0 / 12, net(50000.0, profile("HK", 2025)).incomeTax, .00001)
        assertEquals(56290.0 / 12, net(50000.0, profile("HK", 2026)).incomeTax, .00001)
        assertEquals(0.0, net(15000.0, profile("HK", 2025)).incomeTax, .00001)
        assertEquals(14250.0, net(15000.0, profile("HK", 2025)).net, .00001)
    }
    @Test fun hongKongChoosesStandardTaxAndItsSecondTierForHighIncome() {
        val n = net(1000000.0, profile("HK"))
        assertEquals(1867120.0 / 12, n.incomeTax, .00001)
        assertEquals(0.0, n.personalCredit, .00001)
        assertEquals(1500.0, n.pension, .00001)
    }
    @Test fun hongKongExtraCashNeverDuplicatesMpfAndIsRejectedAboveAvailableCash() {
        val before = net(30000.0, profile("HK"))
        val after = net(30000.0, profile("HK").copy(foreignMonthlyDeductions = 100.0))
        assertEquals(before.incomeTax, after.incomeTax, .00001)
        assertEquals(before.pension, after.pension, .00001)
        assertEquals(before.net - 100, after.net, .00001)
        assertNull(estimateNet(7100.0, 7100.0, profile("HK").copy(foreignMonthlyDeductions = 7000.0), 2026))
    }
    @Test fun missingEligibilityOrCashAndUnsupportedYearsDoNotProduceAnEstimate() {
        for (country in hourlyMarketTaxCountries) {
            val s = profile(country)
            for (amount in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 5001.0))
                assertNull(estimateNet(5000.0, 5000.0, s.copy(foreignMonthlyDeductions = amount), 2026))
            assertNull(estimateNet(5000.0, 5000.0, s.copy(foreignProfileCountry = ""), 2026))
            assertNull(estimateNet(5000.0, 5000.0, s.copy(foreignProfileCountry = "GB"), 2026))
            assertNull(estimateNet(5000.0, 5000.0, s.copy(taxYear = 2025), 2026))
            assertNull(estimateNet(5000.0, 5000.0, s.copy(taxYear = 2027), 2027))
            assertNull(estimateNet(Double.MAX_VALUE, Double.MAX_VALUE, s, 2026))
        }
        assertNull(estimateNet(5000.0, 5000.0, profile("IE", 2025), 2025))
    }
    @Test fun hongKongFiscalYearAndCurrencyAreIndependentOfMainlandChina() {
        assertEquals("HKD", taxCurrency("HK"))
        assertEquals("CNY", taxCurrency("CN"))
        assertEquals("EUR", taxCurrency("IE"))
        assertEquals(2025, taxYearFor("HK", LocalDate.of(2026, 3, 31)))
        assertEquals(2026, taxYearFor("HK", LocalDate.of(2026, 4, 1)))
        assertEquals(2026, taxYearFor("IE", LocalDate.of(2026, 1, 1)))
        assertEquals(2026, taxYearFor("CN", LocalDate.of(2026, 3, 31)))
    }
    @Test fun bothProfilesAndActualDeductionsSurviveBackupAndLegacyV6RemainsReadable() {
        for (country in hourlyMarketTaxCountries) {
            val original = AppData(settings = profile(country).copy(foreignMonthlyDeductions = 150.0))
            assertEquals(original, readBackup(backupText(original)))
        }
        val old = JSONObject(encodeData(AppData())).put("version", 6)
        assertEquals("IL", decodeData(old.toString()).settings.taxCountry)
        assertEquals(DATA_VERSION, JSONObject(encodeData(AppData())).getInt("version"))
        old.put("version", DATA_VERSION + 1)
        assertThrows(IllegalArgumentException::class.java) { decodeData(old.toString()) }
    }
}
