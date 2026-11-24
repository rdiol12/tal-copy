package org.json;
import j2k.Nullable;
// API stub for the converter; the implementation is compat.json.JSONArray (Kotlin).
public class JSONArray {
    public JSONArray() {}
    public JSONArray(String text) {}
    public JSONArray(java.util.Collection<?> copyFrom) {}
    public int length() { return 0; }
    public JSONArray put(@Nullable Object value) { return this; }
    public JSONArray put(boolean value) { return this; }
    public JSONArray put(int value) { return this; }
    public JSONArray put(long value) { return this; }
    public JSONArray put(double value) { return this; }
    public JSONArray put(int index, @Nullable Object value) { return this; }
    public Object remove(int index) { return null; }
    public boolean isNull(int index) { return false; }
    public Object opt(int index) { return null; }
    public Object get(int index) { return null; }
    public boolean getBoolean(int index) { return false; }
    public boolean optBoolean(int index) { return false; }
    public boolean optBoolean(int index, boolean fallback) { return false; }
    public double getDouble(int index) { return 0; }
    public double optDouble(int index) { return 0; }
    public double optDouble(int index, double fallback) { return 0; }
    public int getInt(int index) { return 0; }
    public int optInt(int index) { return 0; }
    public int optInt(int index, int fallback) { return 0; }
    public long getLong(int index) { return 0; }
    public long optLong(int index) { return 0; }
    public long optLong(int index, long fallback) { return 0; }
    public String getString(int index) { return null; }
    public String optString(int index) { return null; }
    public String optString(int index, @Nullable String fallback) { return null; }
    public JSONArray getJSONArray(int index) { return null; }
    public JSONArray optJSONArray(int index) { return null; }
    public JSONObject getJSONObject(int index) { return null; }
    public JSONObject optJSONObject(int index) { return null; }
    public String toString(int indentSpaces) { return null; }
}
