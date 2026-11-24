package com.tal.workhours

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** No snapshots/reports uploaded on a timer. Network and JSON work run off the UI thread. */
internal object HomeSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val diskLock = Any()
    @Volatile private var cachedToken: String? = null
    @Volatile private var watchCursor = 0L
    @Volatile private var watchIdentity = ""
    private var watcher: Job? = null
    private var pending: Job? = null
    private val statusFlow = MutableStateFlow("off")
    val status = statusFlow.asStateFlow()
    private val diagnosticFlow = MutableStateFlow("")
    val diagnostic = diagnosticFlow.asStateFlow()
    private fun describeFailure(e: Exception): String {
        val text = if(e is HomeSyncHttpFailure) "HTTP ${e.code} · ${e.path} · ${e.detail}" else when(e) {
            is javax.net.ssl.SSLException -> "TLS/HTTPS · ${e.javaClass.simpleName}"
            is java.net.UnknownHostException -> "DNS · ${e.javaClass.simpleName}"
            is java.net.SocketTimeoutException -> "Timeout · ${e.javaClass.simpleName}"
            is java.net.ConnectException -> "Connection · ${e.javaClass.simpleName}"
            else -> "${e.javaClass.simpleName} · ${e.message.orEmpty()}"
        }
        val secret = cachedToken
        return (if(!secret.isNullOrEmpty())text.replace(secret,"[redacted]") else text).take(500)
    }
    private fun prefs(c: Context) = c.getSharedPreferences("shifthours-home",0)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun url(c: Context) = prefs(c).getString("url", "") ?: ""
    private fun stateFile(c: Context) = AtomicFile(File(c.noBackupFilesDir,"home-sync-state.json"))
    private fun state(c: Context): JSONObject = synchronized(diskLock) { stateFile(c).let { f ->
        if(f.baseFile.exists() || File(f.baseFile.path+".bak").exists()) f.openRead().bufferedReader().use { JSONObject(it.readText()) }
        else JSONObject().put("cursor",0).put("base",JSONObject()).put("revisions",JSONObject())
    } }
    private fun save(c: Context, s: JSONObject) = synchronized(diskLock) {
        val f=stateFile(c);val out=f.startWrite()
        try { out.write(s.toString().toByteArray(Charsets.UTF_8));f.finishWrite(out);watchCursor=s.optLong("cursor",0);watchIdentity=s.optString("serverId","") }
        catch(e:Exception){f.failWrite(out);throw e}
    }
    private fun key(): SecretKey {
        val store=java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("shifthours-home-key",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("shifthours-home-key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypted(token: String): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        return Base64.encodeToString(cipher.iv+cipher.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    private fun token(c: Context): String {
        cachedToken?.let { return it }
        val bytes=Base64.decode(state(c).getString("token"),Base64.NO_WRAP)
        return String(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))) }.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8).also { cachedToken=it }
    }
    suspend fun configure(c: Context, address: String, secret: String) = withContext(Dispatchers.IO) {
        val uri=URI(address.trim().trimEnd('/'))
        require(uri.scheme=="https" && uri.host!=null && uri.userInfo==null && uri.rawQuery==null && uri.fragment==null && (uri.path.isNullOrEmpty() || uri.path=="/")) { tr(R.string.home_sync_https) }
        require(secret.matches(Regex("[A-Za-z0-9_-]{40,100}"))) { tr(R.string.home_sync_key) }
        gate.withLock {
            val addressText=uri.toString().trimEnd('/')
            require(url(c).isEmpty() || url(c)==addressText) { tr(R.string.home_sync_other_server) }
            val s=state(c);s.put("token",encrypted(secret));save(c,s);cachedToken=secret
            prefs(c).edit().putString("url",addressText).putBoolean("enabled",true).commit()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("shifthours-home-sync",ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<HomeSyncWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        sync(c)
    }
    suspend fun disconnect(c: Context) = gate.withLock {
        prefs(c).edit().putBoolean("enabled",false).commit()
        WorkManager.getInstance(c).cancelUniqueWork("shifthours-home-sync")
        watcher?.cancel();pending?.cancel();statusFlow.value="off";diagnosticFlow.value=""
    }
    private fun request(c: Context, path: String, data: JSONObject? = null): JSONObject {
        val conn=java.net.URL(url(c)+path).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects=false;conn.connectTimeout=10_000;conn.readTimeout=30_000
            conn.setRequestProperty("Authorization","Bearer "+token(c));conn.setRequestProperty("X-ShiftHours","1")
            if(data!=null){conn.requestMethod="POST";conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }}
            val code=conn.responseCode
            if(code !in 200..299) {
                val raw=runCatching { conn.errorStream?.use { input ->
                    val bytes=ByteArray(4096);var count=0
                    while(count<bytes.size){val n=input.read(bytes,count,bytes.size-count);if(n<0)break;count+=n}
                    String(bytes,0,count,Charsets.UTF_8)
                }.orEmpty() }.getOrDefault("")
                val detail=runCatching { JSONObject(raw).opt("detail")?.toString().orEmpty() }.getOrDefault("")
                throw HomeSyncHttpFailure(code,path.substringBefore('?'),detail.take(350))
            }
            val bytes=conn.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0
                while(true){val n=input.read(buffer);if(n<0)break;total+=n;check(total<=25_000_000);output.write(buffer,0,n)};output.toByteArray()
            }
            return JSONObject(String(bytes,Charsets.UTF_8))
        } finally { conn.disconnect() }
    }
    suspend fun sync(c: Context): Boolean = withContext(Dispatchers.IO) { gate.withLock {
        if(!enabled(c)) return@withLock true
        try {
            statusFlow.value="working"
            val repo=WorkRepository.get(c);repo.load()
            repeat(3) {
                val s=state(c)
                val response=request(c,"/api/changes?after="+s.optLong("cursor",0))
                val id=response.getString("serverId")
                check(!s.has("serverId") || s.getString("serverId")==id){"identity"}
                val delta=response.getJSONArray("changes")
                val base=s.getJSONObject("base")
                repo.applyHomeDelta(base,delta)
                // Persist acknowledgement after durable local write. A crash only replays the same pull.
                val revisions=s.getJSONObject("revisions")
                repeat(delta.length()){i->val r=delta.getJSONObject(i);val k=r.getString("key");revisions.put(k,r.getLong("rev"));if(r.isNull("value"))base.remove(k) else base.put(k,r.get("value"))}
                s.put("cursor",response.getLong("cursor")).put("serverId",id);save(c,s)
                val local=HomeSyncDocument.flat(encodeData(repo.snapshot()))
                val ops=HomeSyncDocument.operations(base,revisions,local)
                if(ops.length()==0){diagnosticFlow.value="";statusFlow.value="ok";return@withLock true}
                try { request(c,"/api/changes",JSONObject().put("serverId",id).put("operations",ops)) }
                catch(e:Exception){if(e.message!="retry")throw e;diagnosticFlow.value=describeFailure(e)}
            }
            statusFlow.value="pending";false
        } catch(e:Exception) {
            if(e is CancellationException)throw e
            diagnosticFlow.value=describeFailure(e)
            statusFlow.value=when(e.message){"auth"->"auth";"identity"->"identity";else->if(e is HomeSyncConflict)"conflict" else "error"}
            false
        }
    } }
    /** Local file itself is the durable outbox. A crash/reboot never drops unsent edits. */
    fun changed(context: Context) {
        val c=context.applicationContext
        if(!enabled(c))return
        pending?.cancel();pending=scope.launch { delay(900);sync(c) }
    }
    fun foreground(context: Context) {
        val c=context.applicationContext
        if(!enabled(c) || watcher?.isActive==true)return
        watcher=scope.launch {
            sync(c)
            while(isActive && enabled(c)) {
                if(statusFlow.value in setOf("conflict","identity","auth")){delay(30_000);continue}
                try {
                    val cursor=watchCursor;val identity=watchIdentity
                    val r=request(c,"/api/revision?after="+cursor)
                    if(r.getLong("cursor")!=cursor || r.getString("serverId")!=identity || statusFlow.value in setOf("error","pending")) { if(!sync(c))delay(15_000) }
                } catch(e:Exception){if(e is CancellationException)throw e;diagnosticFlow.value=describeFailure(e);statusFlow.value="error";delay(15_000);sync(c)}
            }
        }
    }
    fun background(){watcher?.cancel();watcher=null}
}
internal class HomeSyncConflict: Exception()
class HomeSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result = if(HomeSync.sync(applicationContext))Result.success() else Result.retry()
}

internal class HomeSyncHttpFailure(val code: Int, val path: String, val detail: String): Exception(
    when(code){401 -> "auth";409 -> "retry";else -> "http"})
