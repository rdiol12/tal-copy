package com.tal.workhours

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExpansionFifteenTaxTest {
    // Conditional entered payroll scenarios, NOT statutory insurance-rate examples.
    private data class Scenario(val c: String, val gross: Double, val annual: Double?, val base: Double?, val cash: Double, val monthly: Double? = null)
    private val cases = listOf(
        Scenario("MX", 30000.0, 420000.0, 360000.0, 1500.0),
        Scenario("BR", 6000.0, 84000.0, null, 650.0, 5392.80),
        Scenario("CR", 2000000.0, null, null, 220000.0),
        Scenario("GT", 8000.0, 112000.0, 55000.0, 400.0),
        Scenario("DO", 60000.0, 840000.0, 780000.0, 3000.0),
        Scenario("KE", 100000.0, 1300000.0, 1100000.0, 15000.0),
        Scenario("RW", 500000.0, 6500000.0, 6000000.0, 35000.0),
        Scenario("UG", 500000.0, 6000000.0, 6000000.0, 25000.0),
        Scenario("ZM", 15000.0, 180000.0, 180000.0, 1500.0),
        Scenario("PK", 300000.0, 3900000.0, 3900000.0, 10000.0),
        Scenario("UZ", 10000000.0, 130000000.0, 130000000.0, 100000.0),
        Scenario("KZ", 500000.0, 6500000.0, 4200000.0, 60000.0),
        Scenario("MA", 12000.0, 156000.0, 110000.0, 800.0),
        Scenario("MD", 20000.0, 260000.0, 200000.0, 1800.0),
        Scenario("MK", 60000.0, 780000.0, 430416.0, 16800.0))
    private fun profile(s: Scenario) = AppSettings(taxCountry = s.c, foreignProfileCountry = s.c,
        annualEmploymentGross = s.annual, annualTaxableBase = s.base,
        monthlyTaxableBase = s.monthly, foreignMonthlyDeductions = s.cash)
    private fun net(s: Scenario, p: AppSettings = profile(s)) = estimateNet(s.gross, s.gross, p, 2026)
    private fun payout(i: Int, tax: Double, expected: Double) {
        val n = net(cases[i])!!
        assertEquals(tax, n.incomeTax, .0001)
        assertEquals(expected, n.net, .0001)
        assertEquals(cases[i].cash, n.otherDeductions, .0001)
        assertEquals(0.0, n.pensionCredit, 0.0)
    }
    private fun quotas(c: String, points: List<Pair<Double, Double>>) = points.forEach { (b,t) ->
        assertEquals("$c base=$b", t, expansionFifteenAnnualBeforeRelief(c,b), .0001)
    }

    @Test fun mexico2026PublishedLowerLimitsAndFixedQuotas() {
        for ((b,t) in listOf(0.0 to 0.0, .01 to 0.0, 10135.12 to 194.59, 86022.12 to 5051.37,
            151176.20 to 12140.13, 175735.67 to 16069.64, 210403.70 to 22282.14,
            424353.98 to 67981.92, 668840.15 to 125485.07, 1276925.99 to 307910.81,
            1702567.98 to 444116.23, 5107703.93 to 1601862.46))
            assertEquals(t, mexico2026AnnualTax(b), .0001)
        assertEquals(194.59392, mexico2026AnnualTax(10135.11), .0001)
        assertEquals(1602212.46, mexico2026AnnualTax(5108703.93), .0001)
    }
    @Test fun dominicanPublishedQuotasAreNotSmoothedIntoAnotherTariff() {
        for ((b,t) in listOf(416220.0 to 0.0, 416220.01 to 0.0, 416221.01 to .15,
            624329.01 to 31216.0, 624330.01 to 31216.20,
            867123.01 to 79776.0, 867124.01 to 79776.25))
            assertEquals(t, dominican2026AnnualTax(b), .0001)
    }
    @Test fun guatemalaOrdinaryTwoBandQuotas() = quotas("GT", listOf(0.0 to 0.0, 300000.0 to 15000.0, 400000.0 to 22000.0))
    @Test fun kenyaFiveBandsBeforePersonalRelief() = quotas("KE", listOf(0.0 to 0.0, 288000.0 to 28800.0,
        388000.0 to 53800.0, 6000000.0 to 1737400.0, 9600000.0 to 2907400.0, 10000000.0 to 3047400.0))
    @Test fun rwandaOrdinaryAnnualBandsAreNotTheCasualLabourTariff() = quotas("RW", listOf(0.0 to 0.0,
        720000.0 to 0.0, 1200000.0 to 48000.0, 2400000.0 to 288000.0, 3600000.0 to 648000.0))
    @Test fun ugandaJuly2026NewBandsAndAdditionalTenPercent() = quotas("UG", listOf(0.0 to 0.0,
        4020000.0 to 0.0, 4920000.0 to 180000.0, 5820000.0 to 405000.0,
        6000000.0 to 459000.0, 120000000.0 to 34659000.0, 121200000.0 to 35139000.0))
    @Test fun zambia2026AnnualisedBandsMatchPublishedMonthlyFifteenThousandExample() = quotas("ZM", listOf(
        61200.0 to 0.0, 85200.0 to 4800.0, 110400.0 to 12360.0, 180000.0 to 38112.0))
    @Test fun pakistanEnacted2026BandsIncludeBothNewIntermediateSteps() = quotas("PK", listOf(
        600000.0 to 0.0, 1200000.0 to 6000.0, 2200000.0 to 116000.0,
        3200000.0 to 316000.0, 4100000.0 to 541000.0, 5600000.0 to 976000.0,
        7000000.0 to 1424000.0, 10000000.0 to 2474000.0))
    @Test fun kazakhstanNewMciThresholdAndFifteenPercentExcess() {
        assertEquals(4325.0, KAZAKHSTAN_MCI_2026, 0.0)
        quotas("KZ", listOf(36762500.0 to 3676250.0, 37762500.0 to 3826250.0))
    }
    @Test fun morocco2026SixBandQuotasHaveThirtySevenNotThirtyEightTopRate() = quotas("MA", listOf(
        40000.0 to 0.0, 60000.0 to 2000.0, 80000.0 to 6000.0, 100000.0 to 12000.0,
        180000.0 to 39200.0, 200000.0 to 46600.0))
    @Test fun uzbekMoldovanAndMacedonianOrdinaryRates() {
        quotas("UZ",listOf(100000.0 to 12000.0))
        quotas("MD",listOf(100000.0 to 12000.0))
        quotas("MK",listOf(100000.0 to 10000.0))
    }
    @Test fun brazilOfficialSixThousandSimplifiedBaseExample() {
        val (tax, reduction) = brazil2026MonthlyTax(6000.0,5392.80)
        assertEquals(394.54,tax,.0001)
        assertEquals(179.75,reduction,.0001)
    }
    @Test fun brazilChoosesLowerWholeBaseWithoutCombiningSimplifiedAndActualDeductions() {
        val ordinary = brazil2026MonthlyTax(6000.0,5392.80)
        assertEquals(ordinary,brazil2026MonthlyTax(6000.0,6000.0)) // simplified option wins
        val actual = brazil2026MonthlyTax(6000.0,5000.0)
        assertEquals(286.52,actual.first,.0001) // 5000*.275-908.73-179.75, no further607.20 deduction
        assertEquals(179.75,actual.second,.0001)
    }
    @Test fun brazilLowPayReductionCannotCreateARefundAndFiveThousandIsZero() {
        for (g in listOf(0.0,1000.0,3000.0,4000.0,5000.0)) {
            val (tax,credit)=brazil2026MonthlyTax(g,g)
            assertEquals(0.0,tax,.0001);assertTrue(credit>=0 && credit<=312.89+.00001)
        }
        assertEquals(0.0,brazil2026MonthlyTax(7350.0,6742.80).second,.0001)
        assertEquals(0.0,brazil2026MonthlyTax(8000.0,7000.0).second,.0001)
    }
    @Test fun costaRicaUses2026SalaryBandsBeforeSocialDeductions() {
        for ((g,t) in listOf(918000.0 to 0.0,1347000.0 to 42900.0,2364000.0 to 195450.0,
            4727000.0 to 668050.0,5000000.0 to 736300.0)) {
            val p=profile(cases[2]).copy(foreignMonthlyDeductions=0.0)
            assertEquals(t,estimateNet(g,g,p,2026)!!.incomeTax,.0001)
        }
    }
    @Test fun mexicoAnnualVerifiedBasePayout() = payout(0,4519.65914,23980.34086)
    @Test fun brazilMonthlyPayoutIncludesNewReduction() = payout(1,394.54,4955.46)
    @Test fun costaRicaMonthlyPayoutDoesNotDeductSocialFromTaxBase() = payout(2,140850.0,1639150.0)
    @Test fun guatemalaFinalBaseAlreadyContainsTheExtra2026Deduction() = payout(3,2750.0/12,7370.833333)
    @Test fun dominicanAnnualFinalBasePayout() = payout(4,62350.198/12,51804.150167)
    @Test fun kenyaPayoutContainsResidentPersonalReliefOnce() {
        payout(5,238600.0/12,65116.666667)
        assertEquals(2400.0,net(cases[5])!!.personalCredit,.0001)
    }
    @Test fun rwandaOrdinarySalaryPayout() = payout(6,114000.0,351000.0)
    @Test fun ugandaFiveHundredThousandUsesNewThirtyEightThousandTax() = payout(7,38250.0,436750.0)
    @Test fun zambiaPublishedBandPayoutWithEnteredCash() = payout(8,3176.0,10324.0)
    @Test fun pakistan2026TwentyFivePercentBandPayout() = payout(9,491000.0/12,249083.333333)
    @Test fun uzbekistanReserveIncludesEmbeddedPensionAllocation() = payout(10,1300000.0,8600000.0)
    @Test fun kazakhstanVerifiedFullYearDeductionsPayout() = payout(11,35000.0,405000.0)
    @Test fun moroccoFinalProfessionalExpenseBasePayout() = payout(12,15400.0/12,9916.666667)
    @Test fun moldovaOrdinaryAllowanceBasePayout() = payout(13,2000.0,16200.0)
    @Test fun macedoniaOrdinaryAllowanceAndDuesAreNotRepeated() = payout(14,3586.8,39613.2)
    @Test fun allFifteenCashChangesOnlyPayoutNotTaxOrIncludedRelief() {
        for(s in cases) {
            val a=net(s)!!;val b=net(s,profile(s).copy(foreignMonthlyDeductions=s.cash+100))!!
            assertEquals(a.incomeTax,b.incomeTax,.0001);assertEquals(a.personalCredit,b.personalCredit,.0001)
            assertEquals(a.net-100,b.net,.0001)
            assertEquals(s.gross-b.incomeTax-b.otherDeductions,b.net,.0001)
            assertEquals(0.0,b.pension+b.nationalInsurance+b.healthInsurance+b.pensionCredit,.0001)
        }
    }
    @Test fun kenyaLowIncomePersonalReliefIsLimitedToTaxAndDoesNotRefundInsurance() {
        val p=profile(cases[5]).copy(annualEmploymentGross=240000.0,annualTaxableBase=240000.0,foreignMonthlyDeductions=1000.0)
        val n=estimateNet(20000.0,20000.0,p,2026)!!
        assertEquals(0.0,n.incomeTax,.0001);assertEquals(2000.0,n.personalCredit,.0001)
        assertEquals(19000.0,n.net,.0001)
    }
    @Test fun mexicoSubsidyScopeRefusesLowAverageBaseRatherThanIgnoringRelief() {
        val p=profile(cases[0]).copy(annualEmploymentGross=180000.0,annualTaxableBase=137911.92)
        assertNull(net(cases[0],p))
        assertNull(net(cases[0],p.copy(annualTaxableBase=137911.91)))
        assertNotNull(net(cases[0],p.copy(annualTaxableBase=137911.93)))
        assertNotNull(net(cases[0],p.copy(annualTaxableBase=137912.0)))
    }
    @Test fun brazilRequiresVerifiedMonthlyBaseAndNeverUsesAnnualBaseAsItsSubstitute() {
        val s=cases[1]
        assertNull(net(s,profile(s).copy(monthlyTaxableBase=null,annualTaxableBase=50000.0)))
        assertEquals(net(s),net(s,profile(s).copy(annualTaxableBase=10000.0)))
        for(b in listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY,s.gross+1))
            assertNull(net(s,profile(s).copy(monthlyTaxableBase=b)))
    }
    @Test fun brazilConservativeAnnualLimitIncludesSeparateThirteenthPayButDoesNotTaxItHere() {
        val s=cases[1];val p=profile(s).copy(annualEmploymentGross=600000.0)
        assertEquals(net(s),net(s,p)) // monthly gross/base drive ordinary monthly tax
        assertNull(net(s,p.copy(annualEmploymentGross=600000.01)))
        assertNull(net(s,p.copy(annualEmploymentGross=null)))
    }
    @Test fun costaRicaNeedsNoInventedAnnualOrMonthlyTaxBase() {
        val s=cases[2]
        assertNotNull(net(s))
        assertEquals(net(s),net(s,profile(s).copy(annualEmploymentGross=24000000.0,
            annualTaxableBase=10000000.0,monthlyTaxableBase=1000000.0)))
    }
    @Test fun verifiedAnnualBaseAndIncomeAreRequiredForAllThirteenAnnualProfiles() {
        for(s in cases.filter{it.c in expansionFifteenAnnualCountries}) {
            for(a in listOf(null,-1.0,Double.NaN,Double.POSITIVE_INFINITY,s.gross-1,MAX_VERIFIED_ANNUAL_AMOUNT+1))
                assertNull(net(s,profile(s).copy(annualEmploymentGross=a)))
            for(b in listOf(null,-1.0,Double.NaN,Double.POSITIVE_INFINITY,s.annual!!+1))
                assertNull(net(s,profile(s).copy(annualTaxableBase=b)))
        }
    }
    @Test fun basicAllowancesMustAlreadyBeReflectedInVerifiedBases() {
        for(s in cases.filter{it.c in setOf("GT","KZ","MD","MK")})
            assertNull(net(s,profile(s).copy(annualTaxableBase=s.annual)))
        val g=cases[3]
        assertNull(net(g,profile(g).copy(annualTaxableBase=g.annual!!-48000))) // misses extra3024
        assertNotNull(net(g,profile(g).copy(annualTaxableBase=g.annual!!-51024)))
    }
    @Test fun moldovaPakistanUzbekistanAndUgandaScopeGatesAreEnforced() {
        assertNull(net(cases[13],profile(cases[13]).copy(annualEmploymentGross=360000.0)))
        assertNotNull(net(cases[13],profile(cases[13]).copy(annualEmploymentGross=359999.99)))
        assertNull(net(cases[9],profile(cases[9]).copy(annualEmploymentGross=10000000.01)))
        assertNull(net(cases[10],profile(cases[10]).copy(annualTaxableBase=129999000.0)))
        assertNull(net(cases[7],profile(cases[7]).copy(annualEmploymentGross=6500000.0)))
    }
    @Test fun cashProfileYearBadGrossAndImpossibleNetNeverProduceAnEstimate() {
        for(s in cases) {
            for(c in listOf(null,-1.0,Double.NaN,Double.POSITIVE_INFINITY,s.gross+1,s.gross))
                assertNull(net(s,profile(s).copy(foreignMonthlyDeductions=c)))
            for(g in listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE,1000000001.0))
                assertNull(estimateNet(g,g,profile(s),2026))
            for(c in listOf("","IL","GB")) assertNull(net(s,profile(s).copy(foreignProfileCountry=c)))
            for(y in listOf(2025,2027)) {
                assertFalse(supportsTaxYear(s.c,y))
                assertNull(estimateNet(s.gross,s.gross,profile(s).copy(taxYear=y),y))
            }
            assertNull(net(s,profile(s).copy(taxYear=2025)))
        }
    }
    @Test fun lowIncomeHasNoInventedRefundOrCashExemptionAndZeroProfilesWork() {
        for(s in cases) {
            val base=if(s.c in setOf("GT","KZ","MD","MK"))0.0 else 1200.0
            val p=profile(s).copy(annualEmploymentGross=1200.0,annualTaxableBase=base,
                monthlyTaxableBase=100.0,foreignMonthlyDeductions=10.0)
            val n=estimateNet(100.0,100.0,p,2026)
            if(s.c=="MX") assertNull(n) else {
                assertNotNull(n);assertTrue(n!!.net in 0.0..90.0)
                assertEquals(10.0,n.otherDeductions,.0001)
            }
            val zero=p.copy(annualEmploymentGross=0.0,annualTaxableBase=0.0,
                monthlyTaxableBase=0.0,foreignMonthlyDeductions=0.0)
            assertEquals(0.0,estimateNet(0.0,0.0,zero,2026)!!.net,.0001)
        }
    }
    @Test fun fiscalYearStartsJulyForUgandaAndPakistanOthersUseCalendarYear() {
        for(c in setOf("UG","PK")) {
            assertEquals(2025,taxYearFor(c,LocalDate.of(2026,6,30)))
            assertEquals(2026,taxYearFor(c,LocalDate.of(2026,7,1)))
            assertEquals(2026,taxYearFor(c,LocalDate.of(2027,6,30)))
            assertEquals(2027,taxYearFor(c,LocalDate.of(2027,7,1)))
        }
        for(c in expansionFifteenTaxCountries-setOf("UG","PK")) {
            assertEquals(2026,taxYearFor(c,LocalDate.of(2026,1,1)))
            assertEquals(2027,taxYearFor(c,LocalDate.of(2027,1,1)))
        }
    }
    @Test fun allFiftyFiveSelectorsHaveUniqueCodesAndCorrectCurrencies() {
        assertEquals(supportedTaxCountries.size,taxCountries.size);assertEquals(supportedTaxCountries,taxCountries.map{it.code}.toSet())
        for((c,m) in listOf("MX" to "MXN","BR" to "BRL","CR" to "CRC","GT" to "GTQ","DO" to "DOP",
            "KE" to "KES","RW" to "RWF","UG" to "UGX","ZM" to "ZMW","PK" to "PKR",
            "UZ" to "UZS","KZ" to "KZT","MA" to "MAD","MD" to "MDL","MK" to "MKD")) {
            assertEquals(m,taxCurrency(c));assertTrue(c in foreignTaxCountries)
            assertEquals(c in expansionFifteenAnnualCountries,c in verifiedBaseTaxCountries)
        }
    }
    @Test fun allFifteenFullSettingsSurviveBothLocalAndDrivePayloads() {
        for(s in cases) {
            val original=AppData(settings=profile(s).copy(reasons=listOf("Remote support")))
            original.settings.validate(allowUnconfigured=true)
            assertEquals(original,decodeData(encodeData(original)))
            assertEquals(original,readBackup(backupText(original)))
        }
        assertTrue(DATA_VERSION>=14)
    }
    @Test fun legacyV13DoesNotInventMonthlyBaseAndPreviousAnnualProfilesStillWork() {
        val s=AppSettings(taxCountry="PE",foreignProfileCountry="PE",annualEmploymentGross=112000.0,
            annualTaxableBase=73500.0,foreignMonthlyDeductions=1000.0)
        val data=AppData(settings=s)
        val json=JSONObject(encodeData(data)).put("version",13)
        json.getJSONObject("settings").remove("monthlyTaxableBase")
        val restored=decodeData(json.toString())
        assertEquals(data,restored);assertNull(restored.settings.monthlyTaxableBase)
        assertEquals(6280.0,estimateNet(8000.0,8000.0,restored.settings,2026)!!.net,.0001)
        json.put("version",DATA_VERSION+1)
        assertThrows(IllegalArgumentException::class.java){decodeData(json.toString())}
    }
    @Test fun monthlyFieldRejectsNonfiniteOverflowAndFreshInstallHasNoTaxProfile() {
        val fresh=AppData();fresh.validate()
        assertNull(fresh.settings.monthlyTaxableBase);assertNull(fresh.settings.annualEmploymentGross)
        assertNull(fresh.settings.annualTaxableBase);assertNull(fresh.settings.foreignMonthlyDeductions)
        assertEquals("",fresh.settings.foreignProfileCountry)
        assertEquals(fresh,readBackup(backupText(fresh)))
        for(v in listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY,1000000001.0))
            assertThrows(IllegalArgumentException::class.java){profile(cases[1]).copy(monthlyTaxableBase=v).validate(true)}
    }
}
