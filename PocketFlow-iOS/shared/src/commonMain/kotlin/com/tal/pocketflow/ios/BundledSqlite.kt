// Connects the android.database look-alikes to the SQLite engine bundled with
// androidx.sqlite (the same library on iOS and on the JVM used for tests).
package com.tal.pocketflow

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import compat.android.Sqlite
import compat.android.SqliteConnection
import compat.android.SqliteStatement

private class BundledStatement(private val statement: SQLiteStatement) : SqliteStatement {
    override fun bindNull(index: Int) = statement.bindNull(index)
    override fun bindLong(index: Int, value: Long) = statement.bindLong(index, value)
    override fun bindDouble(index: Int, value: Double) = statement.bindDouble(index, value)
    override fun bindText(index: Int, value: String) = statement.bindText(index, value)
    override fun step(): Boolean = statement.step()
    override val columnCount: Int get() = statement.getColumnCount()
    override fun columnName(index: Int): String = statement.getColumnName(index)
    override fun columnType(index: Int): Int = statement.getColumnType(index)
    override fun getLong(index: Int): Long = statement.getLong(index)
    override fun getDouble(index: Int): Double = statement.getDouble(index)
    override fun getText(index: Int): String = statement.getText(index)
    override fun close() = statement.close()
}

private class BundledConnection(private val connection: SQLiteConnection) : SqliteConnection {
    override fun prepare(sql: String): SqliteStatement = BundledStatement(connection.prepare(sql))
    override fun close() = connection.close()
}

/** Call once at startup, before the first database access. */
fun installSqlite() {
    val driver = BundledSQLiteDriver()
    Sqlite.opener = { path -> BundledConnection(driver.open(path)) }
}
