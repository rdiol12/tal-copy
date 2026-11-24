package com.tal.workhours

import android.accounts.Account
import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { if (c.isActive) c.resume(it) }
    addOnFailureListener { if (c.isActive) c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

data class CloudFile(val id: String, val name: String, val created: String)
class DriveHttpException(val status: Int) : IOException(when (status) {
    401 -> tr(R.string.text_925bb7a659)
    403 -> tr(R.string.text_86de69005f)
    404 -> tr(R.string.text_0885c5a6fa)
    429 -> tr(R.string.text_09780711f6)
    else -> tr(R.string.text_2a152ef564, status)
})

class DriveBackup(private val context: Context) {
    private val prefs = context.getSharedPreferences("drive-backup", 0)
    var account: String
        get() = prefs.getString("account", "") ?: ""
        set(value) {
            if (value != account) prefs.edit().putString("account", value).putBoolean("connected", false)
                .remove("lastSuccess").remove("lastError").apply()
        }
    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        set(value) { prefs.edit().putBoolean("connected", value).apply() }
    var hours: Int
        get() = prefs.getInt("hours", 0)
        set(value) { prefs.edit().putInt("hours", value).apply() }
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifiOnly", false)
        set(value) { prefs.edit().putBoolean("wifiOnly", value).apply() }
    val lastSuccess: Long get() = prefs.getLong("lastSuccess", 0)
    val lastError: String get() = prefs.getString("lastError", "") ?: ""
    fun reportError(message: String) { prefs.edit().putString("lastError", message.take(1000)).apply() }
    fun authorized() { connected = true; reportError("") }

    fun request(): AuthorizationRequest {
        require(account.isNotBlank()) { tr(R.string.text_07d8d8fb15) }
        return AuthorizationRequest.builder().setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata"))).build()
    }

    private fun call(token: String, url: String, method: String = "GET", bytes: ByteArray? = null, contentType: String = "application/json"): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer ${token}")
            if (bytes != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw DriveHttpException(code)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder(); val buffer = CharArray(8192)
                while (true) { val n = reader.read(buffer); if (n < 0) break; out.append(buffer, 0, n); require(out.length <= 25_000_000) { tr(R.string.text_5e9b8b7914) } }
                out.toString()
            }
        } finally { connection.disconnect() }
    }

    suspend fun upload(token: String, data: AppData): String = withContext(Dispatchers.IO) {
        val name = "ShiftHours-${System.currentTimeMillis()}-${UUID.randomUUID()}.json"
        val metadata = JSONObject().put("name", name).put("mimeType", "application/json").put("parents", JSONArray().put("appDataFolder"))
        val boundary = "workhours_" + UUID.randomUUID().toString()
        val body = driveMultipartBody(metadata.toString(), backupText(data), boundary)
        val response = call(token, "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", "POST", body.toByteArray(Charsets.UTF_8), "multipart/related; boundary=${boundary}")
        val id = JSONObject(response).getString("id")
        require(id.isNotBlank()) { tr(R.string.text_44707e2acd) }
        prefs.edit().putLong("lastSuccess", System.currentTimeMillis()).putString("lastError", "").apply()
        id
    }

    suspend fun list(token: String): List<CloudFile> = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("trashed = false and mimeType = 'application/json'", "UTF-8")
        val raw = call(token, "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=100&orderBy=createdTime%20desc&q=${query}&fields=files(id,name,createdTime)")
        val files = JSONObject(raw).getJSONArray("files")
        (0 until files.length()).map { i -> files.getJSONObject(i).let { CloudFile(it.getString("id"), it.getString("name"), it.getString("createdTime")) } }
            .filter { it.name.startsWith("WorkHours-") || it.name.startsWith("ShiftHours-") }
    }

    suspend fun download(token: String, id: String): AppData = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        readBackup(call(token, "https://www.googleapis.com/drive/v3/files/${id}?alt=media"))
    }

    fun schedule(update: Boolean = true) {
        val manager = WorkManager.getInstance(context)
        if (hours == 0 || !connected) { manager.cancelUniqueWork("workhours-drive-backup"); return }
        val constraints = Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val work = PeriodicWorkRequestBuilder<DriveBackupWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints).setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        manager.enqueueUniquePeriodicWork("workhours-drive-backup", if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun disconnect() {
        connected = false; hours = 0; account = ""; reportError("")
        WorkManager.getInstance(context).cancelUniqueWork("workhours-drive-backup")
    }
}

class DriveBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val drive = DriveBackup(applicationContext)
        if (!drive.connected || drive.hours == 0) return Result.success()
        return try {
            val auth = Identity.getAuthorizationClient(applicationContext).authorize(drive.request()).awaitResult()
            if (auth.hasResolution() || auth.accessToken.isNullOrBlank()) {
                drive.reportError(tr(R.string.text_eee5e9d37e))
                Result.failure()
            } else {
                val repository = WorkRepository.get(applicationContext)
                repository.load()
                drive.upload(auth.accessToken!!, repository.snapshot())
                Result.success()
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            drive.reportError(tr(R.string.text_fb5e219eb1) + (e.message ?: tr(R.string.text_82a2bfa2fe)))
            if ((e is DriveHttpException && e.status in setOf(401, 403, 404)) || runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }
}
