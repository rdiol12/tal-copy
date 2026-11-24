package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import org.json.JSONObject

class AdditionalCountryTaxTest {
    private fun profile(country: String, deductions: Double? = 0.0) = AppSettings(taxCountry = country,
        foreignProfileCountry = country, foreignMonthlyDeductions = deductions)
    @Test fun russiaProgressiveResidentSalary() {
        val net = estimateNet(250000.0, 250000.0, profile("RU", 2000.0), 2026)!!
        assertEquals(33500.0, net.incomeTax, .00001)
        assertEquals(214500.0, net.net, .00001)
        assertEquals(0.0, net.nationalInsurance, .00001)
        assertEquals(73500.0, estimateNet(500000.0, 500000.0, profile("RU"), 2026)!!.incomeTax, .00001)
    }
    @Test fun indiaRebateMakesSalaryBelowThresholdTaxFreeButStillDeductsEmployeeCosts() {
        val net = estimateNet(100000.0, 100000.0, profile("IN", 6000.0), 2026)!!
        assertEquals(0.0, net.incomeTax, .00001)
        assertEquals(0.0, net.otherTax, .00001)
        assertEquals(94000.0, net.net, .00001)
    }
    @Test fun indiaTaxAndCessAreSeparateAndActualDeductionsDoNotReduceTaxableSalary() {
        val net = estimateNet(150000.0, 150000.0, profile("IN", 7000.0), 2026)!!
        assertEquals(145000.0 / 12, net.incomeTax, .00001)
        assertEquals(5800.0 / 12, net.otherTax, .00001)
        assertEquals(130433.333333, net.net, .00001)
        val without = estimateNet(150000.0, 150000.0, profile("IN"), 2026)!!
        assertEquals(without.incomeTax, net.incomeTax, .00001)
        assertEquals(without.net - 7000.0, net.net, .00001)
    }
    @Test fun indiaMarginalReliefAvoidsRebateCliffAndSurchargeCasesAreRejected() {
        val gross = 1280000.0 / 12
        val net = estimateNet(gross, gross, profile("IN"), 2026)!!
        assertEquals(5000.0 / 12, net.incomeTax, .00001)
        assertEquals(200.0 / 12, net.otherTax, .00001)
        val high = 5075001.0 / 12
        assertNull(estimateNet(high, high, profile("IN"), 2026))
    }
    @Test fun uaeExpatriateNetIncludesActualEmployeeDeductions() {
        val net = estimateNet(15000.0, 15000.0, profile("AE", 310.5), 2026)!!
        assertEquals(0.0, net.incomeTax, .00001)
        assertEquals(14689.5, net.net, .00001)
    }
    @Test fun confirmationMustMatchCountryAndDeductionsMustBeExplicitAndValid() {
        for (country in listOf("IN", "RU", "AE")) {
            assertNull(estimateNet(1000.0, 1000.0, profile(country, null), 2026))
            assertNull(estimateNet(1000.0, 1000.0, profile(country).copy(foreignProfileCountry = ""), 2026))
            assertNull(estimateNet(1000.0, 1000.0, profile(country, 1001.0), 2026))
            assertNull(estimateNet(1000.0, 1000.0, profile(country, -1.0), 2026))
            assertNull(estimateNet(1000.0, 1000.0, profile(country), 2027))
        }
        assertNull(estimateNet(1000.0, 1000.0, profile("IN").copy(taxCountry = "AE"), 2026))
    }
    @Test fun indiaYearStartsAprilFirstAndCalendarCountriesDoNotShift() {
        assertEquals(2025, taxYearFor("IN", LocalDate.of(2026, 3, 31)))
        assertEquals(2026, taxYearFor("IN", LocalDate.of(2026, 4, 1)))
        assertEquals(2026, taxYearFor("RU", LocalDate.of(2026, 1, 1)))
    }
    @Test fun allNewProfileInputsRoundTripAndOldBackupsDoNotAutoConfirmEligibility() {
        for (country in listOf("IN", "RU", "AE")) {
            val original = AppData(settings = profile(country, 123.45))
            assertEquals(original, readBackup(backupText(original)))
            val raw = JSONObject(encodeData(original)); val s = raw.getJSONObject("settings")
            s.remove("foreignProfileCountry"); s.remove("foreignMonthlyDeductions")
            val restored = decodeData(raw.toString()).settings
            assertEquals("", restored.foreignProfileCountry)
            assertNull(restored.foreignMonthlyDeductions)
            assertNull(estimateNet(1000.0, 1000.0, restored, 2026))
        }
    }
}
