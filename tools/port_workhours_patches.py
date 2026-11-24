# Source patches for tools/port_workhours.py.
#
# file name -> list of patches, applied to the Android source before the
# import rewrites. A patch is either
#   (old, new)              exact text
#   ("re", pattern, new)    regular expression (multi-line, dot matches newline)
# and must match exactly once, so upstream changes that affect a patch fail
# the port instead of silently producing wrong code.

PATCHES = {
    "AppDesign.kt": [
        ("import android.app.Activity\n", ""),
        ("import androidx.appcompat.app.AppCompatDelegate\n", ""),
        ("import androidx.core.view.WindowCompat\n", ""),
        # Android: night mode + status bar icon colours. iOS: one UIKit style override.
        ("re", r"    val context = LocalContext\.current\n    LaunchedEffect\(appearance\) \{\n.*?\n    SideEffect \{\n.*?\n    \}\n",
         "    SideEffect { compat.android.compose.platformApplyAppearance(appearance) }\n"),
    ],
    "MainActivity.kt": [
        ("import android.os.Bundle\n", ""),
        ("import androidx.appcompat.app.AppCompatActivity\n", ""),
        ("import androidx.activity.compose.setContent\n", ""),
        ("import androidx.activity.enableEdgeToEdge\n", ""),
        ("import androidx.compose.ui.platform.LocalView\n", ""),
        # The Activity itself; ios/App.kt is its counterpart.
        ("re", r"^class MainActivity : AppCompatActivity\(\) \{\n.*?^\}\n", ""),
        ("    val feedbackView = LocalView.current\n", "    val feedbackHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current\n"),
        ("re", r"runCatching \{ feedbackView\.performHapticFeedback\([^\n]*\) \}\n",
         "runCatching { feedbackHaptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }\n"),
        # "Press back again to exit" has no meaning on iOS.
        ("re", r"            else -> \{\n                val pressed=android\.os\.SystemClock\.elapsedRealtime\(\)\n.*?\n            \}\n",
         "            else -> Unit\n"),
        ("re", r"    DisposableEffect\(feedbackView\) \{\n.*?\n    \}\n", ""),
        ("; DriveBackup(context).schedule(update = false) }", " }"),
        ("    val drive=remember { DriveBackup(context.applicationContext) }\n", ""),
        # Snapshot "backup" card: drop the Google Drive status lines, keep the shortcut to the backup screen.
        ("re", r"            Text\(if\(drive\.lastSuccess==0L\)[^\n]*\n            if\(drive\.lastError\.isNotBlank\(\)\)[^\n]*\n            Text\(when\(drive\.hours\)[^\n]*\n", ""),
    ],
    "TaxCountries.kt": [
        # Same result through Foundation; avoids relying on \\p{M} support in the Kotlin/Native regex engine.
        ("java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)\n        .replace(Regex(\"\\\\p{M}+\"),\"\")", "compat.text.stripDiacritics(value)"),
    ],
    "ShiftExportButton.kt": [
        ("android.text.TextUtils.getLayoutDirectionFromLocale(appLocale)==android.view.View.LAYOUT_DIRECTION_RTL", "AppText.isRightToLeft"),
    ],
    "SettingsScreen.kt": [
        # The home-server sync screen is a separate Activity on Android; here it opens as a full-screen dialog.
        ("    val syncContext = androidx.compose.ui.platform.LocalContext.current\n",
         "    var homeSyncOpen by remember { mutableStateOf(false) }\n    if (homeSyncOpen) HomeSyncDialog { homeSyncOpen = false }\n"),
        ("OutlinedButton(onClick = { syncContext.startActivity(android.content.Intent(syncContext, HomeSyncActivity::class.java)) })",
         "OutlinedButton(onClick = { homeSyncOpen = true })"),
    ],
    "HomeSync.kt": [
        ("import android.util.Base64\n", ""),
        ("import androidx.work.*\n", ""),
        ("import java.util.concurrent.TimeUnit\n", ""),
        ("re", r"import javax\.crypto\.Cipher\nimport javax\.crypto\.KeyGenerator\nimport javax\.crypto\.SecretKey\nimport javax\.crypto\.spec\.GCMParameterSpec\n", ""),
        ("re", r"import android\.security\.keystore\.KeyGenParameterSpec\nimport android\.security\.keystore\.KeyProperties\n", ""),
        # Android wraps the server key with an Android Keystore AES key. On iOS the state file sits in a
        # backup-excluded folder and is encrypted at rest by iOS data protection, so the key is stored as-is.
        ("re", r"    private fun key\(\): SecretKey \{\n.*?\n    \}\n    private fun encrypted\(token: String\): String \{\n.*?\n    \}\n    private fun token\(c: Context\): String \{\n.*?\n    \}\n",
         "    private fun encrypted(token: String): String = token\n"
         "    private fun token(c: Context): String {\n"
         "        cachedToken?.let { return it }\n"
         "        return state(c).getString(\"token\").also { cachedToken=it }\n"
         "    }\n"),
        # No WorkManager: sync runs while the app is open (see ios/App.kt).
        ("re", r"            WorkManager\.getInstance\(c\)\.enqueueUniquePeriodicWork\([^\n]*\n[^\n]*\.build\(\)\)\n", ""),
        ("        WorkManager.getInstance(c).cancelUniqueWork(\"shifthours-home-sync\")\n", ""),
        ("re", r"class HomeSyncWorker\(context:Context,params:WorkerParameters\):CoroutineWorker\(context,params\)\{\n.*?\n\}\n", ""),
    ],
}

TEST_PATCHES = {}

SKIPPED_TESTS = set()
