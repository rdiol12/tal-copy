package com.tal.workhours

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CountryTaxTest {
    private fun uk(region: String = "ENG") = AppSettings(taxCountry = "GB", taxRegion = region, ukStandardProfile = true)
    @Test fun englandMonthlyTaxAndInsurance() {
        val net = estimateNet(3000.0, 3000.0, uk(), 2026)!!
        assertEquals(390.5, net.incomeTax, .00001)
        assertEquals(156.16, net.nationalInsurance, .00001)
        assertEquals(2453.34, net.net, .00001)
    }
    @Test fun highIncomeAllowanceTapers() {
        val net = estimateNet(10000.0, 10000.0, uk(), 2026)!!
        assertEquals(3286.0, net.incomeTax, .00001)
        assertEquals(367.5, net.nationalInsurance, .00001)
        assertEquals(6346.5, net.net, .00001)
    }
    @Test fun scotlandHasDistinctBandsAndWalesMatchesEngland() {
        val english = estimateNet(3000.0, 3000.0, uk(), 2026)!!
        val scottish = estimateNet(3000.0, 3000.0, uk("SCT"), 2026)!!
        assertEquals(4711.07 / 12, scottish.incomeTax, .00001)
        assertEquals(english, estimateNet(3000.0, 3000.0, uk("WLS"), 2026))
        assertEquals(english, estimateNet(3000.0, 3000.0, uk("NIR"), 2026))
    }
    @Test fun unsupportedCountryYearAndMissingProfileNeverUseIsraeliTax() {
        val profile = AppSettings(creditPoints = 2.25, pensionPercent = 6.0)
        for (country in taxCountries.filter { it.code !in setOf("IL", "GB") })
            assertNull(estimateNet(12000.0, 12000.0, profile.copy(taxCountry = country.code), 2026))
        assertNull(estimateNet(3000.0, 3000.0, uk().copy(ukStandardProfile = false), 2026))
        assertNull(estimateNet(3000.0, 3000.0, uk(""), 2026))
        assertNull(estimateNet(3000.0, 3000.0, uk(), 2027))
        assertNull(estimateNet(3000.0, 3000.0, uk().copy(taxYear = 2025), 2026))
    }
    @Test fun ukTaxYearSwitchesOnAprilSixth() {
        assertEquals(2025, taxYearFor("GB", LocalDate.of(2026, 4, 5)))
        assertEquals(2026, taxYearFor("GB", LocalDate.of(2026, 4, 6)))
        assertEquals(2026, taxYearFor("IL", LocalDate.of(2026, 1, 1)))
    }
    @Test fun thresholdsHaveNoNegativeTaxOrInsurance() {
        val net = estimateNet(1000.0, 1000.0, uk(), 2026)!!
        assertEquals(1000.0, net.net, .00001)
        assertEquals(0.0, estimateNet(0.0, 0.0, uk(), 2026)!!.net, .00001)
    }
    @Test fun countryProfileSurvivesBackupAndLegacyDefaultsStayIsraeli() {
        val original = AppData(settings = uk("SCT"))
        assertEquals(original, readBackup(backupText(original)))
        val old = org.json.JSONObject(encodeData(original)).put("version", 2)
        val settings = old.getJSONObject("settings")
        listOf("taxCountry", "taxYear", "taxRegion", "ukStandardProfile").forEach { settings.remove(it) }
        val restored = decodeData(old.toString()).settings
        assertEquals("IL", restored.taxCountry)
        assertEquals(2026, restored.taxYear)
        assertFalse(restored.ukStandardProfile)
    }
}
