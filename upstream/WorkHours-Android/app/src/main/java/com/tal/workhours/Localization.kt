package com.tal.workhours

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Uses application-backed configuration contexts, never a retained Activity. */
object AppText {
    @Volatile private var context: Context? = null
    val locale: Locale get() = context?.resources?.configuration?.locales?.get(0) ?: Locale.ENGLISH
    fun bind(source: Context) {
        context = source.applicationContext.createConfigurationContext(source.resources.configuration)
    }
    fun get(@StringRes id: Int, args: Array<out Any>): String {
        val current = context ?: return "Invalid input"
        return if (args.isEmpty()) current.getString(id) else current.getString(id, *args)
    }
}

fun tr(@StringRes id: Int, vararg args: Any): String = AppText.get(id, args)
fun locationLabel(value: String): String = when (value) {
    HOME -> tr(R.string.location_home)
    OFFICE -> tr(R.string.location_office)
    else -> value
}

class WorkHoursApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppText.bind(systemLanguageContext(this))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppText.bind(systemLanguageContext(this))
        WorkRepository.get(this).state.value?.let { ShiftWidgetProvider.refresh(this, it) }
    }
}

/** Pick the primary phone language only; unsupported languages use English. */
internal fun supportedSystemLanguage(locale: Locale): String = when (locale.language) {
    "he", "iw" -> "he"
    "en", "es", "ru", "ar", "hi", "fr", "pt", "de" -> locale.language
    "zh" -> if (locale.script == "Hant" || (locale.script.isBlank() && locale.country in setOf("TW", "HK", "MO"))) "en" else "zh-Hans"
    else -> "en"
}

fun systemLanguageContext(source: Context): Context {
    val preference = source.getSharedPreferences("workhours-ui", Context.MODE_PRIVATE).getString("language", "").orEmpty()
    val nativeOverride = if (Build.VERSION.SDK_INT >= 33)
        source.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales?.isEmpty == false else false
    if (nativeOverride) return source
    val phoneLocale = android.content.res.Resources.getSystem().configuration.locales[0]
    val config = android.content.res.Configuration(source.resources.configuration)
    val selected = if (Build.VERSION.SDK_INT < 33 && preference.isNotBlank()) preference else supportedSystemLanguage(phoneLocale)
    config.setLocales(LocaleList.forLanguageTags(selected))
    return source.createConfigurationContext(config)
}

fun initializeLanguagePolicy(activity: AppCompatActivity) {
    val prefs = activity.getSharedPreferences("workhours-ui", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("system-default-v2", false)) {
        // Switch upgrades as well as fresh installs to the requested phone-language default.
        prefs.edit().putBoolean("system-default-v2", true).putBoolean("initialized", true)
            .putString("language", "").apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}

private data class AppLanguage(val tag: String, val name: String)
private val appLanguages = listOf(
    AppLanguage("he", "עברית"), AppLanguage("en", "English"),
    AppLanguage("es", "Español"), AppLanguage("ru", "Русский"),
    AppLanguage("ar", "العربية"), AppLanguage("zh-Hans", "简体中文"),
    AppLanguage("hi", "हिन्दी"), AppLanguage("fr", "Français"),
    AppLanguage("pt", "Português"), AppLanguage("de", "Deutsch")
)

@Composable
fun LanguageSelector() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Observe system configuration as well as activity recreation.
    val configuration = LocalConfiguration.current
    val explicit = AppCompatDelegate.getApplicationLocales()
    val tag = explicit.get(0)?.toLanguageTag().orEmpty()
    val selected = appLanguages.find { it.tag == tag || (tag.isNotEmpty() && it.tag.substringBefore('-') == tag.substringBefore('-')) }
    var expanded by remember(configuration) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr(R.string.language_title) + " · " + (selected?.name ?: tr(R.string.language_system)))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            val choices = listOf(AppLanguage("", tr(R.string.language_system))) + appLanguages
            choices.forEach { language ->
                DropdownMenuItem(text = { Text(language.name) }, onClick = {
                    expanded = false
                    context.getSharedPreferences("workhours-ui", Context.MODE_PRIVATE).edit()
                        .putString("language", language.tag).putBoolean("initialized", true).apply()
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                })
            }
        }
    }
    Text(tr(R.string.language_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
}
