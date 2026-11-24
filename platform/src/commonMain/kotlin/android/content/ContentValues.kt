package android.content

class ContentValues {
    val values = LinkedHashMap<String, Any?>()
    fun put(key: String, value: String?) { values[key] = value }
    fun put(key: String, value: Int?) { values[key] = value?.toLong() }
    fun put(key: String, value: Long?) { values[key] = value }
    fun put(key: String, value: Double?) { values[key] = value }
    fun put(key: String, value: Boolean?) { values[key] = value?.let { if (it) 1L else 0L } }
    fun putNull(key: String) { values[key] = null }
    fun get(key: String): Any? = values[key]
    fun containsKey(key: String): Boolean = values.containsKey(key)
    fun keySet(): MutableSet<String> = values.keys
    fun size(): Int = values.size
    fun remove(key: String) { values.remove(key) }
    fun clear() = values.clear()
}

