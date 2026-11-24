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
        # Entry point of the Android-only home-server sync screen.
        ("    val syncContext = androidx.compose.ui.platform.LocalContext.current\n", ""),
        ("            OutlinedButton(onClick = { syncContext.startActivity(android.content.Intent(syncContext, HomeSyncActivity::class.java)) }) { Text(tr(R.string.home_sync_title)) }\n", ""),
    ],
}

TEST_PATCHES = {}

SKIPPED_TESTS = set()
