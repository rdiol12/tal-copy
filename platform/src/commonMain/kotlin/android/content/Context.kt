package android.content

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

import compat.android.platformExportFile
import compat.io.ByteArrayInputStream
import compat.io.ByteArrayOutputStream
import compat.io.File
import compat.io.InputStream
import compat.io.OutputStream
import compat.io.platformCacheDirectory
import compat.io.platformFilesDirectory
import compat.android.platformExcludeFromBackup
import android.net.Uri
import org.json.JSONObject

/**
 * android.content.Context. There is one application context ([app]); an
 * Activity is also a Context and shares the same files and preferences.
 */
open class Context protected constructor() {
    val applicationContext: Context get() = app
    val baseContext: Context get() = this
    val filesDir: File get() = File(platformFilesDirectory())
    val cacheDir: File get() = File(platformCacheDirectory())
    /** Kept out of device backups (used for credentials and sync bookkeeping). */
    val noBackupFilesDir: File get() = File(filesDir, "no-backup").also { platformExcludeFromBackup(it.path) }
    val contentResolver: ContentResolver get() = resolver
    fun getDatabasePath(name: String): File = File(File(filesDir, "databases").also { it.mkdirs() }, name)
    val packageName: String get() = applicationId
    val resources: android.content.res.Resources get() = android.content.res.Resources.system
    val mainLooper: android.os.Looper get() = android.os.Looper.getMainLooper()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { SharedPreferences(File(filesDir, "prefs-$name.json")) }

    fun getString(id: Int, vararg args: Any?): String = stringResolver(id, args)

    companion object {
        const val MODE_PRIVATE = 0
        /** The single application context. */
        @JvmField
        val app: Context = Context()
        private val resolver = ContentResolver()
        /** Shown as the bundle/package name; set by each app at startup. */
        var applicationId: String = "app"
        /** Installed by the app's localization layer. */
        var stringResolver: (Int, Array<out Any?>) -> String = { id, _ -> "#$id" }
        private val preferences = HashMap<String, SharedPreferences>()
    }
}

/** Key-value store persisted as one small JSON file per preferences name. */
class SharedPreferences internal constructor(private val file: File) {
    private val values: JSONObject = runCatching { if (file.exists()) JSONObject(file.readText()) else JSONObject() }.getOrElse { JSONObject() }

    val all: Map<String, Any?> get() = values.keys().asSequence().associateWith { values.opt(it) }
    fun contains(key: String): Boolean = values.has(key)
    fun getString(key: String, default: String?): String? = if (values.has(key) && !values.isNull(key)) values.optString(key) else default
    fun getBoolean(key: String, default: Boolean): Boolean = if (values.has(key)) values.optBoolean(key, default) else default
    fun getInt(key: String, default: Int): Int = if (values.has(key)) values.optInt(key, default) else default
    fun getLong(key: String, default: Long): Long = if (values.has(key)) values.optLong(key, default) else default
    fun getFloat(key: String, default: Float): Float = if (values.has(key)) values.optDouble(key, default.toDouble()).toFloat() else default
    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = ArrayList<() -> Unit>()
        fun putString(key: String, value: String?): Editor { changes.add { values.put(key, value) }; return this }
        fun putBoolean(key: String, value: Boolean): Editor { changes.add { values.put(key, value) }; return this }
        fun putInt(key: String, value: Int): Editor { changes.add { values.put(key, value) }; return this }
        fun putLong(key: String, value: Long): Editor { changes.add { values.put(key, value) }; return this }
        fun putFloat(key: String, value: Float): Editor { changes.add { values.put(key, value.toDouble()) }; return this }
        fun remove(key: String): Editor { changes.add { values.remove(key) }; return this }
        fun clear(): Editor { changes.add { values.keys().asSequence().toList().forEach { values.remove(it) } }; return this }
        fun commit(): Boolean {
            changes.forEach { it() }
            changes.clear()
            return runCatching { file.writeText(values.toString()) }.isSuccess
        }
        fun apply() { commit() }
    }
}

class ContentResolver internal constructor() {
    @JvmOverloads
    fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream? = object : ByteArrayOutputStream() {
        private var closed = false
        override fun close() {
            if (closed) return
            closed = true
            platformExportFile(uri.name, uri.mime, toByteArray())
        }
    }
    fun openInputStream(uri: Uri): InputStream? = uri.bytes?.let { ByteArrayInputStream(it) }
}

