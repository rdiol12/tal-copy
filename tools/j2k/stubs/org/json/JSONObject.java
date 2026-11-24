package org.json;
import j2k.Nullable;
import java.util.Iterator;
// API stub for the converter; the implementation is compat.json.JSONObject (Kotlin).
public class JSONObject {
    public static final Object NULL = new Object();
    public JSONObject() {}
    public JSONObject(String text) {}
    public JSONObject(java.util.Map<?, ?> copyFrom) {}
    public static String quote(@Nullable String data) { return null; }
    public static String numberToString(Number number) { return null; }
    public int length() { return 0; }
    public boolean has(String name) { return false; }
    public boolean isNull(String name) { return false; }
    public Iterator<String> keys() { return null; }
    public JSONArray names() { return null; }
    public Object remove(String name) { return null; }
    public JSONObject put(String name, @Nullable Object value) { return this; }
    public JSONObject put(String name, boolean value) { return this; }
    public JSONObject put(String name, int value) { return this; }
    public JSONObject put(String name, long value) { return this; }
    public JSONObject put(String name, double value) { return this; }
    public JSONObject putOpt(@Nullable String name, @Nullable Object value) { return this; }
    public Object opt(String name) { return null; }
    public Object get(String name) { return null; }
    public boolean getBoolean(String name) { return false; }
    public boolean optBoolean(String name) { return false; }
    public boolean optBoolean(String name, boolean fallback) { return false; }
    public double getDouble(String name) { return 0; }
    public double optDouble(String name) { return 0; }
    public double optDouble(String name, double fallback) { return 0; }
    public int getInt(String name) { return 0; }
    public int optInt(String name) { return 0; }
    public int optInt(String name, int fallback) { return 0; }
    public long getLong(String name) { return 0; }
    public long optLong(String name) { return 0; }
    public long optLong(String name, long fallback) { return 0; }
    public String getString(String name) { return null; }
    public String optString(String name) { return null; }
    public String optString(String name, @Nullable String fallback) { return null; }
    public JSONArray getJSONArray(String name) { return null; }
    public JSONArray optJSONArray(String name) { return null; }
    public JSONObject getJSONObject(String name) { return null; }
    public JSONObject optJSONObject(String name) { return null; }
    public String toString(int indentSpaces) { return null; }
}
