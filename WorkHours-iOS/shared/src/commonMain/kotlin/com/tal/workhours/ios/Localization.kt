// iOS counterpart of the Android Localization.kt. Strings come from the
// tables generated out of the Android strings.xml files, so the language can
// be switched inside the app without restarting it.
package com.tal.workhours

import com.tal.workhours.generated.STRING_TABLES
import compat.android.Context
import compat.text.format
import compat.util.Locale
import compat.util.platformLanguageTag

object AppText {
    private const val PREFERENCES = "workhours-ui"
    private val english: Array<String?> = STRING_TABLES.getValue("en")
    private var table: Array<String?> = english
    private var listeners = ArrayList<() -> Unit>()

    /** Language tag of the strings in use, e.g. "he" or "zh-Hans". */
    var language: String = "en"
        private set

    /** The user's explicit choice; empty means "follow the phone language". */
    var preference: String = ""
        private set

    val locale: Locale get() = Locale.forLanguageTag(language)
    val isRightToLeft: Boolean get() = language == "he" || language == "ar"

    /** Reads the saved choice and applies it. Safe to call again when the phone language changes. */
    fun bind(context: Context = Context.app) {
        Context.stringResolver = { id, args -> get(id, args) }
        preference = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("language", "").orEmpty()
        apply()
    }

    /** Saves and applies a language choice ("" = phone language) and notifies the UI. */
    fun select(tag: String, context: Context = Context.app) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("language", tag).putBoolean("initialized", true).apply()
        preference = tag
        apply()
        listeners.toList().forEach { it() }
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    private fun apply() {
        val chosen = preference.takeIf { it in STRING_TABLES }
            ?: supportedSystemLanguage(Locale.forLanguageTag(platformLanguageTag()))
        language = if (chosen in STRING_TABLES) chosen else "en"
        table = STRING_TABLES.getValue(language)
        // Dates, month names and currency use the same language as the texts.
        Locale.defaultOverride = locale
    }

    fun get(id: Int, args: Array<out Any?>): String {
        val text = table.getOrNull(id) ?: english.getOrNull(id) ?: return "Invalid input"
        return if (args.isEmpty()) text else text.format(*args)
    }
}

fun tr(id: Int, vararg args: Any): String = AppText.get(id, args)

fun locationLabel(value: String): String = when (value) {
    HOME -> tr(R.string.location_home)
    OFFICE -> tr(R.string.location_office)
    else -> value
}

/** Pick the primary phone language only; unsupported languages use English. */
internal fun supportedSystemLanguage(locale: Locale): String = when (locale.language) {
    "he", "iw" -> "he"
    "en", "es", "ru", "ar", "hi", "fr", "pt", "de" -> locale.language
    "zh" -> if (locale.script == "Hant" || (locale.script.isBlank() && locale.country in setOf("TW", "HK", "MO"))) "en" else "zh-Hans"
    else -> "en"
}

internal data class AppLanguage(val tag: String, val name: String)

internal val appLanguages = listOf(
    AppLanguage("he", "עברית"), AppLanguage("en", "English"),
    AppLanguage("es", "Español"), AppLanguage("ru", "Русский"),
    AppLanguage("ar", "العربية"), AppLanguage("zh-Hans", "简体中文"),
    AppLanguage("hi", "हिन्दी"), AppLanguage("fr", "Français"),
    AppLanguage("pt", "Português"), AppLanguage("de", "Deutsch")
)
