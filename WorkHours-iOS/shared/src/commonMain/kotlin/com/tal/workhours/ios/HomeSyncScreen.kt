// Home-server ("Personal server") sync settings. On Android this is
// HomeSyncActivity; here the same content is shown as a full-screen dialog.
package com.tal.workhours

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compat.android.compose.LocalContext
import kotlinx.coroutines.launch

@Composable
fun HomeSyncDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { HomeSyncScreen(onClose) }
    }
}

@Composable
private fun HomeSyncScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by HomeSync.status.collectAsState()
    val diagnostic by HomeSync.diagnostic.collectAsState()
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(HomeSync.url(context)) }
    // The key is intentionally not saveable and never included in backups.
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(HomeSync.enabled(context)) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr(R.string.home_sync_title), style = MaterialTheme.typography.headlineSmall)
        Text(tr(R.string.home_sync_hint))
        OutlinedTextField(address, { address = it }, label = { Text(tr(R.string.home_sync_address)) }, singleLine = true, enabled = !busy && !enabled, modifier = Modifier.fillMaxWidth())
        if (!enabled) OutlinedTextField(secret, { secret = it }, label = { Text(tr(R.string.home_sync_key)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text(tr(when (state) {
            "working" -> R.string.home_sync_working; "ok" -> R.string.home_sync_ok; "conflict" -> R.string.home_sync_conflict
            "auth" -> R.string.home_sync_auth; "identity" -> R.string.home_sync_identity; "error" -> R.string.home_sync_error
            "pending" -> R.string.home_sync_pending; else -> R.string.home_sync_off
        }))
        if (diagnostic.isNotBlank()) Text(diagnostic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, onClick = { scope.launch {
            busy = true; problem = null
            try {
                if (!enabled) { HomeSync.configure(context, address, secret); secret = "" } else HomeSync.sync(context)
                enabled = HomeSync.enabled(context); HomeSync.foreground(context)
            } catch (e: Exception) { problem = e.message ?: tr(R.string.home_sync_error) }
            finally { enabled = HomeSync.enabled(context); busy = false }
        } }) { Text(tr(if (enabled) R.string.home_sync_now else R.string.home_sync_connect)) }
        if (enabled) OutlinedButton(enabled = !busy, onClick = { scope.launch { HomeSync.disconnect(context); enabled = false } }) { Text(tr(R.string.home_sync_disconnect)) }
        Text(tr(R.string.home_sync_backup_hint), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onClose) { Text(tr(R.string.home_sync_back)) }
    }
}
