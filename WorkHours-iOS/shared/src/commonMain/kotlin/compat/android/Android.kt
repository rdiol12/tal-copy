// Tiny look-alikes of the Android classes the app touches outside of Compose:
// Context, SharedPreferences, AtomicFile, ContentResolver/Uri, Log.
// They let the Android sources compile unchanged apart from their imports.
package compat.android

import compat.io.ByteArrayInputStream
import compat.io.ByteArrayOutputStream
import compat.io.File
import compat.io.FileNotFoundException
import compat.io.InputStream
import compat.io.OutputStream
import compat.io.platformCacheDirectory
import compat.io.platformFilesDirectory
import compat.json.JSONObject

class Context private constructor() {
    val applicationContext: Context get() = this
    val baseContext: Context get() = this
    val filesDir: File get() = File(platformFilesDirectory())
    val cacheDir: File get() = File(platformCacheDirectory())
    /** Kept out of device backups (used for credentials and sync bookkeeping). */
    val noBackupFilesDir: File get() = File(filesDir, "no-backup").also { platformExcludeFromBackup(it.path) }
    val contentResolver: ContentResolver = ContentResolver()
    val packageName: String get() = "com.tal.workhours"

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { SharedPreferences(File(filesDir, "prefs-$name.json")) }

    fun getString(id: Int, vararg args: Any?): String = stringResolver(id, args)

    companion object {
        const val MODE_PRIVATE = 0
        /** The single application context. */
        val app: Context = Context()
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

/** android.util.AtomicFile: a write either replaces the file completely or not at all. */
class AtomicFile(val baseFile: File) {
    private class Pending : ByteArrayOutputStream()

    fun startWrite(): OutputStream = Pending()
    fun finishWrite(stream: OutputStream?) {
        val pending = stream as? Pending ?: return
        baseFile.writeBytes(pending.toByteArray())
    }
    fun failWrite(stream: OutputStream?) {}
    fun openRead(): InputStream {
        if (!baseFile.exists()) throw FileNotFoundException("${baseFile.path} (No such file or directory)")
        return ByteArrayInputStream(baseFile.readBytes())
    }
    fun readFully(): ByteArray = baseFile.readBytes()
    fun delete() { baseFile.delete() }
}

/**
 * Stand-in for a document Uri. An export Uri collects the bytes the app writes
 * and hands them to the system "save file" sheet when the stream is closed; an
 * import Uri carries the bytes of the file the user picked.
 */
class Uri internal constructor(internal val name: String, internal val mime: String, internal val bytes: ByteArray?) {
    override fun toString(): String = "document:$name"
}

class ContentResolver internal constructor() {
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

object Log {
    fun d(tag: String, message: String): Int { println("D/$tag: $message"); return 0 }
    fun i(tag: String, message: String): Int { println("I/$tag: $message"); return 0 }
    fun w(tag: String, message: String): Int { println("W/$tag: $message"); return 0 }
    fun w(tag: String, message: String, error: Throwable?): Int { println("W/$tag: $message ${error?.message.orEmpty()}"); return 0 }
    fun e(tag: String, message: String): Int { println("E/$tag: $message"); return 0 }
    fun e(tag: String, message: String, error: Throwable?): Int { println("E/$tag: $message ${error?.message.orEmpty()}"); return 0 }
}

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class StringRes

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class DrawableRes

// ---- Platform hooks ----
/** Shows the system sheet for saving [bytes] as a file named [name]. */
expect fun platformExportFile(name: String, mime: String, bytes: ByteArray)
/** Creates the directory if needed and marks it as not to be included in device backups. */
expect fun platformExcludeFromBackup(directory: String)
/** Calls back whenever the app comes to the foreground / goes to the background. */
expect fun platformObserveForeground(onForeground: () -> Unit, onBackground: () -> Unit)
/** Lets the user pick a file; calls back with its bytes, or null if cancelled. */
expect fun platformImportFile(mimeTypes: List<String>, onResult: (ByteArray?) -> Unit)
