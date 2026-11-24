// Engine interface behind the android.database look-alikes. The app installs
// the real engine (the bundled SQLite driver) through Sqlite.opener at startup.
package compat.android

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

