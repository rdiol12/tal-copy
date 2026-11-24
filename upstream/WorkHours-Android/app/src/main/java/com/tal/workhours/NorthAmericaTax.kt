package com.tal.workhours

import kotlin.math.*

/** Limited full-year resident, steady-salary scenarios; see TAX-SOURCES.md. */
fun estimateNorthAmericaNet(gross: Double, settings: AppSettings, year: Int): NetEstimate? {
    val country = settings.taxCountry
    if (country !in setOf("US", "CA") || !gross.isFinite() || gross < 0 || year != 2026 ||
        settings.taxYear != year || settings.foreignProfileCountry != country) return null
    val deductions = settings.foreignMonthlyDeductions ?: return null
    if (!deductions.isFinite() || deductions < 0 || deductions > gross) return null
    val annual = gross * 12
    return if (country == "US") estimateUsNet(annual, deductions, settings) else estimateCanadaNet(annual, deductions, settings)
}

private fun estimateUsNet(annual: Double, deductions: Double, settings: AppSettings): NetEstimate? {
    val state = estimateUsStateTax(annual, settings) ?: return null
    val joint = settings.usFilingStatus == "MFJ"
    val standardDeduction = if (joint) 32200.0 else 16100.0
    val caps = if (joint) listOf(24800.0, 100800.0, 211400.0, 403550.0, 512450.0, 768700.0, Double.POSITIVE_INFINITY)
        else listOf(12400.0, 50400.0, 105700.0, 201775.0, 256225.0, 640600.0, Double.POSITIVE_INFINITY)
    val tax = bandTax(max(0.0, annual - standardDeduction), caps, listOf(.10, .12, .22, .24, .32, .35, .37))
    val ss = min(annual, 184500.0) * .062
    // Final annual liability threshold, not the employer's $200,000 withholding trigger.
    val medicare = annual * .0145 + max(0.0, annual - if (joint) 250000.0 else 200000.0) * .009
    val net = (annual - tax - ss - medicare - state.annualTax) / 12 - state.localTax - deductions
    if (!net.isFinite() || net < -.005) return null
    return NetEstimate(tax / 12, ss / 12, medicare / 12, 0.0, 0.0, 0.0,
        max(0.0, net), otherDeductions = deductions, regionalTax = state.annualTax / 12,
        otherTax = state.localTax, personalCredit = state.appliedRelief / 12)
}

internal fun ontarioHealthPremium(income: Double): Double = when {
    income <= 20000 -> 0.0
    income <= 36000 -> min(300.0, (income - 20000) * .06)
    income <= 48000 -> min(450.0, 300 + (income - 36000) * .06)
    income <= 72000 -> min(600.0, 450 + (income - 48000) * .25)
    income <= 200000 -> min(750.0, 600 + (income - 72000) * .25)
    else -> min(900.0, 750 + (income - 200000) * .25)
}

private fun estimateCanadaNet(annual: Double, deductions: Double, settings: AppSettings): NetEstimate? {
    if (settings.taxRegion !in setOf("ON", "AB")) return null
    val pensionable = max(0.0, min(annual, 74600.0) - 3500.0)
    val cppBase = pensionable * .0495
    val cppEnhanced = pensionable * .01
    val cpp = cppBase + cppEnhanced
    val cpp2 = max(0.0, min(annual, 85000.0) - 74600.0) * .04
    val ei = min(annual, 68900.0) * .0163
    val taxable = max(0.0, annual - cppEnhanced - cpp2)
    val basicAmount = 16452.0 - 1623.0 * ((taxable - 181440.0) / (258482.0 - 181440.0)).coerceIn(0.0, 1.0)
    val federalBeforeCredits = bandTax(taxable,
        listOf(58523.0, 117045.0, 181440.0, 258482.0, Double.POSITIVE_INFINITY), listOf(.14, .205, .26, .29, .33))
    val federal = max(0.0, federalBeforeCredits - .14 * (basicAmount + min(annual, 1501.0) + cppBase + ei))
    val province: Double
    val health: Double
    if (settings.taxRegion == "ON") {
        val base = max(0.0, bandTax(taxable,
            listOf(53891.0, 107785.0, 150000.0, 220000.0, Double.POSITIVE_INFINITY), listOf(.0505, .0915, .1116, .1216, .1316)) -
            .0505 * (12989.0 + cppBase + ei))
        val surtax = max(0.0, base - 5818.0) * .20 + max(0.0, base - 7446.0) * .36
        val reduction = min(base + surtax, max(0.0, 600.0 - base - surtax))
        province = max(0.0, base + surtax - reduction)
        health = ontarioHealthPremium(taxable)
    } else {
        val credits = .08 * (22769.0 + cppBase + ei)
        val k5p = max(0.0, credits - 4896.0) * .25
        province = max(0.0, bandTax(taxable,
            listOf(61200.0, 154259.0, 185111.0, 246813.0, 370220.0, Double.POSITIVE_INFINITY), listOf(.08, .10, .12, .13, .14, .15)) - credits - k5p)
        health = 0.0
    }
    return NetEstimate(federal / 12, ei / 12, health / 12, cpp / 12, 0.0, 0.0,
        (annual - federal - province - health - cpp - cpp2 - ei) / 12 - deductions,
        otherDeductions = deductions, regionalTax = province / 12, secondPension = cpp2 / 12)
}
