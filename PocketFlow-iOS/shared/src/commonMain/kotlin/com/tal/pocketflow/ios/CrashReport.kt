// When the app crashes, the error is written to a file; the next launch shows it instead of
// starting straight away, so it can be copied and sent, and the user can still continue.
package com.tal.pocketflow

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compat.io.File

internal object CrashReport {
    private fun file(): File = File(Context.app.noBackupFilesDir.also { it.mkdirs() }, "last-crash.txt")

    fun install() {
        compat.android.platformInstallCrashHook { error ->
            val text = buildString {
                append(compat.time.LocalDateTime.now().toString()).append('\n')
                append(error.stackTraceToString())
            }
            file().writeText(text.take(20_000))
        }
    }

    fun pending(): String? = runCatching { file().takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear() { runCatching { file().delete() } }
}

@Composable
internal fun CrashScreen(report: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F6F9)).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PocketFlow נסגר בפעם הקודמת בגלל שגיאה", style = MaterialTheme.typography.titleLarge, color = Color(0xFF182333))
        Text("הנתונים שלך לא נמחקו. אפשר להעתיק את פרטי השגיאה ולשלוח אותם, ואז להמשיך.", color = Color(0xFF4E5D70))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("העתקת פרטי השגיאה") }
            OutlinedButton(onClick = onContinue) { Text("המשך") }
        }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().background(Color.White).padding(10.dp).verticalScroll(rememberScrollState())) {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF182333))
        }
    }
}
