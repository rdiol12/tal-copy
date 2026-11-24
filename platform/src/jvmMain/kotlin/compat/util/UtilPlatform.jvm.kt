package compat.util

actual fun platformLanguageTag(): String = java.util.Locale.getDefault().toLanguageTag()
actual fun platformCountryName(regionCode: String, languageTag: String): String =
    java.util.Locale.Builder().setRegion(regionCode).build().getDisplayCountry(java.util.Locale.forLanguageTag(languageTag))
actual fun platformLanguageName(tag: String, languageTag: String): String =
    java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale.forLanguageTag(languageTag))
actual fun platformCurrencySymbol(currencyCode: String, languageTag: String): String =
    java.util.Currency.getInstance(currencyCode).getSymbol(java.util.Locale.forLanguageTag(languageTag))
actual fun platformNanoTime(): Long = java.lang.System.nanoTime()

actual class PlatformLock actual constructor() {
    private val lock = java.util.concurrent.locks.ReentrantLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
actual fun platformRunInBackground(name: String, block: () -> Unit) {
    java.lang.Thread({ block() }, name).also { it.isDaemon = true }.start()
}
