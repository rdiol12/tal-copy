// android.database look-alikes (SQLiteOpenHelper, SQLiteDatabase, Cursor,
// ContentValues) on top of a tiny engine interface. The app installs the real
// engine (the bundled SQLite driver) through Sqlite.opener at startup.
package compat.android

import compat.io.File

interface SqliteStatement {
    /** Parameter indexes are 1-based, column indexes 0-based, as in SQLite. */
    fun bindNull(index: Int)
    fun bindLong(index: Int, value: Long)
    fun bindDouble(index: Int, value: Double)
    fun bindText(index: Int, value: String)
    fun step(): Boolean
    val columnCount: Int
    fun columnName(index: Int): String
    /** 1 = integer, 2 = float, 3 = text, 4 = blob, 5 = null. */
    fun columnType(index: Int): Int
    fun getLong(index: Int): Long
    fun getDouble(index: Int): Double
    fun getText(index: Int): String
    fun close()
}

interface SqliteConnection {
    fun prepare(sql: String): SqliteStatement
    fun close()
}

object Sqlite {
    /** Opens (creating if needed) the database file at the given path. */
    var opener: (path: String) -> SqliteConnection = { error("No SQLite engine installed (set compat.android.Sqlite.opener)") }
}

class SQLiteException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ContentValues {
    internal val values = LinkedHashMap<String, Any?>()
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

/** A fully-read result set. */
class Cursor internal constructor(private val names: List<String>, private val rows: List<Array<Any?>>) : AutoCloseable {
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

class SQLiteDatabase internal constructor(private val connection: SqliteConnection) {
    private var depth = 0
    private var successful = false
    private var failed = false
    var isOpen: Boolean = true
        private set
    val isReadOnly: Boolean get() = false

    var version: Int
        get() = rawQuery("PRAGMA user_version", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        set(value) = execSQL("PRAGMA user_version = $value")

    private fun bind(statement: SqliteStatement, args: Array<out Any?>?) {
        args?.forEachIndexed { i, arg ->
            when (arg) {
                null -> statement.bindNull(i + 1)
                is Long -> statement.bindLong(i + 1, arg)
                is Int -> statement.bindLong(i + 1, arg.toLong())
                is Short -> statement.bindLong(i + 1, arg.toLong())
                is Byte -> statement.bindLong(i + 1, arg.toLong())
                is Boolean -> statement.bindLong(i + 1, if (arg) 1L else 0L)
                is Double -> statement.bindDouble(i + 1, arg)
                is Float -> statement.bindDouble(i + 1, arg.toDouble())
                else -> statement.bindText(i + 1, arg.toString())
            }
        }
    }
    private inline fun <T> run(sql: String, args: Array<out Any?>?, block: (SqliteStatement) -> T): T {
        val statement = try { connection.prepare(sql) } catch (e: Exception) { throw SQLiteException("${e.message} (while compiling: $sql)", e) }
        try { bind(statement, args); return block(statement) }
        catch (e: SQLiteException) { throw e }
        catch (e: Exception) { throw SQLiteException("${e.message} (while executing: $sql)", e) }
        finally { statement.close() }
    }

    fun execSQL(sql: String) = execSQL(sql, null)
    fun execSQL(sql: String, bindArgs: Array<out Any?>?) { run(sql, bindArgs) { while (it.step()) { /* drain */ } } }

    fun rawQuery(sql: String, selectionArgs: Array<out String?>?): Cursor = run(sql, selectionArgs) { statement ->
        val names = (0 until statement.columnCount).map { statement.columnName(it) }
        val rows = ArrayList<Array<Any?>>()
        while (statement.step()) {
            rows.add(Array(names.size) { i ->
                when (statement.columnType(i)) { 1 -> statement.getLong(i); 2 -> statement.getDouble(i); 3 -> statement.getText(i); 5 -> null; else -> statement.getText(i) }
            })
        }
        Cursor(names, rows)
    }
    fun query(table: String, columns: Array<out String?>?, selection: String?, selectionArgs: Array<out String?>?, groupBy: String?, having: String?, orderBy: String?): Cursor {
        val sql = StringBuilder("SELECT ").append(if (columns.isNullOrEmpty()) "*" else columns.joinToString(",")).append(" FROM ").append(table)
        if (!selection.isNullOrEmpty()) sql.append(" WHERE ").append(selection)
        if (!groupBy.isNullOrEmpty()) sql.append(" GROUP BY ").append(groupBy)
        if (!having.isNullOrEmpty()) sql.append(" HAVING ").append(having)
        if (!orderBy.isNullOrEmpty()) sql.append(" ORDER BY ").append(orderBy)
        return rawQuery(sql.toString(), selectionArgs)
    }

    private fun scalar(sql: String): Long = run(sql, null) { if (it.step()) it.getLong(0) else 0L }
    fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues): Long = insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE)
    fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long = try { insertOrThrow(table, nullColumnHack, values) } catch (e: SQLiteException) { -1L }
    fun insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int): Long {
        val keys = values.values.keys.toList()
        val verb = "INSERT" + CONFLICT_VERBS[conflictAlgorithm]
        val sql = if (keys.isEmpty()) "$verb INTO $table DEFAULT VALUES"
            else "$verb INTO $table(${keys.joinToString(",")}) VALUES(${keys.joinToString(",") { "?" }})"
        execSQL(sql, keys.map { values.values[it] }.toTypedArray())
        return if (scalar("SELECT changes()") > 0) scalar("SELECT last_insert_rowid()") else -1L
    }
    fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<out String?>?): Int {
        val keys = values.values.keys.toList()
        if (keys.isEmpty()) throw IllegalArgumentException("Empty values")
        val sql = "UPDATE $table SET ${keys.joinToString(",") { "$it=?" }}" + (if (whereClause.isNullOrEmpty()) "" else " WHERE $whereClause")
        execSQL(sql, (keys.map { values.values[it] } + (whereArgs?.toList() ?: emptyList())).toTypedArray())
        return scalar("SELECT changes()").toInt()
    }
    fun delete(table: String, whereClause: String?, whereArgs: Array<out String?>?): Int {
        execSQL("DELETE FROM $table" + (if (whereClause.isNullOrEmpty()) "" else " WHERE $whereClause"), whereArgs)
        return scalar("SELECT changes()").toInt()
    }

    // Nested transactions behave like Android's: the outermost endTransaction commits only if every level was marked successful.
    fun beginTransaction() {
        if (depth == 0) { execSQL("BEGIN IMMEDIATE"); failed = false }
        depth++
        successful = false
    }
    fun beginTransactionNonExclusive() = beginTransaction()
    fun setTransactionSuccessful() { successful = true }
    fun endTransaction() {
        if (depth == 0) throw IllegalStateException("Cannot perform this operation because there is no current transaction.")
        if (!successful) failed = true
        successful = false
        depth--
        if (depth == 0) execSQL(if (failed) "ROLLBACK" else "COMMIT")
    }
    fun inTransaction(): Boolean = depth > 0
    fun setForeignKeyConstraintsEnabled(enable: Boolean) = execSQL("PRAGMA foreign_keys = " + (if (enable) "ON" else "OFF"))
    fun close() { if (isOpen) { isOpen = false; connection.close() } }

    companion object {
        const val CONFLICT_NONE = 0
        const val CONFLICT_ROLLBACK = 1
        const val CONFLICT_ABORT = 2
        const val CONFLICT_FAIL = 3
        const val CONFLICT_IGNORE = 4
        const val CONFLICT_REPLACE = 5
        private val CONFLICT_VERBS = arrayOf("", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE")
    }
}

abstract class SQLiteOpenHelper(context: Context?, val databaseName: String?, factory: Any?, private val targetVersion: Int) : AutoCloseable {
    private val owner: Context = context?.applicationContext ?: Context.app
    private var database: SQLiteDatabase? = null

    val writableDatabase: SQLiteDatabase get() = database?.takeIf { it.isOpen } ?: open()
    val readableDatabase: SQLiteDatabase get() = writableDatabase

    private fun open(): SQLiteDatabase {
        val path = if (databaseName == null) ":memory:" else owner.getDatabasePath(databaseName).path
        val db = SQLiteDatabase(Sqlite.opener(path))
        try {
            onConfigure(db)
            val current = db.version
            if (current != targetVersion) {
                db.beginTransaction()
                try {
                    if (current == 0) onCreate(db)
                    else if (current > targetVersion) throw SQLiteException("Can't downgrade database from version $current to $targetVersion")
                    else onUpgrade(db, current, targetVersion)
                    db.version = targetVersion
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            onOpen(db)
        } catch (e: Throwable) { db.close(); throw e }
        database = db
        return db
    }

    open fun onConfigure(db: SQLiteDatabase?) {}
    abstract fun onCreate(db: SQLiteDatabase?)
    abstract fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int)
    open fun onOpen(db: SQLiteDatabase?) {}
    override fun close() { database?.close(); database = null }
}
