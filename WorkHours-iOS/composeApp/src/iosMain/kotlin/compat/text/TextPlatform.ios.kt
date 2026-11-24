package compat.text

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSLocale
import platform.Foundation.NSMakeRange
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.compare
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.stringByFoldingWithOptions

@Suppress("CAST_NEVER_SUCCEEDS")
private fun String.ns(): NSString = this as NSString

private fun locale(tag: String): NSLocale = NSLocale(localeIdentifier = if (tag.isEmpty() || tag == "und") "en_US_POSIX" else tag)

actual fun platformStripDiacritics(value: String): String =
    value.ns().stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)

actual fun platformNormalize(value: String, form: String): String = when (form) {
    "NFC" -> value.ns().precomposedStringWithCanonicalMapping
    "NFKC" -> value.ns().precomposedStringWithCompatibilityMapping
    "NFKD" -> value.ns().decomposedStringWithCompatibilityMapping
    else -> value.ns().decomposedStringWithCanonicalMapping
}

actual fun platformCollate(a: String, b: String, languageTag: String): Int =
    a.ns().compare(b, options = 0u, range = NSMakeRange(0u, a.length.toULong()), locale = locale(languageTag)).toInt()

actual fun platformFormatCurrency(value: Double, currencyCode: String, minFraction: Int, maxFraction: Int, languageTag: String): String {
    val formatter = NSNumberFormatter()
    formatter.locale = locale(languageTag)
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.currencyCode = currencyCode
    formatter.minimumFractionDigits = minFraction.toULong()
    formatter.maximumFractionDigits = maxFraction.toULong()
    return formatter.stringFromNumber(NSNumber(double = value)) ?: "$currencyCode $value"
}

actual fun platformFormatNumber(value: Double, minFraction: Int, maxFraction: Int, grouping: Boolean, languageTag: String): String {
    val formatter = NSNumberFormatter()
    formatter.locale = locale(languageTag)
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    formatter.usesGroupingSeparator = grouping
    formatter.minimumFractionDigits = minFraction.toULong()
    formatter.maximumFractionDigits = maxFraction.toULong()
    return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}
