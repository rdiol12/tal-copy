package compat.util

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencySymbol
import platform.Foundation.NSProcessInfo
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.preferredLanguages

private fun locale(tag: String): NSLocale = NSLocale(localeIdentifier = if (tag.isEmpty() || tag == "und") "en_US_POSIX" else tag)

actual fun platformLanguageTag(): String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"

actual fun platformCountryName(regionCode: String, languageTag: String): String =
    locale(languageTag).localizedStringForCountryCode(regionCode) ?: regionCode

actual fun platformLanguageName(tag: String, languageTag: String): String =
    locale(languageTag).localizedStringForLanguageCode(tag.substringBefore('-')) ?: tag

actual fun platformCurrencySymbol(currencyCode: String, languageTag: String): String =
    locale(languageTag).displayNameForKey(NSLocaleCurrencySymbol, value = currencyCode) ?: currencyCode

actual fun platformNanoTime(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()

actual class PlatformLock actual constructor() {
    private val lock = platform.Foundation.NSRecursiveLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
actual fun platformRunInBackground(name: String, block: () -> Unit) {
    platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { block() }
}
