package android.database;
public class Cursor implements AutoCloseable {
    public static final int FIELD_TYPE_NULL = 0, FIELD_TYPE_INTEGER = 1, FIELD_TYPE_FLOAT = 2, FIELD_TYPE_STRING = 3, FIELD_TYPE_BLOB = 4;
    public int getCount() { return 0; }
    public int getColumnCount() { return 0; }
    public String[] getColumnNames() { return null; }
    public boolean moveToFirst() { return false; }
    public boolean moveToNext() { return false; }
    public boolean moveToPosition(int position) { return false; }
    public int getColumnIndex(String name) { return 0; }
    public int getColumnIndexOrThrow(String name) { return 0; }
    public String getColumnName(int index) { return null; }
    public int getType(int index) { return 0; }
    public boolean isNull(int index) { return false; }
    public long getLong(int index) { return 0; }
    public int getInt(int index) { return 0; }
    public double getDouble(int index) { return 0; }
    public String getString(int index) { return null; }
    @Override public void close() {}
}
