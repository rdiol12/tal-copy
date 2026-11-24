package com.tal.workhours

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

val additionalFifteenTaxCountries = setOf("PA", "NI", "SV", "HN", "PY", "EC", "NA", "KH", "AZ", "BH", "KW", "OM", "QA", "BN", "BS")
val additionalAnnualTaxCountries = setOf("PA", "NI", "HN", "PY", "EC", "NA")
val additionalMonthlyBaseCountries = setOf("SV", "KH")
val additionalZeroSalaryTaxCountries = setOf("BH", "KW", "OM", "QA", "BN", "BS")

/** SRI2026 published starting quotas, not continuous integration of rounded amounts. */
internal fun ecuador2026AnnualTax(base: Double): Double {
    val lower = listOf(0.0, 12208.0, 15549.0, 20188.0, 26700.0, 35136.0, 46575.0, 62005.0, 82679.0, 109956.0)
    val quota = listOf(0.0, 0.0, 167.0, 631.0, 1412.0, 2678.0, 4965.0, 8823.0, 15025.0, 24572.0)
    val rate = listOf(0.0, .05, .10, .12, .15, .20, .25, .30, .35, .37)
    val i = lower.indexOfLast { base >= it }.coerceAtLeast(0)
    return quota[i] + max(0.0, base - lower[i]) * rate[i]
}

/** Mainland/no-dependent ordinary profile only. Expenses are a credit basis, NOT a taxable-base deduction. */
internal fun ecuador2026ExpenseRebate(expenses: Double): Double = BigDecimal.valueOf(minOf(expenses, 5752.60))
    .multiply(BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP).toDouble()

internal fun additionalAnnualBeforeRelief(country: String, base: Double): Double = when (country) {
    "PA" -> bandTax(base, listOf(11000.0, 50000.0, Double.POSITIVE_INFINITY), listOf(0.0, .15, .25))
    "NI" -> bandTax(base, listOf(100000.0, 200000.0, 350000.0, 500000.0, Double.POSITIVE_INFINITY), listOf(0.0, .15, .20, .25, .30))
    "HN" -> bandTax(base, listOf(228324.32, 348154.10, 809660.75, Double.POSITIVE_INFINITY), listOf(0.0, .15, .20, .25))
    "PY" -> bandTax(base, listOf(50000000.0, 150000000.0, Double.POSITIVE_INFINITY), listOf(.08, .09, .10))
    "EC" -> ecuador2026AnnualTax(base)
    "NA" -> bandTax(base, listOf(100000.0, 150000.0, 350000.0, 550000.0, 850000.0, 1550000.0, Double.POSITIVE_INFINITY), listOf(0.0, .18, .25, .28, .30, .32, .37))
    else -> error("Unsupported annual salary profile")
}

/** Ordinary monthly table only; no June/December cumulative or annual settlement calculation. */
internal fun elSalvadorMonthlyWithholding(base: Double): Double = when {
    base > 2038.10 -> 288.57 + (base - 2038.10) * .30
    base > 895.24 -> 60.0 + (base - 895.24) * .20
    base > 550.0 -> 17.67 + (base - 550.0) * .10
    else -> 0.0
}

/** Private non-oil/non-gas main employment only; employee insurance is separate cash, not another PIT deduction. */
internal fun azerbaijan2026Monthly(gross: Double): Pair<Double, Double> = when {
    gross <= 2500.0 -> max(0.0, gross - 200.0) * .03 to minOf(gross, 200.0) * .03
    gross <= 8000.0 -> (75.0 + (gross - 2500.0) * .10) to 0.0
    else -> (625.0 + (gross - 8000.0) * .14) to 0.0
}

fun estimateAdditionalFifteenNet(gross: Double, settings: AppSettings, year: Int): NetEstimate? {
    val country = settings.taxCountry
    if (country !in additionalFifteenTaxCountries || settings.foreignProfileCountry != country ||
        !supportsTaxYear(country, year) || settings.taxYear != year || !gross.isFinite() || gross !in 0.0..1_000_000_000.0) return null
    val cash = settings.foreignMonthlyDeductions ?: return null
    if (!cash.isFinite() || cash < 0 || cash > gross) return null
    var includedRelief = 0.0
    val tax = when {
        // Explicitly verified ordinary local SALARY scope, not an unknown-country zero-tax fallback.
        country in additionalZeroSalaryTaxCountries -> 0.0
        country == "AZ" -> azerbaijan2026Monthly(gross).also { includedRelief = it.second }.first
        country in additionalMonthlyBaseCountries -> {
            val base = settings.monthlyTaxableBase ?: return null
            if (!base.isFinite() || base !in 0.0..gross) return null
            if (country == "SV") elSalvadorMonthlyWithholding(base) else
                bandTax(base, listOf(1500000.0, 2000000.0, 8500000.0, 12500000.0, Double.POSITIVE_INFINITY), listOf(0.0, .05, .10, .15, .20))
        }
        else -> {
            val annual = settings.annualEmploymentGross ?: return null
            val base = settings.annualTaxableBase ?: return null
            if (!annual.isFinite() || annual !in 0.0..MAX_VERIFIED_ANNUAL_AMOUNT || annual < gross ||
                !base.isFinite() || base !in 0.0..annual) return null
            // Full ordinary40k education/medical allowance must already be independently verified.
            if (country == "HN" && base > max(0.0, annual - 40000.0) + .01) return null
            if (country == "PY") {
                // Pre-existing IRP-RSP registration before2026 is part of explicit confirmed scope.
                val receipts = settings.pyAnnualTaxableReceipts ?: return null
                if (!receipts.isFinite() || receipts !in 0.0..annual || base > receipts) return null
                // Aguinaldo/exempt receipts belong in total annual employment income but NOT this threshold.
                if (receipts <= 80000000.0) 0.0 else additionalAnnualBeforeRelief(country, base) / 12
            } else {
                val before = additionalAnnualBeforeRelief(country, base)
                if (country == "EC") {
                    if (settings.taxRegion != "EC-MAINLAND") return null
                    val expenses = settings.ecAnnualPersonalExpenses ?: return null
                    if (!expenses.isFinite() || expenses !in 0.0..MAX_VERIFIED_ANNUAL_AMOUNT) return null
                    includedRelief = minOf(before, ecuador2026ExpenseRebate(expenses)) / 12
                }
                before / 12 - includedRelief
            }
        }
    }
    if (tax + cash > gross + .01) return null
    return NetEstimate(tax, 0.0, 0.0, 0.0, 0.0, 0.0, max(0.0, gross - tax - cash),
        otherDeductions = cash, personalCredit = includedRelief)
}
