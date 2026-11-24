package com.tal.workhours

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MalaysiaPhilippinesTaxTest {
    private fun my() = AppSettings(taxCountry = "MY", foreignProfileCountry = "MY",
        annualTaxableBase = 47000.0, foreignMonthlyDeductions = 575.0)
    private fun ph() = AppSettings(taxCountry = "PH", foreignProfileCountry = "PH",
        annualTaxableBase = 564000.0, foreignMonthlyDeductions = 3000.0)
    private fun net(gross: Double, s: AppSettings) = estimateNet(gross, gross, s, 2026)!!

    @Test fun malaysiaPublishedBracketQuotasAndTopRate() {
        val examples = listOf(0.0 to 0.0, 5000.0 to 0.0, 20000.0 to 150.0,
            35000.0 to 600.0, 50000.0 to 1500.0, 70000.0 to 3700.0,
            100000.0 to 9400.0, 400000.0 to 84400.0, 600000.0 to 136400.0,
            2000000.0 to 528400.0, 2100000.0 to 558400.0)
        examples.forEach { (base, expected) -> assertEquals(expected, malaysiaAnnualTax(base), .00001) }
    }
    @Test fun malaysiaVerifiedBaseIsNotReducedByContributionsAgain() {
        val n = net(5000.0, my())
        assertEquals(110.0, n.incomeTax, .00001)
        assertEquals(575.0, n.otherDeductions, .00001)
        assertEquals(4315.0, n.net, .00001)
        assertEquals(0.0, n.pension, .00001) // actual EPF is in cash, not a second computed charge
        val changed = net(5000.0, my().copy(foreignMonthlyDeductions = 600.0))
        assertEquals(n.incomeTax, changed.incomeTax, .00001)
        assertEquals(n.net - 25, changed.net, .00001)
    }
    @Test fun malaysiaPersonalRebateIsNonRefundableAndEndsAbove35000() {
        for ((base, tax, credit) in listOf(Triple(0.0, 0.0, 0.0), Triple(20000.0, 0.0, 150.0),
            Triple(30000.0, 50.0, 400.0), Triple(35000.0, 200.0, 400.0), Triple(35001.0, 600.06, 0.0))) {
            val n = net(5000.0, my().copy(annualTaxableBase = base))
            assertEquals(tax / 12, n.incomeTax, .00001)
            assertEquals(credit / 12, n.personalCredit, .00001)
            assertEquals(5000.0 - 575 - tax / 12, n.net, .00001)
        }
    }
    @Test fun malaysiaSelfReliefMustAlreadyBeReflectedAndLowIncomeIsAllowed() {
        assertNull(estimateNet(5000.0, 5000.0, my().copy(annualTaxableBase = 60000.0), 2026))
        assertNull(estimateNet(5000.0, 5000.0, my().copy(annualTaxableBase = 51001.0), 2026))
        assertNotNull(estimateNet(5000.0, 5000.0, my().copy(annualTaxableBase = 51000.0), 2026))
        val n = net(500.0, my().copy(annualTaxableBase = 0.0, foreignMonthlyDeductions = 50.0))
        assertEquals(450.0, n.net, .00001)
        assertEquals(0.0, n.incomeTax, .00001)
    }
    @Test fun philippinesPublishedBracketQuotasAndTopRate() {
        val examples = listOf(0.0 to 0.0, 250000.0 to 0.0, 400000.0 to 22500.0,
            800000.0 to 102500.0, 2000000.0 to 402500.0, 8000000.0 to 2202500.0,
            9000000.0 to 2552500.0)
        examples.forEach { (base, expected) -> assertEquals(expected, philippinesAnnualTax(base), .00001) }
    }
    @Test fun philippinesCashAndTaxBaseDeductContributionsOnlyOnce() {
        val n = net(50000.0, ph())
        assertEquals(55300.0 / 12, n.incomeTax, .00001)
        assertEquals(42391.6666667, n.net, .00001)
        assertEquals(3000.0, n.otherDeductions, .00001)
        val extraPostTax = net(50000.0, ph().copy(foreignMonthlyDeductions = 4000.0))
        assertEquals(n.incomeTax, extraPostTax.incomeTax, .00001)
        assertEquals(n.net - 1000, extraPostTax.net, .00001)
    }
    @Test fun philippinesRegularLowIncomeThresholdDoesNotInventMweEligibility() {
        val low = net(20000.0, ph().copy(annualTaxableBase = 228000.0, foreignMonthlyDeductions = 1000.0))
        assertEquals(19000.0, low.net, .00001)
        assertEquals(0.0, low.incomeTax, .00001)
        val taxable = net(25000.0, ph().copy(annualTaxableBase = 276000.0, foreignMonthlyDeductions = 2000.0))
        assertEquals(325.0, taxable.incomeTax, .00001)
        assertEquals(22675.0, taxable.net, .00001)
    }
    @Test fun philippinesInconsistentCashBaseAndNegativePayoutAreUnavailable() {
        assertNull(estimateNet(50000.0, 50000.0, ph().copy(annualTaxableBase = 563999.0), 2026))
        assertNull(estimateNet(50000.0, 50000.0, ph().copy(annualTaxableBase = 600001.0), 2026))
        assertNull(estimateNet(50000.0, 50000.0, ph().copy(foreignMonthlyDeductions = 50000.0), 2026))
    }
    @Test fun missingInvalidAmountsAndCountryBoundConfirmationRefuseEstimate() {
        for ((gross, s) in listOf(5000.0 to my(), 50000.0 to ph())) {
            for (cash in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, gross + 1))
                assertNull(estimateNet(gross, gross, s.copy(foreignMonthlyDeductions = cash), 2026))
            for (base in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, gross * 12 + 1))
                assertNull(estimateNet(gross, gross, s.copy(annualTaxableBase = base), 2026))
            for (amount in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE))
                assertNull(estimateNet(amount, amount, s, 2026))
            assertNull(estimateNet(gross, gross, s.copy(foreignProfileCountry = ""), 2026))
            assertNull(estimateNet(gross, gross, s.copy(foreignProfileCountry = "SG"), 2026))
        }
    }
    @Test fun only2026IsSupportedAndCurrencyUsesCountry() {
        for ((gross, s) in listOf(5000.0 to my(), 50000.0 to ph())) {
            for (year in listOf(2025, 2027)) {
                assertFalse(supportsTaxYear(s.taxCountry, year))
                assertNull(estimateNet(gross, gross, s.copy(taxYear = year), year))
            }
            assertNull(estimateNet(gross, gross, s.copy(taxYear = 2025), 2026))
            assertEquals(2026, taxYearFor(s.taxCountry, LocalDate.of(2026, 1, 1)))
            assertEquals(2027, taxYearFor(s.taxCountry, LocalDate.of(2027, 1, 1)))
        }
        assertEquals("MYR", taxCurrency("MY"))
        assertEquals("PHP", taxCurrency("PH"))
        assertEquals(supportedTaxCountries.size, taxCountries.size)
        assertEquals(taxCountries.map { it.code }.toSet(), supportedTaxCountries)
    }
    @Test fun newProfilesRoundTripLocalAndDriveEnvelopeWithVerifiedValues() {
        for (s in listOf(my(), ph())) {
            s.validate(allowUnconfigured = true)
            val original = AppData(settings = s)
            assertEquals(original, decodeData(encodeData(original)))
            assertEquals(original, readBackup(backupText(original)))
        }
        val fresh = AppData().settings
        assertEquals("", fresh.foreignProfileCountry)
        assertNull(fresh.annualTaxableBase)
        assertNull(fresh.foreignMonthlyDeductions)
    }
    @Test fun legacyV9ProfilesRemainReadableAndFuturePayloadIsRejected() {
        val original = AppData(settings = AppSettings(taxCountry = "SG", sgCpfStatus = "FULL",
            foreignProfileCountry = "SG", foreignMonthlyDeductions = 0.0))
        val json = JSONObject(encodeData(original)).put("version", 9)
        assertEquals(original, decodeData(json.toString()))
        assertNotNull(estimateNet(5000.0, 5000.0, decodeData(json.toString()).settings, 2026))
        json.put("version", DATA_VERSION + 1)
        assertThrows(IllegalArgumentException::class.java) { decodeData(json.toString()) }
        assertEquals(DATA_VERSION, JSONObject(encodeData(AppData())).getInt("version"))
    }
}
