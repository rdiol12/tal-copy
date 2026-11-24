package compat.util

actual fun platformLanguageTag(): String = java.util.Locale.getDefault().toLanguageTag()
actual fun platformCountryName(regionCode: String, languageTag: String): String =
    java.util.Locale.Builder().setRegion(regionCode).build().getDisplayCountry(java.util.Locale.forLanguageTag(languageTag))
actual fun platformLanguageName(tag: String, languageTag: String): String =
    java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale.forLanguageTag(languageTag))
actual fun platformCurrencySymbol(currencyCode: String, languageTag: String): String =
    java.util.Currency.getInstance(currencyCode).getSymbol(java.util.Locale.forLanguageTag(languageTag))
actual fun platformNanoTime(): Long = java.lang.System.nanoTime()
