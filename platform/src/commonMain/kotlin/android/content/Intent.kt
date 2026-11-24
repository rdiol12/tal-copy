package android.content

import android.net.Uri
import android.os.Bundle
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Only the pieces the app uses: an action, a MIME type, a data Uri and string extras. */
class Intent() {
    @JvmField internal var actionValue: String? = null
    @JvmField internal var typeValue: String? = null
    @JvmField internal var dataValue: Uri? = null
    @JvmField internal var flagBits: Int = 0
    val action: String? get() = actionValue
    val type: String? get() = typeValue
    val data: Uri? get() = dataValue
    val flags: Int get() = flagBits
    val categories = HashSet<String>()
    private val extraValues = HashMap<String, Any?>()
    constructor(action: String?) : this() { actionValue = action }
    constructor(action: String?, uri: Uri?) : this() { actionValue = action; dataValue = uri }
    constructor(context: Context?, target: Any?) : this()
    constructor(source: Intent?) : this() { if (source != null) { actionValue = source.actionValue; typeValue = source.typeValue; dataValue = source.dataValue; extraValues.putAll(source.extraValues) } }
    fun setAction(value: String?): Intent { actionValue = value; return this }
    fun setType(value: String?): Intent { typeValue = value; return this }
    fun setData(value: Uri?): Intent { dataValue = value; return this }
    fun setDataAndType(value: Uri?, mime: String?): Intent { dataValue = value; typeValue = mime; return this }
    fun addCategory(category: String?): Intent { if (category != null) categories.add(category); return this }
    fun addFlags(value: Int): Intent { flagBits = flagBits or value; return this }
    fun setFlags(value: Int): Intent { flagBits = value; return this }
    fun setPackage(name: String?): Intent = this
    fun putExtra(name: String, value: String?): Intent { extraValues[name] = value; return this }
    fun putExtra(name: String, value: Boolean): Intent { extraValues[name] = value; return this }
    fun putExtra(name: String, value: Int): Intent { extraValues[name] = value; return this }
    fun putExtra(name: String, value: Long): Intent { extraValues[name] = value; return this }
    fun putExtra(name: String, value: Uri?): Intent { extraValues[name] = value; return this }
    fun putExtra(name: String, value: Array<String>?): Intent { extraValues[name] = value; return this }
    fun hasExtra(name: String?): Boolean = extraValues.containsKey(name)
    fun removeExtra(name: String?) { extraValues.remove(name) }
    fun getStringExtra(name: String?): String? = extraValues[name] as? String
    fun getBooleanExtra(name: String?, default: Boolean): Boolean = extraValues[name] as? Boolean ?: default
    fun getIntExtra(name: String?, default: Int): Int = extraValues[name] as? Int ?: default
    fun getLongExtra(name: String?, default: Long): Long = extraValues[name] as? Long ?: default
    val extras: Bundle? get() = null
    internal fun extraMimeTypes(): List<String>? { @Suppress("UNCHECKED_CAST") return (extraValues[EXTRA_MIME_TYPES] as? Array<String>)?.toList() }

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val EXTRA_TITLE = "android.intent.extra.TITLE"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 64
        const val FLAG_ACTIVITY_NEW_TASK = 268435456
        const val FLAG_ACTIVITY_CLEAR_TOP = 67108864
        const val FLAG_ACTIVITY_SINGLE_TOP = 536870912
        @JvmStatic fun createChooser(target: Intent?, title: CharSequence?): Intent = target ?: Intent()
    }
}

class ActivityNotFoundException(message: String? = null) : RuntimeException(message)

class IntentSender {
    class SendIntentException(message: String? = null) : Exception(message)
}

class ClipData private constructor(val label: CharSequence?, val text: CharSequence?) {
    companion object { @JvmStatic fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(label, text) }
}

interface DialogInterface {
    fun interface OnClickListener { fun onClick(dialog: DialogInterface?, which: Int) }
    fun interface OnDismissListener { fun onDismiss(dialog: DialogInterface?) }
    fun interface OnCancelListener { fun onCancel(dialog: DialogInterface?) }
    fun interface OnShowListener { fun onShow(dialog: DialogInterface?) }
    fun interface OnMultiChoiceClickListener { fun onClick(dialog: DialogInterface?, which: Int, isChecked: Boolean) }
    fun cancel()
    fun dismiss()
    companion object {
        const val BUTTON_POSITIVE = -1
        const val BUTTON_NEGATIVE = -2
        const val BUTTON_NEUTRAL = -3
    }
}
