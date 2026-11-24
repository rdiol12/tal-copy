package android.database.sqlite;
import android.content.Context;
import j2k.Nullable;
public abstract class SQLiteOpenHelper implements AutoCloseable {
    public SQLiteOpenHelper(Context context, @Nullable String name, @Nullable Object factory, int version) {}
    public SQLiteDatabase getWritableDatabase() { return null; }
    public SQLiteDatabase getReadableDatabase() { return null; }
    public String getDatabaseName() { return null; }
    public void onConfigure(SQLiteDatabase db) {}
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
    public void onOpen(SQLiteDatabase db) {}
    @Override public void close() {}
}
