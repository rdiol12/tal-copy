package android.database

/** A fully-read result set. */
class Cursor(private val names: List<String>, private val rows: List<Array<Any?>>) : AutoCloseable {
    private var position = -1
    val count: Int get() = rows.size
    val columnCount: Int get() = names.size
    val columnNames: Array<String> get() = names.toTypedArray()

    fun moveToFirst(): Boolean { position = 0; return rows.isNotEmpty() }
    fun moveToNext(): Boolean { if (position < rows.size) position++; return position < rows.size }
    fun moveToPosition(target: Int): Boolean { position = target.coerceIn(-1, rows.size); return target in rows.indices }
    fun getColumnIndex(name: String): Int = names.indexOfFirst { it.equals(name, ignoreCase = true) }
    fun getColumnIndexOrThrow(name: String): Int = getColumnIndex(name).also { if (it < 0) throw IllegalArgumentException("column '$name' does not exist") }
    fun getColumnName(index: Int): String = names[index]
    private fun value(index: Int): Any? {
        if (position !in rows.indices) throw IndexOutOfBoundsException("Cursor index $position requested, with a size of ${rows.size}")
        return rows[position][index]
    }
    fun getType(index: Int): Int = when (value(index)) {
        null -> FIELD_TYPE_NULL; is Long -> FIELD_TYPE_INTEGER; is Double -> FIELD_TYPE_FLOAT; is String -> FIELD_TYPE_STRING; else -> FIELD_TYPE_BLOB
    }
    fun isNull(index: Int): Boolean = value(index) == null
    fun getLong(index: Int): Long = when (val v = value(index)) { null -> 0L; is Long -> v; is Double -> v.toLong(); else -> v.toString().toLongOrNull() ?: v.toString().toDoubleOrNull()?.toLong() ?: 0L }
    fun getInt(index: Int): Int = getLong(index).toInt()
    fun getDouble(index: Int): Double = when (val v = value(index)) { null -> 0.0; is Double -> v; is Long -> v.toDouble(); else -> v.toString().toDoubleOrNull() ?: 0.0 }
    fun getString(index: Int): String? = when (val v = value(index)) { null -> null; is Double -> if (v == v.toLong().toDouble() && kotlin.math.abs(v) < 1e15) v.toLong().toString() + ".0" else v.toString(); else -> v.toString() }
    override fun close() {}

    companion object {
        const val FIELD_TYPE_NULL = 0
        const val FIELD_TYPE_INTEGER = 1
        const val FIELD_TYPE_FLOAT = 2
        const val FIELD_TYPE_STRING = 3
        const val FIELD_TYPE_BLOB = 4
    }
}

