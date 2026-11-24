package android.content;
import j2k.Nullable;
public class SharedPreferences {
    public java.util.Map<String, ?> getAll() { return null; }
    public boolean contains(String key) { return false; }
    public String getString(String key, @Nullable String fallback) { return null; }
    public boolean getBoolean(String key, boolean fallback) { return false; }
    public int getInt(String key, int fallback) { return 0; }
    public long getLong(String key, long fallback) { return 0; }
    public float getFloat(String key, float fallback) { return 0; }
    public Editor edit() { return null; }
    public class Editor {
        public Editor putString(String key, @Nullable String value) { return this; }
        public Editor putBoolean(String key, boolean value) { return this; }
        public Editor putInt(String key, int value) { return this; }
        public Editor putLong(String key, long value) { return this; }
        public Editor putFloat(String key, float value) { return this; }
        public Editor remove(String key) { return this; }
        public Editor clear() { return this; }
        public boolean commit() { return false; }
        public void apply() {}
    }
}
