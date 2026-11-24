package android.text

import kotlin.jvm.JvmStatic

interface Editable : CharSequence {
    fun replace(start: Int, end: Int, text: CharSequence?): Editable
    fun insert(where: Int, text: CharSequence?): Editable = replace(where, where, text)
    fun delete(start: Int, end: Int): Editable = replace(start, end, "")
    fun append(text: CharSequence?): Editable = replace(length, length, text)
    fun clear() { replace(0, length, "") }
}

interface TextWatcher {
    fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int)
    fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)
    fun afterTextChanged(s: Editable?)
}

interface Spanned : CharSequence
interface InputFilter {
    class LengthFilter(val max: Int) : InputFilter
    class AllCaps : InputFilter
}

object InputType {
    const val TYPE_NULL = 0
    const val TYPE_CLASS_TEXT = 1
    const val TYPE_CLASS_NUMBER = 2
    const val TYPE_CLASS_PHONE = 3
    const val TYPE_CLASS_DATETIME = 4
    const val TYPE_MASK_CLASS = 15
    const val TYPE_NUMBER_FLAG_SIGNED = 4096
    const val TYPE_NUMBER_FLAG_DECIMAL = 8192
    const val TYPE_NUMBER_VARIATION_PASSWORD = 16
    const val TYPE_TEXT_FLAG_CAP_CHARACTERS = 4096
    const val TYPE_TEXT_FLAG_CAP_WORDS = 8192
    const val TYPE_TEXT_FLAG_CAP_SENTENCES = 16384
    const val TYPE_TEXT_FLAG_AUTO_CORRECT = 32768
    const val TYPE_TEXT_FLAG_MULTI_LINE = 131072
    const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 524288
    const val TYPE_TEXT_VARIATION_URI = 16
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 32
    const val TYPE_TEXT_VARIATION_PASSWORD = 128
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 144
}

object TextUtils {
    enum class TruncateAt { START, MIDDLE, END, MARQUEE }
    @JvmStatic fun isEmpty(text: CharSequence?): Boolean = text == null || text.isEmpty()
    @JvmStatic fun equals(a: CharSequence?, b: CharSequence?): Boolean = a?.toString() == b?.toString()
    @JvmStatic fun join(delimiter: CharSequence, items: Iterable<*>): String = items.joinToString(delimiter)
    @JvmStatic fun join(delimiter: CharSequence, items: Array<out Any?>): String = items.joinToString(delimiter)
}
