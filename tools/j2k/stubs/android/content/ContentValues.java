package android.content;
import j2k.Nullable;
public class ContentValues {
    public ContentValues() {}
    public void put(String key, @Nullable String value) {}
    public void put(String key, Integer value) {}
    public void put(String key, Long value) {}
    public void put(String key, Double value) {}
    public void put(String key, Boolean value) {}
    public void putNull(String key) {}
    public Object get(String key) { return null; }
    public boolean containsKey(String key) { return false; }
    public java.util.Set<String> keySet() { return null; }
    public int size() { return 0; }
    public void remove(String key) {}
    public void clear() {}
}
