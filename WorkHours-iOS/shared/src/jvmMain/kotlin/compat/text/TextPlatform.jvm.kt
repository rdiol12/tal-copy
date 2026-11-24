package compat.text

import java.util.Locale

actual fun platformStripDiacritics(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
actual fun platformNormalize(value: String, form: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.valueOf(form))
actual fun platformCollate(a: String, b: String, languageTag: String): Int =
    java.text.Collator.getInstance(Locale.forLanguageTag(languageTag)).compare(a, b)
actual fun platformFormatCurrency(value: Double, currencyCode: String, minFraction: Int, maxFraction: Int, languageTag: String): String =
    java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag(languageTag)).apply {
        currency = java.util.Currency.getInstance(currencyCode)
        minimumFractionDigits = minFraction
        maximumFractionDigits = maxFraction
    }.format(value)
actual fun platformFormatNumber(value: Double, minFraction: Int, maxFraction: Int, grouping: Boolean, languageTag: String): String =
    java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag(languageTag)).apply {
        minimumFractionDigits = minFraction
        maximumFractionDigits = maxFraction
        isGroupingUsed = grouping
    }.format(value)
