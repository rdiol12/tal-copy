package android.os

import compat.j.Runnable
import compat.util.locked
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The main-thread queue behind Handler, View.post and animators. The renderer installs
 * [poster] (run an action on the UI thread after a delay) and [frames] (call back every
 * frame until the callback returns false); until then actions wait in [pending].
 */
object MainQueue {
    class Entry(val delayMillis: Long, val action: () -> Unit) { @JvmField var cancel: (() -> Unit)? = null; @JvmField var cancelled = false }
    private val pending = ArrayList<Entry>()
    private val lock = compat.util.PlatformLock()
    var poster: ((delayMillis: Long, action: () -> Unit) -> (() -> Unit))? = null
        set(value) {
            field = value
            if (value == null) return
            val waiting = lock.locked { ArrayList(pending).also { pending.clear() } }
            for (entry in waiting) if (!entry.cancelled) entry.cancel = value(entry.delayMillis, entry.action)
        }
    var frames: ((callback: (nanos: Long) -> Boolean) -> Unit)? = null

    fun post(delayMillis: Long, action: () -> Unit): Entry {
        val entry = Entry(delayMillis, action)
        val post = poster
        if (post != null) entry.cancel = post(delayMillis, action) else lock.locked { pending.add(entry) }
        return entry
    }
    fun cancel(entry: Entry) {
        entry.cancelled = true
        entry.cancel?.invoke()
        lock.locked { pending.remove(entry) }
    }
    /** Calls [callback] with the frame time each frame until it returns false. */
    fun eachFrame(callback: (nanos: Long) -> Boolean) {
        val ticker = frames
        if (ticker != null) ticker(callback) else post(0) { callback(Long.MAX_VALUE) }
    }
}

class Looper private constructor() {
    val thread: Any? get() = null
    companion object {
        private val main = Looper()
        @JvmStatic fun getMainLooper(): Looper = main
        @JvmStatic fun myLooper(): Looper? = main
    }
}

open class Handler(looper: Looper?) {
    constructor() : this(Looper.getMainLooper())
    private val posted = ArrayList<Pair<Runnable, MainQueue.Entry>>()
    private val lock = compat.util.PlatformLock()
    fun post(action: Runnable?): Boolean = postDelayed(action, 0)
    fun postDelayed(action: Runnable?, delayMillis: Long): Boolean {
        if (action == null) return false
        var entry: MainQueue.Entry? = null
        entry = MainQueue.post(delayMillis) {
            lock.locked { posted.removeAll { it.second === entry } }
            action.run()
        }
        lock.locked { posted.add(action to entry) }
        return true
    }
    fun postAtFrontOfQueue(action: Runnable?): Boolean = post(action)
    fun removeCallbacks(action: Runnable?) {
        val matching = lock.locked { posted.filter { it.first === action }.also { posted.removeAll(it) } }
        matching.forEach { MainQueue.cancel(it.second) }
    }
    fun removeCallbacksAndMessages(token: Any?) {
        val all = lock.locked { ArrayList(posted).also { posted.clear() } }
        all.forEach { MainQueue.cancel(it.second) }
    }
    companion object { @JvmField val main = Handler(Looper.getMainLooper()) }
}

object SystemClock {
    @JvmStatic fun elapsedRealtime(): Long = compat.util.platformNanoTime() / 1_000_000L
    @JvmStatic fun uptimeMillis(): Long = elapsedRealtime()
}

class Build {
    object VERSION { const val SDK_INT = 34; const val RELEASE = "iOS" }
    object VERSION_CODES { const val O = 26; const val P = 28; const val Q = 29; const val R = 30; const val S = 31; const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34 }
    companion object { const val MODEL = "iPhone"; const val MANUFACTURER = "Apple" }
}

/** Typed key-value map used for saved state and navigation history. */
class Bundle() {
    private val values = HashMap<String, Any?>()
    constructor(source: Bundle?) : this() { if (source != null) values.putAll(source.values) }
    fun containsKey(key: String?): Boolean = values.containsKey(key)
    fun remove(key: String?) { values.remove(key) }
    fun clear() = values.clear()
    val isEmpty: Boolean get() = values.isEmpty()
    fun size(): Int = values.size
    fun keySet(): MutableSet<String> = values.keys
    fun get(key: String?): Any? = values[key]
    fun putAll(source: Bundle?) { if (source != null) values.putAll(source.values) }
    fun putInt(key: String, value: Int) { values[key] = value }
    fun putLong(key: String, value: Long) { values[key] = value }
    fun putBoolean(key: String, value: Boolean) { values[key] = value }
    fun putFloat(key: String, value: Float) { values[key] = value }
    fun putDouble(key: String, value: Double) { values[key] = value }
    fun putString(key: String, value: String?) { values[key] = value }
    fun putCharSequence(key: String, value: CharSequence?) { values[key] = value }
    fun putIntArray(key: String, value: IntArray?) { values[key] = value }
    fun putLongArray(key: String, value: LongArray?) { values[key] = value }
    fun putStringArray(key: String, value: Array<String>?) { values[key] = value }
    fun putStringArrayList(key: String, value: ArrayList<String>?) { values[key] = value }
    fun putBundle(key: String, value: Bundle?) { values[key] = value }
    fun putParcelable(key: String, value: Any?) { values[key] = value }
    fun putSerializable(key: String, value: Any?) { values[key] = value }
    fun <T> putParcelableArrayList(key: String, value: ArrayList<T>?) { values[key] = value }
    fun getInt(key: String): Int = getInt(key, 0)
    fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    fun getLong(key: String): Long = getLong(key, 0L)
    fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default
    fun getBoolean(key: String): Boolean = getBoolean(key, false)
    fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    fun getFloat(key: String): Float = values[key] as? Float ?: 0f
    fun getDouble(key: String): Double = values[key] as? Double ?: 0.0
    fun getString(key: String): String? = values[key] as? String
    fun getString(key: String, default: String?): String? = values[key] as? String ?: default
    fun getCharSequence(key: String): CharSequence? = values[key] as? CharSequence
    fun getIntArray(key: String): IntArray? = values[key] as? IntArray
    fun getLongArray(key: String): LongArray? = values[key] as? LongArray
    fun getStringArrayList(key: String): ArrayList<String>? { @Suppress("UNCHECKED_CAST") return values[key] as? ArrayList<String> }
    fun getBundle(key: String): Bundle? = values[key] as? Bundle
    fun <T> getParcelable(key: String): T? { @Suppress("UNCHECKED_CAST") return values[key] as? T }
    fun <T> getParcelableArrayList(key: String): ArrayList<T>? { @Suppress("UNCHECKED_CAST") return values[key] as? ArrayList<T> }
}

class CancellationSignal {
    fun interface OnCancelListener { fun onCancel() }
    var isCanceled: Boolean = false
        private set
    private var listener: OnCancelListener? = null
    fun cancel() { if (!isCanceled) { isCanceled = true; listener?.onCancel() } }
    fun setOnCancelListener(listener: OnCancelListener?) { this.listener = listener }
}

class LocaleList private constructor(val tags: String) {
    companion object { @JvmStatic fun forLanguageTags(tags: String?): LocaleList = LocaleList(tags ?: "") }
}
