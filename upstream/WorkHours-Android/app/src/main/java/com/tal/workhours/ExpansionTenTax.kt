package com.tal.workhours

import kotlin.math.abs
import kotlin.math.max

val expansionTenTaxCountries = setOf("PE", "CO", "EE", "LV", "LT", "BG", "RO", "HU", "GE", "AM")
const val PERU_UIT_2026 = 5500.0
const val COLOMBIA_UVT_2026 = 52374.0
const val LITHUANIA_36_VDU_2026 = 83237.40
const val LITHUANIA_60_VDU_2026 = 138729.0

/** Published Article241 starting quotas; do not replace their rounded UVT constants with continuous integration. */
internal fun colombiaTaxInUvt(base: Double): Double = when {
    base > 31000 -> (base - 31000) * .39 + 10352
    base > 18970 -> (base - 18970) * .37 + 5901
    base > 8670 -> (base - 8670) * .35 + 2296
    base > 4100 -> (base - 4100) * .33 + 788
    base > 1700 -> (base - 1700) * .28 + 116
    base > 1090 -> (base - 1090) * .19
    else -> 0.0
}

/** Tariffs only for the explicitly scoped ordinary employment profiles, not full national payroll. */
internal fun expansionTenAnnualTax(country: String, base: Double): Double = when (country) {
    "PE" -> bandTax(base, listOf(5.0, 20.0, 35.0, 45.0).map { it * PERU_UIT_2026 } + Double.POSITIVE_INFINITY,
        listOf(.08, .14, .17, .20, .30))
    "CO" -> colombiaTaxInUvt(base / COLOMBIA_UVT_2026) * COLOMBIA_UVT_2026
    "EE" -> base * .22
    "LV" -> base * .255 // gross <=105300: higher-rate/solidarity scenarios refused by estimator
    "LT" -> bandTax(base, listOf(LITHUANIA_36_VDU_2026, LITHUANIA_60_VDU_2026, Double.POSITIVE_INFINITY), listOf(.20, .25, .32))
    "BG", "RO" -> base * .10
    "HU" -> base * .15
    "GE", "AM" -> base * .20
    else -> error("Unsupported ordinary employment country")
}

/** Verified full-year figures drive a reserve/12; bonus/noncash income is never added to period cash payout. */
fun estimateExpansionTenNet(gross: Double, settings: AppSettings, year: Int): NetEstimate? {
    val country = settings.taxCountry
    if (country !in expansionTenTaxCountries || !supportsTaxYear(country, year) || settings.taxYear != year ||
        settings.foreignProfileCountry != country || !gross.isFinite() || gross < 0) return null
    val annual = settings.annualEmploymentGross ?: return null
    val base = settings.annualTaxableBase ?: return null
    val cash = settings.foreignMonthlyDeductions ?: return null
    if (!annual.isFinite() || annual !in 0.0..MAX_VERIFIED_ANNUAL_AMOUNT || annual < gross ||
        !base.isFinite() || base !in 0.0..annual || !cash.isFinite() || cash < 0 || cash > gross) return null
    val basicRelief = when (country) { "PE" -> 7 * PERU_UIT_2026; "EE" -> 8400.0; "LV" -> 6600.0; else -> 0.0 }
    if (base > max(0.0, annual - basicRelief) + .01) return null
    if (country == "LV" && annual > 105300.0) return null
    // Full ordinary taxable salary, no exemptions/allowances in these limited profiles. Insurance cash is post-tax.
    if (country in setOf("HU", "GE", "AM") && abs(base - annual) > .01) return null
    // Standard NPD only, no sickness/disability/other deductions. High-income profile has no base reduction.
    if (country == "LT" && annual > LITHUANIA_36_VDU_2026 && abs(base - annual) > .01) return null
    val tax = expansionTenAnnualTax(country, base) / 12
    if (tax + cash > gross + .01) return null
    return NetEstimate(tax, 0.0, 0.0, 0.0, 0.0, 0.0, max(0.0, gross - tax - cash), otherDeductions = cash)
}
