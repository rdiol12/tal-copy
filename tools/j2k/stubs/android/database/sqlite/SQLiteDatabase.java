package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import j2k.Nullable;
public class SQLiteDatabase {
    public static final int CONFLICT_NONE = 0, CONFLICT_ROLLBACK = 1, CONFLICT_ABORT = 2, CONFLICT_FAIL = 3, CONFLICT_IGNORE = 4, CONFLICT_REPLACE = 5;
    public void execSQL(String sql) {}
    public void execSQL(String sql, Object[] bindArgs) {}
    public Cursor rawQuery(String sql, @Nullable String[] selectionArgs) { return null; }
    public Cursor query(String table, @Nullable String[] columns, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String groupBy, @Nullable String having, @Nullable String orderBy) { return null; }
    public long insert(String table, @Nullable String nullColumnHack, ContentValues values) { return 0; }
    public long insertOrThrow(String table, @Nullable String nullColumnHack, ContentValues values) { return 0; }
    public long insertWithOnConflict(String table, @Nullable String nullColumnHack, ContentValues values, int conflictAlgorithm) { return 0; }
    public int update(String table, ContentValues values, @Nullable String whereClause, @Nullable String[] whereArgs) { return 0; }
    public int delete(String table, @Nullable String whereClause, @Nullable String[] whereArgs) { return 0; }
    public void beginTransaction() {}
    public void beginTransactionNonExclusive() {}
    public void setTransactionSuccessful() {}
    public void endTransaction() {}
    public boolean inTransaction() { return false; }
    public void setForeignKeyConstraintsEnabled(boolean enable) {}
    public boolean isReadOnly() { return false; }
    public boolean isOpen() { return false; }
    public int getVersion() { return 0; }
    public void setVersion(int version) {}
    public void close() {}
}
