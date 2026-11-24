package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class FurtherCountryTaxTest {
    private fun profile(country: String, year: Int = 2026) = AppSettings(taxCountry = country, taxYear = year,
        foreignProfileCountry = country, foreignMonthlyDeductions = 0.0)
    private fun net(gross: Double, settings: AppSettings) = estimateNet(gross, gross, settings, settings.taxYear)!!

    @Test fun germanTariffUsesWholeEuroBaseAndSolidarityPhaseIn() {
        assertEquals(0.0, germanAnnualIncomeTax(12348.99), .00001)
        assertEquals(10548.0, germanAnnualIncomeTax(50000.99), .00001)
        assertEquals(0.0, germanSolidarity(20350.0), .00001)
        assertEquals(.11, germanSolidarity(20351.0), .00001)
        assertEquals(1148.35, germanSolidarity(30000.0), .00001)
        assertEquals(3300.0, germanSolidarity(60000.0), .00001)
        val n = net(5000.0, profile("DE").copy(annualTaxableBase = 50000.0, foreignMonthlyDeductions = 900.0))
        assertEquals(879.0, n.incomeTax, .00001)
        assertEquals(3221.0, n.net, .00001)
    }
    @Test fun franceUses2025IncomeTableAndVerifiedFinalBaseWithoutAnotherExpenseAllowance() {
        val n = net(4000.0, profile("FR", 2025).copy(annualTaxableBase = 30000.0, foreignMonthlyDeductions = 900.0))
        assertEquals(2104.0 / 12, n.incomeTax, .00001)
        assertEquals(2924.6666667, n.net, .00001)
        assertEquals(0.0, n.nationalInsurance, .00001)
        assertNull(estimateNet(4000.0, 4000.0, profile("FR").copy(annualTaxableBase = 30000.0), 2026))
    }
    @Test fun frenchDecoteAndNonCollectionThresholdAreAppliedAfterWholeEuroRounding() {
        fun annualTax(base: Double) = net(4000.0, profile("FR", 2025).copy(annualTaxableBase = base)).incomeTax * 12
        assertEquals(445.0, annualTax(20000.0), .00001)
        assertEquals(0.0, annualTax(15000.0), .00001)
        assertEquals(0.0, annualTax(17590.0), .00001)
        assertEquals(62.0, annualTax(17600.0), .00001)
    }
    @Test fun verifiedBaseIsMandatoryAndMustFitTheAnnualGrossScenario() {
        for (s in listOf(profile("DE"), profile("FR", 2025))) {
            for (base in listOf(null, -1.0, Double.NaN, 48001.0))
                assertNull(estimateNet(4000.0, 4000.0, s.copy(annualTaxableBase = base), s.taxYear))
        }
        assertNull(estimateNet(20000.0, 20000.0, profile("FR", 2025).copy(annualTaxableBase = 100000.0), 2025))
    }
    @Test fun portugalUsesMainlandBandsSpecificDeductionAndEmployeeSocialContributions() {
        val n = net(2500.0, profile("PT").copy(taxRegion = "PT-MAINLAND"))
        assertEquals(275.0, n.nationalInsurance, .00001)
        assertEquals(4810.65401 / 12, n.incomeTax, .00001)
        assertEquals(1824.1121658333, n.net, .00001)
        assertEquals(0.0, n.otherTax, .00001)
    }
    @Test fun portugalUsesActualSocialDeductionWhenGreaterAndCalculatesSolidarity() {
        val n = net(10000.0, profile("PT").copy(taxRegion = "PT-MAINLAND"))
        assertEquals(1100.0, n.nationalInsurance, .00001)
        assertEquals(670.0 / 12, n.otherTax, .00001)
        assertEquals(10000.0 - n.incomeTax - n.nationalInsurance - n.otherTax, n.net, .00001)
    }
    @Test fun unsupportedPortugalRegionsAndMinimumExistenceProfilesAreRejected() {
        for (region in listOf("", "OTHER", "AZORES", "MADEIRA"))
            assertNull(estimateNet(2500.0, 2500.0, profile("PT").copy(taxRegion = region), 2026))
        assertNull(estimateNet(1600.0, 1600.0, profile("PT").copy(taxRegion = "PT-MAINLAND"), 2026))
    }
    @Test fun australianIncomeTaxRateChangeAndLitoDoNotDuplicateCredits() {
        val n = net(5000.0, profile("AU").copy(auHospitalCover = "FULL"))
        assertEquals(8420.0 / 12, n.incomeTax, .00001)
        assertEquals(100.0 / 12, n.personalCredit, .00001)
        assertEquals(100.0, n.healthInsurance, .00001)
        assertEquals(4198.3333333, n.net, .00001)
        val old = net(5000.0, profile("AU", 2025).copy(auHospitalCover = "FULL"))
        assertEquals(8688.0 / 12, old.incomeTax, .00001)
        assertEquals(4176.0, old.net, .00001)
    }
    @Test fun australianSurchargeDependsOnYearAndExplicitWholeYearHospitalCover() {
        val full = net(10000.0, profile("AU").copy(auHospitalCover = "FULL"))
        val none = net(10000.0, profile("AU").copy(auHospitalCover = "NONE"))
        val old = net(10000.0, profile("AU", 2025).copy(auHospitalCover = "NONE"))
        assertEquals(0.0, full.otherTax, .00001)
        assertEquals(100.0, none.otherTax, .00001)
        assertEquals(125.0, old.otherTax, .00001)
        assertEquals(7490.0, none.net, .00001)
        assertEquals(none.net + 100, full.net, .00001)
        assertNull(estimateNet(5000.0, 5000.0, profile("AU"), 2026))
        assertNull(estimateNet(4000.0, 4000.0, profile("AU").copy(auHospitalCover = "FULL"), 2026))
    }
    @Test fun newZealandIncomeTaxAccAndIetcHaveSeparateExplicitEligibility() {
        val n = net(5000.0, profile("NZ").copy(nzIetcEligible = true))
        assertEquals(9700.5 / 12, n.incomeTax, .00001)
        assertEquals(520.0 / 12, n.personalCredit, .00001)
        assertEquals(87.5, n.nationalInsurance, .00001)
        assertEquals(4104.125, n.net, .00001)
        val unconfirmed = net(5000.0, profile("NZ"))
        assertEquals(0.0, unconfirmed.personalCredit, .00001)
        assertEquals(10220.5 / 12, unconfirmed.incomeTax, .00001)
        assertEquals(260.0 / 12, net(68000.0 / 12, profile("NZ").copy(nzIetcEligible = true)).personalCredit, .00001)
        assertEquals(0.0, net(70000.0 / 12, profile("NZ").copy(nzIetcEligible = true)).personalCredit, .00001)
    }
    @Test fun newZealandAccYearSpecificCapsAndFiscalBoundaries() {
        assertEquals(2741.22 / 12, net(20000.0, profile("NZ")).nationalInsurance, .00001)
        assertEquals(2551.59 / 12, net(20000.0, profile("NZ", 2025)).nationalInsurance, .00001)
        assertEquals(2025, taxYearFor("NZ", LocalDate.of(2026, 3, 31)))
        assertEquals(2026, taxYearFor("NZ", LocalDate.of(2026, 4, 1)))
        assertEquals(2025, taxYearFor("AU", LocalDate.of(2026, 6, 30)))
        assertEquals(2026, taxYearFor("AU", LocalDate.of(2026, 7, 1)))
    }
    @Test fun actualCashDeductionsReduceNetOnlyAndMissingConfirmationNeverUsesAnotherCountry() {
        val profiles = listOf(profile("DE").copy(annualTaxableBase = 40000.0),
            profile("FR", 2025).copy(annualTaxableBase = 40000.0), profile("PT").copy(taxRegion = "PT-MAINLAND"),
            profile("AU").copy(auHospitalCover = "FULL"), profile("NZ").copy(nzIetcEligible = true))
        for (s in profiles) {
            val before = net(5000.0, s)
            val after = net(5000.0, s.copy(foreignMonthlyDeductions = 175.0))
            assertEquals(before.incomeTax, after.incomeTax, .00001)
            assertEquals(before.net - 175, after.net, .00001)
            assertNull(estimateNet(5000.0, 5000.0, s.copy(foreignMonthlyDeductions = null), s.taxYear))
            assertNull(estimateNet(5000.0, 5000.0, s.copy(foreignProfileCountry = "US"), s.taxYear))
            assertNull(estimateNet(5000.0, 5000.0, s, 2027))
            assertNull(estimateNet(Double.MAX_VALUE, Double.MAX_VALUE, s, s.taxYear))
        }
    }
    @Test fun countrySpecificSettingsRoundTripAndOldBackupsDoNotInventEligibilityOrBases() {
        val s = profile("AU").copy(annualTaxableBase = 40000.0, auHospitalCover = "FULL", nzIetcEligible = true)
        val original = AppData(settings = s)
        assertEquals(original, readBackup(backupText(original)))
        val legacy = JSONObject(encodeData(original)).put("version", 4)
        legacy.getJSONObject("settings").apply {
            remove("annualTaxableBase"); remove("auHospitalCover"); remove("nzIetcEligible")
        }
        val restored = decodeData(legacy.toString()).settings
        assertNull(restored.annualTaxableBase)
        assertEquals("", restored.auHospitalCover)
        assertFalse(restored.nzIetcEligible)
        assertEquals(DATA_VERSION, JSONObject(encodeData(original)).getInt("version"))
    }
}
