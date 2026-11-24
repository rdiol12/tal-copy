package com.tal.workhours

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

val expansionTwentyTaxCountries = setOf("AT", "CY", "MT", "GR", "SI", "SK", "HR", "RS", "ME", "AL", "BA", "NG", "GH", "TZ", "BW", "MU", "EG", "SZ", "LS", "JO")
val expansionTwentyMonthlyCountries = setOf("RS", "ME", "GH", "TZ")
val expansionTwentyAnnualCountries = expansionTwentyTaxCountries - expansionTwentyMonthlyCountries

internal fun verifiedPaymentDate(value: String): LocalDate? =
    if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) null else
        runCatching { LocalDate.parse(value) }.getOrNull()

/** FURS2026 published starting quotas retain the authority's cent-rounded values. */
internal fun slovenia2026AnnualTax(base: Double): Double = when {
    base <= 9721.43 -> base * .16
    base <= 28592.44 -> 1555.43 + (base - 9721.43) * .26
    base <= 57184.88 -> 6461.89 + (base - 28592.44) * .33
    base <= 82346.23 -> 15897.40 + (base - 57184.88) * .39
    else -> 25710.33 + (base - 82346.23) * .50
}

internal fun twentyAnnualBeforeRelief(country: String, base: Double): Double = when (country) {
    "AT" -> bandTax(base, listOf(13539.0, 21992.0, 36458.0, 70365.0, 104859.0, 1000000.0, Double.POSITIVE_INFINITY), listOf(0.0, .20, .30, .40, .48, .50, .55))
    "CY" -> bandTax(base, listOf(22000.0, 32000.0, 42000.0, 72000.0, Double.POSITIVE_INFINITY), listOf(0.0, .20, .25, .30, .35))
    "MT" -> bandTax(base, listOf(12000.0, 16000.0, 60000.0, Double.POSITIVE_INFINITY), listOf(0.0, .15, .25, .35))
    "GR" -> bandTax(base, listOf(10000.0, 20000.0, 30000.0, 40000.0, 60000.0, Double.POSITIVE_INFINITY), listOf(.09, .20, .26, .34, .39, .44))
    "SI" -> slovenia2026AnnualTax(base)
    "SK" -> bandTax(base, listOf(43983.32, 60349.21, 75010.32, Double.POSITIVE_INFINITY), listOf(.19, .25, .30, .35))
    "HR" -> bandTax(base, listOf(60000.0, Double.POSITIVE_INFINITY), listOf(.23, .33)) // Zagreb only.
    "AL" -> bandTax(base, listOf(2040000.0, Double.POSITIVE_INFINITY), listOf(.13, .23))
    "BA" -> base * .10 // Federation of Bosnia and Herzegovina ONLY.
    "NG" -> bandTax(base, listOf(800000.0, 3000000.0, 12000000.0, 25000000.0, 50000000.0, Double.POSITIVE_INFINITY), listOf(0.0, .15, .18, .21, .23, .25))
    "BW" -> bandTax(base, listOf(48000.0, 84000.0, 120000.0, 156000.0, 400000.0, Double.POSITIVE_INFINITY), listOf(0.0, .05, .125, .1875, .25, .275)) // Enacted1July2026.
    "MU" -> bandTax(base, listOf(500000.0, 1000000.0, Double.POSITIVE_INFINITY), listOf(0.0, .10, .20)) // Scope total<=12m; no high-income levies/rate.
    "EG" -> bandTax(floor(base / 10) * 10, listOf(40000.0, 55000.0, 70000.0, 200000.0, 400000.0, Double.POSITIVE_INFINITY), listOf(0.0, .10, .15, .20, .225, .25)) // Scope total<=600k; do not extend lost-band rules.
    "SZ" -> bandTax(base, listOf(100000.0, 150000.0, 200000.0, Double.POSITIVE_INFINITY), listOf(.20, .25, .30, .33))
    "LS" -> bandTax(base, listOf(77760.0, Double.POSITIVE_INFINITY), listOf(.20, .30))
    "JO" -> bandTax(floor(base), listOf(5000.0, 10000.0, 15000.0, 20000.0, 1000000.0, Double.POSITIVE_INFINITY), listOf(.05, .10, .15, .20, .25, .30))
    else -> error("Unsupported twenty-country annual profile")
}

internal fun twentyMonthlyTax(country: String, base: Double): Double = when (country) {
    "RS" -> base * .10 // Final verified base AFTER actual legal salary allowance; not another social deduction.
    "ME" -> bandTax(base, listOf(700.0, 1000.0, Double.POSITIVE_INFINITY), listOf(0.0, .09, .15))
    "GH" -> bandTax(base, listOf(588.0, 668.0, 768.0, 3668.0, 19668.0, 50000.0, Double.POSITIVE_INFINITY), listOf(0.0, .05, .10, .175, .25, .30, .35))
    "TZ" -> bandTax(base, listOf(270000.0, 520000.0, 760000.0, 1000000.0, Double.POSITIVE_INFINITY), listOf(0.0, .08, .20, .25, .30))
    else -> error("Unsupported twenty-country monthly profile")
}

/** Country-bound, explicitly confirmed ordinary employee scenarios; no guessed insurance or eligibility. */
fun estimateExpansionTwentyNet(gross: Double, settings: AppSettings, year: Int, periodEnd: LocalDate? = null): NetEstimate? {
    val c = settings.taxCountry
    if (c !in expansionTwentyTaxCountries || settings.foreignProfileCountry != c ||
        !supportsTaxYear(c, year) || settings.taxYear != year || !gross.isFinite() || gross !in 0.0..1_000_000_000.0) return null
    val cash = settings.foreignMonthlyDeductions ?: return null
    if (!cash.isFinite() || cash !in 0.0..gross) return null
    val neededRegion = when (c) { "HR" -> "HR-ZAGREB"; "BA" -> "BA-FBIH"; "TZ" -> "TZ-MAINLAND"; else -> null }
    if (neededRegion != null && settings.taxRegion != neededRegion) return null
    var relief = 0.0
    var otherTax = 0.0
    val tax = if (c in expansionTwentyMonthlyCountries) {
        if (c == "GH") {
            val paid = verifiedPaymentDate(settings.taxPaymentDate) ?: return null
            // Only the enacted post1September table; actual date and viewed month must both agree.
            if (paid < LocalDate.of(2026, 9, 1) || paid > LocalDate.of(2026, 12, 31) || periodEnd == null ||
                YearMonth.from(paid) != YearMonth.from(periodEnd) || paid.year != year) return null
        }
        val base = settings.monthlyTaxableBase ?: return null
        if (!base.isFinite() || base !in 0.0..gross) return null
        // Ordinary fully taxable Montenegro salary before employee contributions. Municipal surtax is EMPLOYER cost.
        if (c == "ME" && abs(base - gross) > .01) return null
        twentyMonthlyTax(c, base)
    } else {
        val annual = settings.annualEmploymentGross ?: return null
        val base = settings.annualTaxableBase ?: return null
        if (!annual.isFinite() || annual !in 0.0..MAX_VERIFIED_ANNUAL_AMOUNT || annual < gross ||
            !base.isFinite() || base !in 0.0..annual) return null
        if (c == "MU" && annual > 12000000.0) return null
        if (c == "EG" && (annual > 600000.0 || base > max(0.0, annual - 20000.0) + .01)) return null
        if (c == "HR" && base > max(0.0, annual - 7200.0) + .01) return null
        if (c == "BA" && base > max(0.0, annual - 3600.0) + .01) return null
        if (c == "JO" && base > max(0.0, annual - 9000.0) + .01) return null
        if (c == "AL") {
            val allowance = when { annual <= 600000.0 -> 600000.0; annual <= 720000.0 -> 420000.0; else -> 360000.0 }
            if (base > max(0.0, annual - allowance) + .01) return null
        }
        // Initial Nigerian profile is twelve equal fully taxable CASH monthly salaries, no bonuses/benefits/other income.
        if (c == "NG" && abs(annual - 12 * gross) > .01) return null
        val before = twentyAnnualBeforeRelief(c, base)
        val credit = when (c) {
            "AT" -> 496.0 + when { base <= 19761.0 -> 804.0; base < 30259.0 -> 804.0 * (30259.0 - base) / (30259.0 - 19761.0); else -> 0.0 }
            "GR" -> max(0.0, 777.0 - max(0.0, base - 12000.0) * .02)
            "SZ" -> 8200.0
            "LS" -> 12240.0
            else -> 0.0
        }
        relief = minOf(before, credit) / 12
        if (c == "JO") otherTax = floor(max(0.0, floor(base) - 200000.0) * .01) / 12
        if (c == "NG" && gross <= 70000.0) 0.0 else {
            val due = max(0.0, before - credit)
            // JordanArt11(e): drop fractions of a dinar for annual tax, not period cash contributions.
            (if (c == "JO") floor(due) else due) / 12
        }
    }
    if (tax + otherTax + cash > gross + .01) return null
    return NetEstimate(tax, 0.0, 0.0, 0.0, 0.0, 0.0, max(0.0, gross - tax - otherTax - cash),
        otherTax = otherTax, otherDeductions = cash, personalCredit = relief)
}
