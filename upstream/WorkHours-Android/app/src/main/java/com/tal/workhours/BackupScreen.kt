package com.tal.workhours

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

private fun backupError(e: Exception): String = when {
    e is ApiException && e.statusCode == 10 -> tr(R.string.text_0206a8d338)
    e is ApiException -> tr(R.string.text_630af90bc5, e.statusCode)
    else -> e.message ?: tr(R.string.text_daf10c2ee5)
}

@Composable
fun BackupScreen(repo: WorkRepository, data: AppData, now: Long, enabled: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val drive = remember { DriveBackup(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf("CONNECT") }
    var files by remember { mutableStateOf<List<CloudFile>?>(null) }
    var restorePreview by remember { mutableStateOf<AppData?>(null) }
    var cloudHours by remember { mutableIntStateOf(drive.hours) }
    var wifi by remember { mutableStateOf(drive.wifiOnly) }
    var localError by remember { mutableStateOf<String?>(null) }
    val available = enabled && !busy
    val storedData by repo.state.collectAsState()
    val readable = storedData != null
    fun failure(e: Exception) {
        val message = backupError(e)
        localError = message; drive.reportError(message); onMessage(message); busy = false
    }
    fun execute(token: String, operation: String) {
        scope.launch {
            busy = true
            try {
                drive.authorized()
                when {
                    operation == "BACKUP" -> { drive.upload(token, repo.snapshot()); onMessage(tr(R.string.text_25b395415b)) }
                    operation == "LIST" -> { files = drive.list(token); if (files!!.isEmpty()) onMessage(tr(R.string.text_fc021a3be3)) }
                    operation.startsWith("RESTORE:") -> { restorePreview = drive.download(token, operation.substringAfter(':')); files = null }
                    else -> { if (repo.state.value != null) drive.schedule(update = false); onMessage(tr(R.string.text_439ed2ca59)) }
                }
                localError = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
            finally { busy = false }
        }
    }
    val authorization = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { busy = false; onMessage(tr(R.string.text_7480a74f74)) }
        else try {
            val auth = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
            val token = auth.accessToken ?: error(tr(R.string.text_514e675d10))
            execute(token, action)
        } catch (e: Exception) { failure(e) }
    }
    fun authorize(operation: String) {
        if (busy) return
        busy = true; action = operation
        scope.launch {
            try {
                val auth = Identity.getAuthorizationClient(context).authorize(drive.request()).awaitResult()
                if (auth.hasResolution()) authorization.launch(IntentSenderRequest.Builder(auth.pendingIntent!!.intentSender).build())
                else execute(auth.accessToken ?: error(tr(R.string.text_325ca3ec59)), operation)
            } catch (e: kotlinx.coroutines.CancellationException) { busy = false; throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!name.isNullOrBlank()) { drive.account = name; drive.schedule(); authorize("CONNECT") }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = backupText(repo.snapshot())
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri, "wt") ?: error(tr(R.string.text_5862cd4663))).use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                onMessage(tr(R.string.text_a8df08ecdf))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { localError = backupError(e); onMessage(localError!!) }
            finally { busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                restorePreview = withContext(Dispatchers.IO) {
                    val bytes = ByteArrayOutputStream()
                    (context.contentResolver.openInputStream(uri) ?: error(tr(R.string.text_04bb2141d5))).use { input ->
                        val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(bytes.size() + n <= 25_000_000) { tr(R.string.text_5e9b8b7914) }; bytes.write(buffer, 0, n) }
                    }
                    readBackup(bytes.toString("UTF-8"))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { localError = backupError(e); onMessage(localError!!) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr(R.string.text_806ac63619), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoCard(tr(R.string.text_55d63892ad)) {
            Text(if (drive.account.isBlank()) tr(R.string.text_e5444dccaf) else drive.account)
            Text(if (drive.connected) tr(R.string.text_0a2305a775) else tr(R.string.text_2fc9030263), style = MaterialTheme.typography.bodySmall)
            Button(enabled = available, onClick = {
                try { accountPicker.launch(AccountPicker.newChooseAccountIntent(AccountPicker.AccountChooserOptions.Builder().setAllowableAccountsTypes(listOf("com.google")).build())) }
                catch (e: Exception) { failure(e) }
            }) { Text(if (drive.connected) tr(R.string.text_cd0e41e136) else tr(R.string.text_6f3a7bda3a)) }
            OutlinedButton(enabled = available && readable && drive.account.isNotBlank(), onClick = { authorize("BACKUP") }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.text_15109258b9)) }
            OutlinedButton(enabled = available && drive.account.isNotBlank(), onClick = { authorize("LIST") }, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.text_c75c1e3a33)) }
            // Read on each timer-driven recomposition so background-worker results appear here.
            val last = remember(now / 1000) { drive.lastSuccess }
            Text(if (last == 0L) tr(R.string.text_90b7df067a) else tr(R.string.text_1c2b595825, localTime(last).format(dateFormat), localTime(last).format(clockFormat)))
            val error = localError ?: drive.lastError
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(tr(R.string.text_32640ad12e), style = MaterialTheme.typography.bodySmall)
        }
        InfoCard(tr(R.string.text_ea1a07b6ef)) {
            listOf(0 to tr(R.string.text_8edcd28553), 24 to tr(R.string.text_ef4037576b), 168 to tr(R.string.text_50296d4c2d)).forEach { (hours, label) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(cloudHours == hours, enabled = available && (hours == 0 || (drive.connected && readable)), onClick = {
                        cloudHours = hours; drive.hours = hours; drive.schedule(); onMessage(tr(R.string.text_790f24eb34))
                    }); Text(label)
                }
            }
            SettingSwitch(tr(R.string.text_e75e2661b7), wifi, available) { wifi = it; drive.wifiOnly = it; drive.schedule() }
            Text(tr(R.string.text_cf3d313652), style = MaterialTheme.typography.bodySmall)
            if (drive.connected) TextButton(enabled = available, onClick = { drive.disconnect(); cloudHours = 0; files = null; localError = null; onMessage(tr(R.string.text_1e579b05be)) }) { Text(tr(R.string.text_83eccb0dd3)) }
        }
        InfoCard(tr(R.string.text_b35a854e22)) {
            Text(tr(R.string.text_1da6e0d4aa))
            OutlinedButton(enabled = available && readable, modifier = Modifier.fillMaxWidth(), onClick = { export.launch("ShiftHours-${localTime(System.currentTimeMillis()).toLocalDate()}.json") }) { Text(tr(R.string.text_52cca61a5d)) }
            OutlinedButton(enabled = available, modifier = Modifier.fillMaxWidth(), onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(tr(R.string.text_4d2b863edd)) }
            Text(tr(R.string.text_f4ab9db1a0), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = available && java.io.File(context.filesDir, "before-restore.json").exists(), onClick = {
                scope.launch { busy = true; try { restorePreview = withContext(Dispatchers.IO) { readBackup(java.io.File(context.filesDir, "before-restore.json").readText(Charsets.UTF_8)) } } catch (e: Exception) { localError = backupError(e) } finally { busy = false } }
            }) { Text(tr(R.string.text_86472e4d43)) }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(if (readable) tr(R.string.text_bf72e99186, data.shifts.size) else tr(R.string.text_b5af650c8c), style = MaterialTheme.typography.bodySmall)
    }
    files?.let { options -> AlertDialog(onDismissRequest = { if (!busy) files = null }, title = { Text(tr(R.string.text_f738c4c045)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.isEmpty()) Text(tr(R.string.text_c775e7dcf6))
            options.forEach { f ->
                val date = runCatching { localTime(Instant.parse(f.created).toEpochMilli()).format(DateTimeFormatterForBackup) }.getOrDefault(f.created)
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { authorize("RESTORE:${f.id}") }) { Text(date) }
            }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = { files = null }) { Text(tr(R.string.text_b728721fc0)) } }) }
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
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { localError = backupError(e); onMessage(localError!!) }
            finally { busy = false }
        } }) { Text(tr(R.string.text_a87c9f4c06)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { restorePreview = null }) { Text(tr(R.string.text_a7c55a8d06)) } }) }
}

private val DateTimeFormatterForBackup = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
