// iOS backup screen: export to / restore from a backup file through the system
// file sheets, plus the automatic "before restore" safety copy. The Android
// screen's Google Drive section has no iOS counterpart yet.
package com.tal.workhours

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compat.android.compose.ActivityResultContracts
import compat.android.compose.LocalContext
import compat.android.compose.rememberLauncherForActivityResult
import compat.io.File
import compat.util.System
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun backupError(e: Exception): String = e.message ?: tr(R.string.text_daf10c2ee5)

@Composable
fun BackupScreen(repo: WorkRepository, data: AppData, now: Long, enabled: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<AppData?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val available = enabled && !busy
    val storedData by repo.state.collectAsState()
    val readable = storedData != null
    val safetyCopy = remember(context) { File(context.filesDir, "before-restore.json") }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = backupText(repo.snapshot())
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: error(tr(R.string.text_5862cd4663))).use { it.write(text.encodeToByteArray()) }
                }
                localError = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { localError = backupError(e); onMessage(localError!!) }
            finally { busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                restorePreview = withContext(Dispatchers.IO) {
                    val bytes = (context.contentResolver.openInputStream(uri) ?: error(tr(R.string.text_04bb2141d5))).use { it.readBytes() }
                    require(bytes.size <= 25_000_000) { tr(R.string.text_5e9b8b7914) }
                    readBackup(bytes.decodeToString())
                }
                localError = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { localError = backupError(e); onMessage(localError!!) }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoCard(tr(R.string.text_b35a854e22)) {
            Text(tr(R.string.text_1da6e0d4aa))
            OutlinedButton(enabled = available && readable, modifier = Modifier.fillMaxWidth(), onClick = {
                export.launch("ShiftHours-${localTime(System.currentTimeMillis()).toLocalDate()}.json")
            }) { Text(tr(R.string.text_52cca61a5d)) }
            OutlinedButton(enabled = available, modifier = Modifier.fillMaxWidth(), onClick = {
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }) { Text(tr(R.string.text_4d2b863edd)) }
            Text(tr(R.string.text_f4ab9db1a0), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = available && safetyCopy.exists(), onClick = {
                scope.launch {
                    busy = true
                    try { restorePreview = withContext(Dispatchers.IO) { readBackup(safetyCopy.readText()) } }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { localError = backupError(e) }
                    finally { busy = false }
                }
            }) { Text(tr(R.string.text_86472e4d43)) }
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (!readable) Text(tr(R.string.text_b5af650c8c), style = MaterialTheme.typography.bodySmall)
    }

    restorePreview?.let { restored -> AlertDialog(onDismissRequest = { if (!busy) restorePreview = null }, title = { Text(tr(R.string.text_835ba611ee)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.text_bfe0764274, restored.shifts.size))
            restored.shifts.minOfOrNull { it.workDate }?.let { Text(tr(R.string.text_19a88cf185, it, restored.shifts.maxOf { s -> s.workDate })) }
            Text(tr(R.string.text_3cbbf0077a, if (restored.active == null) tr(R.string.text_ea12a4ba2b) else tr(R.string.text_1f70207bab)))
            Text(tr(R.string.text_2388ed2d80, data.shifts.size))
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try { repo.restore(restored); restorePreview = null; onMessage(tr(R.string.text_f35658ed7c)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { localError = backupError(e); onMessage(localError!!) }
            finally { busy = false }
        } }) { Text(tr(R.string.text_a87c9f4c06)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { restorePreview = null }) { Text(tr(R.string.text_a7c55a8d06)) } }) }
}
