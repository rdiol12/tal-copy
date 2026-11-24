package com.tal.workhours

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExpansionTenTaxTest {
    // Entered, independently verified scenario data; cash amounts are NOT statutory contribution rates.
    private data class Scenario(val country: String, val gross: Double, val annual: Double, val base: Double, val cash: Double)
    private val cases = listOf(
        Scenario("PE", 8000.0, 112000.0, 73500.0, 1000.0),
        Scenario("CO", 12000000.0, 160000000.0, 110000000.0, 1000000.0),
        Scenario("EE", 3000.0, 39000.0, 28260.0, 180.0),
        Scenario("LV", 3000.0, 39000.0, 28305.0, 315.0),
        Scenario("LT", 4000.0, 52000.0, 52000.0, 780.0),
        Scenario("BG", 2000.0, 26000.0, 22000.0, 280.0),
        Scenario("RO", 10000.0, 130000.0, 84500.0, 3500.0),
        Scenario("HU", 500000.0, 6500000.0, 6500000.0, 92500.0),
        Scenario("GE", 3000.0, 39000.0, 39000.0, 60.0),
        Scenario("AM", 500000.0, 6500000.0, 6500000.0, 35000.0))
    private fun profile(s: Scenario) = AppSettings(taxCountry = s.country, foreignProfileCountry = s.country,
        annualEmploymentGross = s.annual, annualTaxableBase = s.base, foreignMonthlyDeductions = s.cash)
    private fun estimate(s: Scenario, p: AppSettings = profile(s)) = estimateNet(s.gross, s.gross, p, 2026)
    private fun payout(index: Int, annualTax: Double, expected: Double) {
        val n = estimate(cases[index])!!
        assertEquals(annualTax / 12, n.incomeTax, .0001)
        assertEquals(cases[index].cash, n.otherDeductions, .0001)
        assertEquals(expected, n.net, .0001)
    }
    @Test fun peru2026UitAndPublishedBracketQuotas() {
        assertEquals(5500.0, PERU_UIT_2026, 0.0)
        for ((base, tax) in listOf(0.0 to 0.0, 27500.0 to 2200.0, 110000.0 to 13750.0,
            192500.0 to 27775.0, 247500.0 to 38775.0, 550000.0 to 129525.0))
            assertEquals(tax, expansionTenAnnualTax("PE", base), .0001)
    }
    @Test fun colombia2026UvtIsNotThe2025Value() {
        assertEquals(52374.0, COLOMBIA_UVT_2026, 0.0)
        assertEquals(0.0, expansionTenAnnualTax("CO", 57087660.0), .0001)
        assertEquals(.19 * 52374, expansionTenAnnualTax("CO", 57140034.0), .0001)
    }
    @Test fun colombiaArticle241RoundedStartingQuotasArePreserved() {
        for ((uvt, tax) in listOf(0.0 to 0.0, 1090.0 to 0.0, 1700.0 to 115.9,
            1701.0 to 116.28, 4100.0 to 788.0, 4101.0 to 788.33,
            8670.0 to 2296.1, 8671.0 to 2296.35, 18970.0 to 5901.0,
            18971.0 to 5901.37, 31000.0 to 10352.1, 31001.0 to 10352.39))
            assertEquals("UVT=$uvt", tax, colombiaTaxInUvt(uvt), .000001)
    }
    @Test fun lithuania2026NewThirtySixAndSixtyVduBands() {
        assertEquals(83237.40, LITHUANIA_36_VDU_2026, .0001)
        assertEquals(138729.0, LITHUANIA_60_VDU_2026, .0001)
        for ((base, tax) in listOf(0.0 to 0.0, 83237.40 to 16647.48,
            138729.0 to 30520.38, 150000.0 to 34127.10))
            assertEquals(tax, expansionTenAnnualTax("LT", base), .0001)
    }
    @Test fun verifiedFlatRateProfilesUseTheirOwnRates() {
        for ((c, rate) in listOf("EE" to .22, "LV" to .255, "BG" to .10, "RO" to .10,
            "HU" to .15, "GE" to .20, "AM" to .20)) {
            assertEquals(0.0, expansionTenAnnualTax(c, 0.0), .0001)
            assertEquals(10000 * rate, expansionTenAnnualTax(c, 10000.0), .0001)
        }
    }
    @Test fun peruVerifiedBaseDoesNotSubtractSevenUitAgain() = payout(0, 8640.0, 6280.0)
    @Test fun colombiaAnnualArticle241ReserveIsNotMonthlyArticle383Withholding() = payout(1, 11945360.0, 10004553.333333)
    @Test fun estoniaBasicExemptionAndDuesAreAlreadyInBase() = payout(2, 6217.2, 2301.9)
    @Test fun latviaVerifiedBasicExemptionAndDuesAreNotRepeated() = payout(3, 7217.775, 2083.51875)
    @Test fun lithuaniaActualInsuranceCashDoesNotBecomeAnExtraPitDeduction() = payout(4, 10400.0, 2353.333333)
    @Test fun bulgaria2026EuroSalaryExample() = payout(5, 2200.0, 1536.666667)
    @Test fun romaniaVerifiedMonthlyBasesAreSummedBeforeAnnualReserve() = payout(6, 8450.0, 5795.833333)
    @Test fun hungaryOrdinarySalaryHasNoInventedAllowance() = payout(7, 975000.0, 326250.0)
    @Test fun georgiaPensionIsSeparateCashRatherThanReducingTaxableSalary() = payout(8, 7800.0, 2290.0)
    @Test fun armeniaActualPensionStampAndHealthCashAreSeparate() = payout(9, 1300000.0, 356666.666667)
    @Test fun cashChangesNetWithoutChangingVerifiedTaxOrInventingCredits() {
        for (s in cases) {
            val a = estimate(s)!!
            val b = estimate(s, profile(s).copy(foreignMonthlyDeductions = s.cash + 100))!!
            assertEquals(a.incomeTax, b.incomeTax, .0001)
            assertEquals(a.net - 100, b.net, .0001)
            assertEquals(s.gross - b.incomeTax - b.otherDeductions, b.net, .0001)
            assertEquals(0.0, b.nationalInsurance + b.healthInsurance + b.pension + b.personalCredit, .0001)
        }
    }
    @Test fun peruEstoniaLatviaRejectBasesStillContainingTheirOrdinaryBasicRelief() {
        for (s in cases.filter { it.country in setOf("PE", "EE", "LV") })
            assertNull(estimate(s, profile(s).copy(annualTaxableBase = s.annual)))
    }
    @Test fun latviaConservativeGrossScopeStopsBeforeSolidarityAndHigherRateCases() {
        val s = cases[3]
        val p = profile(s).copy(annualEmploymentGross = 105300.0, annualTaxableBase = 98700.0)
        assertNotNull(estimateNet(10000.0, 10000.0, p, 2026))
        assertNull(estimateNet(10000.0, 10000.0, p.copy(annualEmploymentGross = 105300.01), 2026))
    }
    @Test fun hungaryGeorgiaArmeniaRefusePretaxInsuranceOrOtherUnsupportedAllowances() {
        for (s in cases.filter { it.country in setOf("HU", "GE", "AM") })
            assertNull(estimate(s, profile(s).copy(annualTaxableBase = s.annual - 100)))
    }
    @Test fun lithuaniaHighIncomeCannotSilentlyApplyUnimplementedDeductions() {
        val s = cases[4]
        val p = profile(s).copy(annualEmploymentGross = 150000.0, annualTaxableBase = 150000.0)
        val n = estimateNet(15000.0, 15000.0, p, 2026)!!
        assertEquals(34127.10 / 12, n.incomeTax, .0001)
        assertNull(estimateNet(15000.0, 15000.0, p.copy(annualTaxableBase = 149000.0), 2026))
        assertNotNull(estimate(s, profile(s).copy(annualTaxableBase = 51000.0))) // entered, verified ordinary NPD
    }
    @Test fun bonusChangesAnnualReserveWithoutAddingBonusOrNoncashBenefitsToPeriodPayout() {
        val s = cases[5]
        val p = profile(s).copy(annualEmploymentGross = 24000.0, annualTaxableBase = 20000.0)
        val before = estimate(s, p)!!
        val after = estimate(s)!!
        assertEquals(200.0 / 12, after.incomeTax - before.incomeTax, .0001)
        assertEquals(before.net - 200.0 / 12, after.net, .0001)
    }
    @Test fun requiredAnnualFiguresNeverDefaultToTwelveTimesPeriodGross() {
        for (s in cases) {
            for (a in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, s.gross - 1, MAX_VERIFIED_ANNUAL_AMOUNT + 1))
                assertNull(estimate(s, profile(s).copy(annualEmploymentGross = a)))
            for (b in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, s.annual + 1))
                assertNull(estimate(s, profile(s).copy(annualTaxableBase = b)))
        }
    }
    @Test fun absentInvalidCashOrAnImpossibleCashPayoutDoesNotProduceNet() {
        for (s in cases) {
            for (c in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, s.gross + 1, s.gross))
                assertNull(estimate(s, profile(s).copy(foreignMonthlyDeductions = c)))
            for (g in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE))
                assertNull(estimateNet(g, g, profile(s), 2026))
        }
    }
    @Test fun explicitCountryBoundConfirmationAndCorrectYearAreRequired() {
        for (s in cases) {
            for (c in listOf("", "IL", "GB")) assertNull(estimate(s, profile(s).copy(foreignProfileCountry = c)))
            for (year in listOf(2025, 2027)) {
                assertFalse(supportsTaxYear(s.country, year))
                assertNull(estimateNet(s.gross, s.gross, profile(s).copy(taxYear = year), year))
            }
            assertNull(estimate(s, profile(s).copy(taxYear = 2025)))
        }
    }
    @Test fun lowAndZeroIncomeDoNotInventRefundsOrInsuranceExemptions() {
        for (s in cases) {
            val base = if (s.country in setOf("PE", "EE", "LV", "LT")) 0.0 else 1200.0
            val p = profile(s).copy(annualEmploymentGross = 1200.0, annualTaxableBase = base, foreignMonthlyDeductions = 10.0)
            val n = estimateNet(100.0, 100.0, p, 2026)!!
            assertEquals(90.0 - expansionTenAnnualTax(s.country, base) / 12, n.net, .0001)
            assertEquals(0.0, n.personalCredit, 0.0)
            assertEquals(0.0, estimateNet(0.0, 0.0, p.copy(annualEmploymentGross = 0.0,
                annualTaxableBase = 0.0, foreignMonthlyDeductions = 0.0), 2026)!!.net, .0001)
        }
    }
    @Test fun allTenUseCalendar2026AndHaveDistinctCountryCurrencySelectors() {
        assertEquals(supportedTaxCountries.size, taxCountries.size)
        assertEquals(supportedTaxCountries, taxCountries.map { it.code }.toSet())
        for ((code, currency) in listOf("PE" to "PEN", "CO" to "COP", "EE" to "EUR", "LV" to "EUR", "LT" to "EUR",
            "BG" to "EUR", "RO" to "RON", "HU" to "HUF", "GE" to "GEL", "AM" to "AMD")) {
            assertEquals(currency, taxCurrency(code))
            assertTrue(code in verifiedBaseTaxCountries && code in foreignTaxCountries)
            assertEquals(2026, taxYearFor(code, LocalDate.of(2026, 1, 1)))
            assertEquals(2027, taxYearFor(code, LocalDate.of(2027, 1, 1)))
        }
    }
    @Test fun completeSettingsAndAnnualFiguresRoundTripForEveryNewCountry() {
        for (s in cases) {
            val data = AppData(settings = profile(s).copy(reasons = listOf("Remote support")))
            data.settings.validate(allowUnconfigured = true)
            assertEquals(data, decodeData(encodeData(data)))
            assertEquals(data, readBackup(backupText(data)))
        }
        assertTrue(DATA_VERSION >= 13)
    }
    @Test fun legacyV12AnnualFiguresRemainIntactAndFutureSchemaIsRefused() {
        val s = AppSettings(taxCountry = "TH", foreignProfileCountry = "TH", annualEmploymentGross = 650000.0,
            annualTaxableBase = 480000.0, foreignMonthlyDeductions = 875.0)
        val data = AppData(settings = s)
        val old = JSONObject(encodeData(data)).put("version", 12)
        assertEquals(data, decodeData(old.toString()))
        assertEquals(47000.0, estimateNet(50000.0, 50000.0, decodeData(old.toString()).settings, 2026)!!.net, .0001)
        old.put("version", DATA_VERSION + 1)
        assertThrows(IllegalArgumentException::class.java) { decodeData(old.toString()) }
    }
}
