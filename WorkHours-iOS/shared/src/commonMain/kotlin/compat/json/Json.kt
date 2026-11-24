// Pure-Kotlin stand-in for org.json as shipped with Android (same method
// names, same coercion rules, insertion-ordered keys, same number output).
package compat.json

class JSONException(message: String) : RuntimeException(message)

private fun numberToString(number: Number): String {
    if (number is Double || number is Float) {
        val d = number.toDouble()
        if (d.isNaN() || d.isInfinite()) throw JSONException("Forbidden numeric value: $d")
        if (d == 0.0 && 1.0 / d < 0) return "-0"
        val asLong = d.toLong()
        if (d == asLong.toDouble() && asLong != Long.MAX_VALUE && asLong != Long.MIN_VALUE) return asLong.toString()
    }
    return number.toString()
}

private fun checkNumber(value: Any?): Any? {
    if (value is Double && (value.isNaN() || value.isInfinite())) throw JSONException("Forbidden numeric value: $value")
    if (value is Float && (value.isNaN() || value.isInfinite())) throw JSONException("Forbidden numeric value: $value")
    return value
}

private fun writeString(out: StringBuilder, value: String) {
    out.append('"')
    for (c in value) {
        when (c) {
            '"', '\\', '/' -> out.append('\\').append(c)
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\u000C' -> out.append("\\f")
            else -> if (c.code <= 0x1F) out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
        }
    }
    out.append('"')
}

private fun writeValue(out: StringBuilder, value: Any?, indent: Int, depth: Int) {
    when (value) {
        null, JSONObject.NULL -> out.append("null")
        is JSONObject -> value.write(out, indent, depth)
        is JSONArray -> value.write(out, indent, depth)
        is Boolean -> out.append(value.toString())
        is Number -> out.append(numberToString(value))
        else -> writeString(out, value.toString())
    }
}

private fun newline(out: StringBuilder, indent: Int, depth: Int) {
    if (indent <= 0) return
    out.append('\n')
    repeat(indent * depth) { out.append(' ') }
}

private fun typeMismatch(key: Any, value: Any?, type: String): Nothing = throw JSONException(
    if (value == null) "Value at $key is null." else "Value $value at $key of type ${value::class.simpleName} cannot be converted to $type")

private fun toBoolean(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> if (value.equals("true", ignoreCase = true)) true else if (value.equals("false", ignoreCase = true)) false else null
    else -> null
}
private fun toDouble(value: Any?): Double? = when (value) {
    is Double -> value
    is Number -> value.toDouble()
    is String -> value.toDoubleOrNull()
    else -> null
}
private fun toInt(value: Any?): Int? = when (value) {
    is Int -> value
    is Number -> value.toInt()
    is String -> value.toDoubleOrNull()?.toInt()
    else -> null
}
private fun toLong(value: Any?): Long? = when (value) {
    is Long -> value
    is Number -> value.toLong()
    is String -> value.toDoubleOrNull()?.toLong()
    else -> null
}
private fun toText(value: Any?): String? = when (value) {
    is String -> value
    null -> null
    is Number -> numberToString(value)
    else -> value.toString()
}

class JSONObject() {
    private val map = LinkedHashMap<String, Any>()

    constructor(text: String) : this() {
        val value = JSONTokener(text).nextValue()
        if (value !is JSONObject) throw JSONException("Value ${toText(value)} of type ${value::class.simpleName} cannot be converted to JSONObject")
        map.putAll(value.map)
    }

    constructor(copyFrom: Map<*, *>) : this() {
        for ((k, v) in copyFrom) {
            if (k == null) throw NullPointerException("key == null")
            map[k as String] = wrap(v) ?: NULL
        }
    }

    fun length(): Int = map.size
    fun has(name: String): Boolean = map.containsKey(name)
    fun isNull(name: String): Boolean = map[name].let { it == null || it === NULL }
    fun keys(): MutableIterator<String> = map.keys.toMutableList().iterator()
    fun keySet(): Set<String> = map.keys
    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray().also { a -> map.keys.forEach { a.put(it) } }
    fun remove(name: String): Any? = map.remove(name)

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) { map.remove(name); return this }
        map[name] = checkNumber(value)!!
        return this
    }
    fun putOpt(name: String?, value: Any?): JSONObject = if (name == null || value == null) this else put(name, value)

    fun opt(name: String): Any? = map[name]
    fun get(name: String): Any = map[name] ?: throw JSONException("No value for $name")

    fun getBoolean(name: String): Boolean = get(name).let { toBoolean(it) ?: typeMismatch(name, it, "boolean") }
    fun optBoolean(name: String, fallback: Boolean = false): Boolean = toBoolean(opt(name)) ?: fallback
    fun getDouble(name: String): Double = get(name).let { toDouble(it) ?: typeMismatch(name, it, "double") }
    fun optDouble(name: String, fallback: Double = Double.NaN): Double = toDouble(opt(name)) ?: fallback
    fun getInt(name: String): Int = get(name).let { toInt(it) ?: typeMismatch(name, it, "int") }
    fun optInt(name: String, fallback: Int = 0): Int = toInt(opt(name)) ?: fallback
    fun getLong(name: String): Long = get(name).let { toLong(it) ?: typeMismatch(name, it, "long") }
    fun optLong(name: String, fallback: Long = 0L): Long = toLong(opt(name)) ?: fallback
    fun getString(name: String): String = get(name).let { toText(it) ?: typeMismatch(name, it, "String") }
    fun optString(name: String, fallback: String = ""): String = toText(opt(name)) ?: fallback
    fun getJSONArray(name: String): JSONArray = get(name).let { it as? JSONArray ?: typeMismatch(name, it, "JSONArray") }
    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray
    fun getJSONObject(name: String): JSONObject = get(name).let { it as? JSONObject ?: typeMismatch(name, it, "JSONObject") }
    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject

    internal fun write(out: StringBuilder, indent: Int, depth: Int) {
        if (map.isEmpty()) { out.append("{}"); return }
        out.append('{')
        var first = true
        for ((key, value) in map) {
            if (!first) out.append(',')
            first = false
            newline(out, indent, depth + 1)
            writeString(out, key)
            out.append(if (indent > 0) ": " else ":")
            writeValue(out, value, indent, depth + 1)
        }
        newline(out, indent, depth)
        out.append('}')
    }

    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()
    fun toString(indentSpaces: Int): String = StringBuilder().also { write(it, indentSpaces, 0) }.toString()

    companion object {
        /** Sentinel for an explicit JSON null, as in org.json. */
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other === this || other == null
            override fun hashCode(): Int = 0
            override fun toString(): String = "null"
        }

        fun quote(data: String?): String {
            if (data == null) return "\"\""
            return StringBuilder().also { writeString(it, data) }.toString()
        }

        fun numberToString(number: Number): String = compat.json.numberToString(number)

        fun wrap(value: Any?): Any? = when (value) {
            null -> NULL
            is JSONObject, is JSONArray, is Boolean, is Number, is String -> value
            NULL -> value
            is Map<*, *> -> JSONObject(value)
            is Collection<*> -> JSONArray(value)
            is Char -> value.toString()
            else -> value.toString()
        }
    }
}

class JSONArray() {
    private val values = ArrayList<Any?>()

    constructor(text: String) : this() {
        val value = JSONTokener(text).nextValue()
        if (value !is JSONArray) throw JSONException("Value ${toText(value)} of type ${value::class.simpleName} cannot be converted to JSONArray")
        values.addAll(value.values)
    }

    constructor(copyFrom: Collection<*>) : this() {
        for (item in copyFrom) put(JSONObject.wrap(item))
    }

    fun length(): Int = values.size
    fun put(value: Any?): JSONArray { values.add(checkNumber(value)); return this }
    fun put(index: Int, value: Any?): JSONArray {
        while (values.size <= index) values.add(null)
        values[index] = checkNumber(value)
        return this
    }
    fun remove(index: Int): Any? = if (index in values.indices) values.removeAt(index) else null
    fun isNull(index: Int): Boolean = opt(index).let { it == null || it === JSONObject.NULL }
    fun opt(index: Int): Any? = if (index in values.indices) values[index] else null
    fun get(index: Int): Any {
        if (index !in values.indices) throw JSONException("Index $index out of range [0..${values.size})")
        return values[index] ?: throw JSONException("Value at $index is null.")
    }

    fun getBoolean(index: Int): Boolean = get(index).let { toBoolean(it) ?: typeMismatch(index, it, "boolean") }
    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = toBoolean(opt(index)) ?: fallback
    fun getDouble(index: Int): Double = get(index).let { toDouble(it) ?: typeMismatch(index, it, "double") }
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = toDouble(opt(index)) ?: fallback
    fun getInt(index: Int): Int = get(index).let { toInt(it) ?: typeMismatch(index, it, "int") }
    fun optInt(index: Int, fallback: Int = 0): Int = toInt(opt(index)) ?: fallback
    fun getLong(index: Int): Long = get(index).let { toLong(it) ?: typeMismatch(index, it, "long") }
    fun optLong(index: Int, fallback: Long = 0L): Long = toLong(opt(index)) ?: fallback
    fun getString(index: Int): String = get(index).let { toText(it) ?: typeMismatch(index, it, "String") }
    fun optString(index: Int, fallback: String = ""): String = toText(opt(index)) ?: fallback
    fun getJSONArray(index: Int): JSONArray = get(index).let { it as? JSONArray ?: typeMismatch(index, it, "JSONArray") }
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray
    fun getJSONObject(index: Int): JSONObject = get(index).let { it as? JSONObject ?: typeMismatch(index, it, "JSONObject") }
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    internal fun write(out: StringBuilder, indent: Int, depth: Int) {
        if (values.isEmpty()) { out.append("[]"); return }
        out.append('[')
        for ((i, value) in values.withIndex()) {
            if (i > 0) out.append(',')
            newline(out, indent, depth + 1)
            writeValue(out, value, indent, depth + 1)
        }
        newline(out, indent, depth)
        out.append(']')
    }

    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()
    fun toString(indentSpaces: Int): String = StringBuilder().also { write(it, indentSpaces, 0) }.toString()
    override fun equals(other: Any?): Boolean = other is JSONArray && other.values == values
    override fun hashCode(): Int = values.hashCode()
}

/** Lenient parser with the same behaviour as Android's JSONTokener for well-formed input. */
class JSONTokener(private val input: String) {
    private var pos = 0

    fun nextValue(): Any {
        return when (val c = nextCleanInternal()) {
            -1 -> throw syntaxError("End of input")
            '{'.code -> readObject()
            '['.code -> readArray()
            '\''.code, '"'.code -> nextString(c.toChar())
            else -> { pos--; readLiteral() }
        }
    }

    private fun nextCleanInternal(): Int {
        while (pos < input.length) {
            val c = input[pos++]
            when (c) {
                '\t', ' ', '\n', '\r', '﻿' -> continue
                '/' -> {
                    if (pos == input.length) return c.code
                    when (input[pos]) {
                        '*' -> {
                            val end = input.indexOf("*/", pos + 1)
                            if (end == -1) throw syntaxError("Unterminated comment")
                            pos = end + 2
                        }
                        '/' -> { pos++; skipToEndOfLine() }
                        else -> return c.code
                    }
                }
                '#' -> skipToEndOfLine()
                else -> return c.code
            }
        }
        return -1
    }

    private fun skipToEndOfLine() {
        while (pos < input.length) {
            val c = input[pos]
            if (c == '\r' || c == '\n') { pos++; break }
            pos++
        }
    }

    private fun nextString(quote: Char): String {
        var builder: StringBuilder? = null
        var start = pos
        while (pos < input.length) {
            val c = input[pos++]
            if (c == quote) {
                return if (builder == null) input.substring(start, pos - 1) else builder.append(input, start, pos - 1).toString()
            }
            if (c == '\\') {
                if (pos == input.length) throw syntaxError("Unterminated escape sequence")
                if (builder == null) builder = StringBuilder()
                builder.append(input, start, pos - 1)
                builder.append(readEscapeCharacter())
                start = pos
            }
        }
        throw syntaxError("Unterminated string")
    }

    private fun readEscapeCharacter(): Char {
        return when (val escaped = input[pos++]) {
            'u' -> {
                if (pos + 4 > input.length) throw syntaxError("Unterminated escape sequence")
                val hex = input.substring(pos, pos + 4)
                pos += 4
                (hex.toIntOrNull(16) ?: throw syntaxError("Invalid escape sequence: $hex")).toChar()
            }
            't' -> '\t'
            'b' -> '\b'
            'n' -> '\n'
            'r' -> '\r'
            'f' -> '\u000C'
            else -> escaped
        }
    }

    private fun readLiteral(): Any {
        val literal = nextToInternal("{}[]/\\:,=;# \t\u000C")
        if (literal.isEmpty()) throw syntaxError("Expected literal value")
        if (literal.equals("null", ignoreCase = true)) return JSONObject.NULL
        if (literal.equals("true", ignoreCase = true)) return true
        if (literal.equals("false", ignoreCase = true)) return false
        if (literal.indexOf('.') == -1) {
            var base = 10
            var number = literal
            if (number.startsWith("0x") || number.startsWith("0X")) { number = number.substring(2); base = 16 }
            else if (number.startsWith("0") && number.length > 1) { number = number.substring(1); base = 8 }
            val asLong = number.toLongOrNull(base)
            if (asLong != null) return if (asLong <= Int.MAX_VALUE && asLong >= Int.MIN_VALUE) asLong.toInt() else asLong
        }
        val asDouble = literal.toDoubleOrNull()
        if (asDouble != null && isNumericLiteral(literal)) return asDouble
        return literal
    }

    /** Kotlin's toDoubleOrNull accepts forms like "NaN" and "1f"; JSON literals must not. */
    private fun isNumericLiteral(text: String): Boolean = text.all { it in '0'..'9' || it == '+' || it == '-' || it == '.' || it == 'e' || it == 'E' }

    private fun nextToInternal(excluded: String): String {
        val start = pos
        while (pos < input.length) {
            val c = input[pos]
            if (c == '\r' || c == '\n' || excluded.indexOf(c) != -1) return input.substring(start, pos)
            pos++
        }
        return input.substring(start)
    }

    private fun readObject(): JSONObject {
        val result = JSONObject()
        val first = nextCleanInternal()
        if (first == '}'.code) return result
        if (first != -1) pos--
        while (true) {
            val name = nextValue()
            if (name !is String) throw syntaxError("Names must be strings, but $name is of type ${name::class.simpleName}")
            val separator = nextCleanInternal()
            if (separator != ':'.code && separator != '='.code) throw syntaxError("Expected ':' after $name")
            if (pos < input.length && input[pos] == '>') pos++
            result.put(name, nextValue())
            when (nextCleanInternal()) {
                '}'.code -> return result
                ';'.code, ','.code -> continue
                else -> throw syntaxError("Unterminated object")
            }
        }
    }

    private fun readArray(): JSONArray {
        val result = JSONArray()
        var hasTrailingSeparator = false
        while (true) {
            when (nextCleanInternal()) {
                -1 -> throw syntaxError("Unterminated array")
                ']'.code -> { if (hasTrailingSeparator) result.put(null); return result }
                ','.code, ';'.code -> { result.put(null); hasTrailingSeparator = true; continue }
                else -> pos--
            }
            result.put(nextValue())
            when (nextCleanInternal()) {
                ']'.code -> return result
                ','.code, ';'.code -> { hasTrailingSeparator = true; continue }
                else -> throw syntaxError("Unterminated array")
            }
        }
    }

    fun syntaxError(message: String): JSONException = JSONException("$message at character $pos of ${input.take(80)}")
}
