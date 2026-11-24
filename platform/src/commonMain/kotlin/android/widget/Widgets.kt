// android.widget: the model objects behind the app's screens. Each widget only stores what the app sets;
// compat.android.compose draws it and writes user input back through the renderer* entry points.
package android.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ResourceDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import compat.j.Fun
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

open class FrameLayout(context: Context?) : ViewGroup(context) {
    open class LayoutParams(width: Int, height: Int) : MarginLayoutParams(width, height) {
        @JvmField var gravity: Int = -1
        constructor(width: Int, height: Int, gravity: Int) : this(width, height) { this.gravity = gravity }
        constructor(source: ViewGroup.LayoutParams?) : this(source?.width ?: WRAP_CONTENT, source?.height ?: WRAP_CONTENT)
    }
    var measureAllChildren: Boolean = false
}

open class LinearLayout(context: Context?) : ViewGroup(context) {
    open class LayoutParams(width: Int, height: Int) : MarginLayoutParams(width, height) {
        @JvmField var weight: Float = 0f
        @JvmField var gravity: Int = -1
        constructor(width: Int, height: Int, weight: Float) : this(width, height) { this.weight = weight }
        constructor(source: ViewGroup.LayoutParams?) : this(source?.width ?: WRAP_CONTENT, source?.height ?: WRAP_CONTENT)
    }
    var orientation: Int = HORIZONTAL
        set(value) { field = value; changed() }
    var gravity: Int = Gravity.START or Gravity.TOP
        set(value) { field = value; changed() }
    fun setHorizontalGravity(value: Int) { gravity = (gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK.inv()) or value }
    fun setVerticalGravity(value: Int) { gravity = (gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or value }
    var weightSum: Float = 0f
    var isBaselineAligned: Boolean = true
    var showDividers: Int = 0
    var dividerDrawable: Drawable? = null
    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
    }
}

open class ScrollView(context: Context?) : FrameLayout(context) {
    var isFillViewport: Boolean = false
        set(value) { field = value; changed() }
    var isSmoothScrollingEnabled: Boolean = true
    /** Scroll requests for the renderer: a target in pixels and a counter that changes with each request. */
    @JvmField var scrollTarget: Int = 0
    @JvmField var scrollRequest: Int = 0
    @JvmField var scrollAnimated: Boolean = false
    /** Furthest scroll offset, written by the renderer. */
    @JvmField var maxScroll: Int = 0
    override fun scrollTo(x: Int, y: Int) { scrollTarget = y; scrollAnimated = false; scrollRequest++; scrollY = y.coerceIn(0, maxOf(maxScroll, 0)); changed() }
    override fun scrollBy(x: Int, y: Int) = scrollTo(0, scrollY + y)
    fun smoothScrollTo(x: Int, y: Int) { scrollTarget = y; scrollAnimated = true; scrollRequest++; changed() }
    fun smoothScrollBy(x: Int, y: Int) = smoothScrollTo(0, scrollY + y)
    fun fullScroll(direction: Int): Boolean { smoothScrollTo(0, if (direction == FOCUS_UP) 0 else Int.MAX_VALUE / 2); return true }
    override fun canScrollVertically(direction: Int): Boolean = if (direction < 0) scrollY > 0 else scrollY < maxScroll
}

open class HorizontalScrollView(context: Context?) : FrameLayout(context) {
    var isFillViewport: Boolean = false
    @JvmField var scrollTarget: Int = 0
    @JvmField var scrollRequest: Int = 0
    override fun scrollTo(x: Int, y: Int) { scrollTarget = x; scrollRequest++; changed() }
    fun smoothScrollTo(x: Int, y: Int) = scrollTo(x, y)
}

open class Space(context: Context?) : View(context)

open class TextView(context: Context?) : View(context) {
    enum class BufferType { NORMAL, SPANNABLE, EDITABLE }
    fun interface OnEditorActionListener { fun onEditorAction(v: TextView?, actionId: Int, event: Any?): Boolean }

    @JvmField internal var content: String = ""
    protected val watchers = ArrayList<TextWatcher>()
    /** The current text. Java reads it as a CharSequence (or Editable for text fields). */
    @Fun open fun getText(): CharSequence = content
    @Fun open fun setText(value: CharSequence?) { replaceText(value?.toString() ?: "", true) }
    fun setText(value: CharSequence?, type: BufferType?) = setText(value)
    @Fun fun setText(resource: Int) = setText(context.getString(resource))
    fun append(value: CharSequence?) = setText(content + (value ?: ""))
    fun length(): Int = content.length
    protected fun replaceText(next: String, notify: Boolean) {
        if (next == content) return
        val previous = content
        if (notify) watchers.toList().forEach { it.beforeTextChanged(previous, 0, previous.length, next.length) }
        content = next
        if (notify) {
            watchers.toList().forEach { it.onTextChanged(next, 0, previous.length, next.length) }
            val editable = editableView()
            watchers.toList().forEach { it.afterTextChanged(editable) }
        }
        changed(); parent?.changed()
    }
    protected open fun editableView(): Editable? = null
    fun addTextChangedListener(watcher: TextWatcher?) { if (watcher != null) watchers.add(watcher) }
    fun removeTextChangedListener(watcher: TextWatcher?) { watchers.remove(watcher) }
    /** Called by the renderer when the user edits the field. */
    fun rendererTyped(next: String) = replaceText(next, true)

    var hint: CharSequence? = null
        set(value) { field = value; changed() }
    @Fun fun setHint(resource: Int) { hint = context.getString(resource) }
    /** Text size in scaled pixels (sp); [setTextSize] with a unit converts to it. */
    @JvmField var textSizeSp: Float = 14f
    @Fun fun getTextSize(): Float = textSizeSp * context.resources.displayMetrics.scaledDensity
    @Fun fun setTextSize(size: Float) { textSizeSp = size; changed() }
    fun setTextSize(unit: Int, size: Float) {
        val metrics = context.resources.displayMetrics
        textSizeSp = when (unit) { 0 -> size / metrics.scaledDensity; 1 -> size * metrics.density / metrics.scaledDensity; else -> size }
        changed()
    }
    @JvmField var textColor: Int = -0x1000000
    @JvmField var textColorSet: Boolean = false
    fun setTextColor(color: Int) { textColor = color; textColorSet = true; changed() }
    fun setTextColor(colors: ColorStateList?) { if (colors != null) setTextColor(colors.defaultColor) }
    val currentTextColor: Int get() = textColor
    @JvmField var hintColor: Int = 0
    fun setHintTextColor(color: Int) { hintColor = color; changed() }
    @JvmField var linkColor: Int = 0
    fun setLinkTextColor(color: Int) { linkColor = color }
    var typeface: Typeface? = null
        set(value) { field = value; changed() }
    fun setTypeface(value: Typeface?, style: Int) { typeface = Typeface.create(value, style) }
    var gravity: Int = Gravity.START or Gravity.TOP
        set(value) { field = value; changed() }
    var maxLines: Int = Int.MAX_VALUE
        set(value) { field = value; changed() }
    var minLines: Int = 0
        set(value) { field = value; changed() }
    fun setLines(lines: Int) { minLines = lines; maxLines = lines }
    @JvmField var singleLine: Boolean = false
    fun setSingleLine(value: Boolean) { singleLine = value; if (value) maxLines = 1; changed() }
    fun setSingleLine() = setSingleLine(true)
    var ellipsize: TextUtils.TruncateAt? = null
        set(value) { field = value; changed() }
    var minHeight: Int
        get() = minimumHeight
        set(value) { minimumHeight = value }
    var minWidth: Int
        get() = minimumWidth
        set(value) { minimumWidth = value }
    var maxWidth: Int = Int.MAX_VALUE
        set(value) { field = value; changed() }
    fun setMinimumWidthCompat(value: Int) { minimumWidth = value }
    var inputType: Int = 0
        set(value) { field = value; changed() }
    var imeOptions: Int = 0
    var imeHintLocales: Any? = null
    var letterSpacing: Float = 0f
        set(value) { field = value; changed() }
    @JvmField var lineSpacingExtra: Float = 0f
    @JvmField var lineSpacingMultiplier: Float = 1f
    fun setLineSpacing(extra: Float, multiplier: Float) { lineSpacingExtra = extra; lineSpacingMultiplier = multiplier; changed() }
    var includeFontPadding: Boolean = true
    var isAllCaps: Boolean = false
        set(value) { field = value; changed() }
    var paintFlags: Int = 0
        set(value) { field = value; changed() }
    var filters: Array<android.text.InputFilter>? = null
    var error: CharSequence? = null
        set(value) { field = value; changed() }
    var isCursorVisible: Boolean = true
    var movementMethod: Any? = null
    var textIsSelectable: Boolean = false
    @JvmField var editorActionListener: OnEditorActionListener? = null
    fun setOnEditorActionListener(listener: OnEditorActionListener?) { editorActionListener = listener }
    @JvmField val compoundDrawables = arrayOfNulls<Drawable>(4)
    @JvmField var compoundPadding: Int = 0
    fun setCompoundDrawablesWithIntrinsicBounds(left: Int, top: Int, right: Int, bottom: Int) {
        val ids = intArrayOf(left, top, right, bottom)
        for (i in 0 until 4) compoundDrawables[i] = if (ids[i] == 0) null else ResourceDrawable(ids[i])
        changed()
    }
    fun setCompoundDrawablesWithIntrinsicBounds(left: Drawable?, top: Drawable?, right: Drawable?, bottom: Drawable?) {
        compoundDrawables[0] = left; compoundDrawables[1] = top; compoundDrawables[2] = right; compoundDrawables[3] = bottom; changed()
    }
    fun setCompoundDrawablesRelativeWithIntrinsicBounds(start: Drawable?, top: Drawable?, end: Drawable?, bottom: Drawable?) = setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom)
    var compoundDrawablePadding: Int
        get() = compoundPadding
        set(value) { compoundPadding = value; changed() }
    val lineCount: Int get() = maxOf(1, content.count { it == '\n' } + 1)
    val lineHeight: Int get() = (getTextSize() * 1.25f).toInt()
    val paint: android.graphics.Paint get() = android.graphics.Paint().also { it.textSize = getTextSize(); it.typeface = typeface }
}

open class Button(context: Context?) : TextView(context) { init { gravity = Gravity.CENTER; isClickable = true } }

open class EditText(context: Context?) : TextView(context) {
    init { isFocusable = true; inputType = android.text.InputType.TYPE_CLASS_TEXT }
    private inner class Content : Editable {
        override val length: Int get() = content.length
        override fun get(index: Int): Char = content[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = content.subSequence(startIndex, endIndex)
        override fun replace(start: Int, end: Int, text: CharSequence?): Editable { replaceText(content.substring(0, start) + (text ?: "") + content.substring(end), true); return this }
        override fun toString(): String = content
        override fun equals(other: Any?): Boolean = other is CharSequence && other.toString() == content
        override fun hashCode(): Int = content.hashCode()
    }
    private val editable = Content()
    override fun getText(): Editable = editable
    override fun editableView(): Editable? = editable
    val editableText: Editable get() = editable
    /** Cursor position; [selectionRequest] changes whenever the app moves it. */
    @JvmField var selectionStartIndex: Int = 0
    @JvmField var selectionEndIndex: Int = 0
    @JvmField var selectionRequest: Int = 0
    val selectionStart: Int get() = selectionStartIndex
    val selectionEnd: Int get() = selectionEndIndex
    fun setSelection(index: Int) = setSelection(index, index)
    fun setSelection(start: Int, stop: Int) { selectionStartIndex = start.coerceIn(0, content.length); selectionEndIndex = stop.coerceIn(0, content.length); selectionRequest++; changed() }
    fun selectAll() = setSelection(0, content.length)
    override fun setText(value: CharSequence?) { super.setText(value); selectionStartIndex = minOf(selectionStartIndex, content.length); selectionEndIndex = minOf(selectionEndIndex, content.length) }
    var isSelectAllOnFocus: Boolean = false
    var showSoftInputOnFocus: Boolean = true
}

interface Filterable

interface Adapter {
    val count: Int
    fun getItem(position: Int): Any?
    fun getItemId(position: Int): Long
    fun getView(position: Int, convertView: View?, parent: ViewGroup?): View?
}
interface ListAdapter : Adapter
interface SpinnerAdapter : Adapter { fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View? }

open class BaseAdapter : ListAdapter, SpinnerAdapter {
    @JvmField internal var onChanged: (() -> Unit)? = null
    override val count: Int get() = 0
    override fun getItem(position: Int): Any? = null
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? = null
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View? = getView(position, convertView, parent)
    open fun notifyDataSetChanged() { onChanged?.invoke() }
    open val isEmpty: Boolean get() = count == 0
}

open class ArrayAdapter<T>(val context: Context?, resource: Int, items: MutableList<T>?) : BaseAdapter(), Filterable {
    constructor(context: Context?, resource: Int) : this(context, resource, ArrayList<T>())
    constructor(context: Context?, resource: Int, items: Array<T>) : this(context, resource, items.toMutableList())
    private val values: MutableList<T> = items ?: ArrayList()
    override val count: Int get() = values.size
    override fun getItem(position: Int): T? = values.getOrNull(position)
    fun getPosition(item: T?): Int = values.indexOf(item)
    fun add(item: T) { values.add(item); notifyDataSetChanged() }
    fun addAll(items: Collection<T>) { values.addAll(items); notifyDataSetChanged() }
    fun remove(item: T) { values.remove(item); notifyDataSetChanged() }
    fun clear() { values.clear(); notifyDataSetChanged() }
    fun setDropDownViewResource(resource: Int) {}
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? =
        TextView(context).also { it.setText(getItem(position)?.toString()); it.textSizeSp = 16f; val pad = (12 * (context ?: Context.app).resources.displayMetrics.density).toInt(); it.setPadding(pad, pad, pad, pad) }
}

abstract class AdapterView<T : Adapter>(context: Context?) : ViewGroup(context) {
    interface OnItemSelectedListener {
        fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
        fun onNothingSelected(parent: AdapterView<*>?)
    }
    fun interface OnItemClickListener { fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) }
    var onItemSelectedListener: OnItemSelectedListener? = null
        set(value) {
            field = value
            // Android reports the initial selection after the first layout; do the same once, asynchronously.
            if (value != null && selectedPosition >= 0) post { if (onItemSelectedListener === value) notifySelected() }
        }
    var onItemClickListener: OnItemClickListener? = null
    @JvmField protected var selectedPosition: Int = -1
    abstract val adapterOrNull: T?
    val selectedItemPosition: Int get() = selectedPosition
    val selectedItem: Any? get() = if (selectedPosition >= 0) adapterOrNull?.getItem(selectedPosition) else null
    val selectedItemId: Long get() = selectedPosition.toLong()
    val count: Int get() = adapterOrNull?.count ?: 0
    fun getItemAtPosition(position: Int): Any? = adapterOrNull?.getItem(position)
    protected fun notifySelected() { onItemSelectedListener?.onItemSelected(this, null, selectedPosition, selectedPosition.toLong()) }
    open fun setSelection(position: Int) {
        if (position == selectedPosition || position < 0 || position >= count) return
        selectedPosition = position; changed(); notifySelected()
    }
    fun setSelection(position: Int, animate: Boolean) = setSelection(position)
    /** Called by the renderer when the user picks a row. */
    fun rendererPicked(position: Int) = setSelection(position)
}

open class Spinner(context: Context?) : AdapterView<SpinnerAdapter>(context) {
    constructor(context: Context?, mode: Int) : this(context)
    var adapter: SpinnerAdapter? = null
        set(value) {
            field = value
            (value as? BaseAdapter)?.onChanged = { if (selectedPosition >= count) selectedPosition = count - 1; changed() }
            selectedPosition = if ((value?.count ?: 0) > 0) 0 else -1
            changed()
        }
    override val adapterOrNull: SpinnerAdapter? get() = adapter
    var popupBackground: Drawable? = null
    fun setPopupBackgroundDrawable(drawable: Drawable?) { popupBackground = drawable }
    var prompt: CharSequence? = null
    var dropDownWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    var dropDownVerticalOffset: Int = 0
    companion object { const val MODE_DIALOG = 0; const val MODE_DROPDOWN = 1 }
}

open class AutoCompleteTextView(context: Context?) : EditText(context) {
    @JvmField var suggestionAdapter: ListAdapter? = null
    @Fun fun <T> setAdapter(adapter: T?) where T : ListAdapter, T : Filterable { suggestionAdapter = adapter; changed() }
    val adapter: ListAdapter? get() = suggestionAdapter
    var threshold: Int = 2
    var dropDownAnchor: Int = NO_ID
    var dropDownWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    var dropDownHeight: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    var dropDownVerticalOffset: Int = 0
    @JvmField var dropDownBackground: Drawable? = null
    fun setDropDownBackgroundDrawable(drawable: Drawable?) { dropDownBackground = drawable }
    /** Whether the whole list was opened on request (true) or follows typing (false). */
    @JvmField var popupOpen: Boolean = false
    @JvmField var popupAll: Boolean = false
    /** Suppresses the suggestion popup for the next programmatic text change. */
    @JvmField var filterNext: Boolean = true
    val isPopupShowing: Boolean get() = popupOpen
    fun showDropDown() { popupOpen = true; popupAll = true; changed() }
    fun dismissDropDown() { if (popupOpen) { popupOpen = false; popupAll = false; changed() } }
    fun setText(value: CharSequence?, filter: Boolean) { filterNext = filter; setText(value); filterNext = true; if (!filter) dismissDropDown() }
    var onItemClickListener: AdapterView.OnItemClickListener? = null
    var onDismissListener: OnDismissListener? = null
    fun interface OnDismissListener { fun onDismiss() }
    fun performCompletion() {}
    /** Called by the renderer: the user typed, so follow the threshold rule. */
    fun rendererTypedSuggest(next: String) { rendererTyped(next); popupAll = false; popupOpen = next.length >= threshold && (suggestionAdapter?.count ?: 0) > 0; changed() }
    /** Called by the renderer when a suggestion row is tapped. */
    fun rendererChose(position: Int, value: String) { setText(value, false); setSelection(value.length); onItemClickListener?.onItemClick(null, null, position, position.toLong()) }
}

open class ImageView(context: Context?) : View(context) {
    enum class ScaleType { CENTER, CENTER_CROP, CENTER_INSIDE, FIT_CENTER, FIT_XY, FIT_START, FIT_END, MATRIX }
    @JvmField var imageResource: Int = 0
    @JvmField var imageDrawable: Drawable? = null
    @JvmField var tint: Int? = null
    fun setImageResource(resource: Int) { imageResource = resource; imageDrawable = null; changed() }
    fun setImageDrawable(drawable: Drawable?) { imageDrawable = drawable; imageResource = (drawable as? ResourceDrawable)?.id ?: 0; changed() }
    val drawable: Drawable? get() = imageDrawable ?: if (imageResource != 0) ResourceDrawable(imageResource) else null
    fun setColorFilter(color: Int) { tint = color; changed() }
    fun setColorFilter(color: Int, mode: Any?) = setColorFilter(color)
    fun clearColorFilter() { tint = null; changed() }
    var imageTintList: ColorStateList? = null
        set(value) { field = value; tint = value?.defaultColor; changed() }
    var scaleType: ScaleType? = ScaleType.FIT_CENTER
        set(value) { field = value; changed() }
    var adjustViewBounds: Boolean = false
    var imageAlpha: Int = 255
        set(value) { field = value; changed() }
}

open class ImageButton(context: Context?) : ImageView(context) { init { isClickable = true } }

open class CompoundButton(context: Context?) : Button(context) {
    fun interface OnCheckedChangeListener { fun onCheckedChanged(button: CompoundButton?, isChecked: Boolean) }
    @JvmField internal var checkedListener: OnCheckedChangeListener? = null
    @JvmField internal var groupListener: ((CompoundButton, Boolean) -> Unit)? = null
    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) { checkedListener = listener }
    var buttonTintList: ColorStateList? = null
        set(value) { field = value; changed() }
    open var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value; changed()
            checkedListener?.onCheckedChanged(this, value)
            groupListener?.invoke(this, value)
        }
    open fun toggle() { isChecked = !isChecked }
    override fun performClick(): Boolean { toggle(); super.performClick(); return true }
    init { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
}

open class CheckBox(context: Context?) : CompoundButton(context)
open class Switch(context: Context?) : CompoundButton(context)
open class RadioButton(context: Context?) : CompoundButton(context) { override fun toggle() { if (!isChecked) isChecked = true } }

open class RadioGroup(context: Context?) : LinearLayout(context) {
    fun interface OnCheckedChangeListener { fun onCheckedChanged(group: RadioGroup?, checkedId: Int) }
    private var listener: OnCheckedChangeListener? = null
    fun setOnCheckedChangeListener(value: OnCheckedChangeListener?) { listener = value }
    var checkedRadioButtonId: Int = -1
        private set
    init { orientation = VERTICAL }
    override fun addView(child: View?, index: Int) {
        if (child is RadioButton) {
            if (child.id == NO_ID) child.id = generateViewId()
            child.groupListener = { button, checked -> if (checked) select(button.id) }
            if (child.isChecked) checkedRadioButtonId = child.id
        }
        super.addView(child, index)
    }
    private fun select(id: Int) {
        if (checkedRadioButtonId == id) return
        checkedRadioButtonId = id
        for (child in children) if (child is RadioButton && child.id != id) child.isChecked = false
        listener?.onCheckedChanged(this, id)
    }
    fun check(id: Int) { if (id == -1) clearCheck() else findViewById<RadioButton>(id)?.isChecked = true }
    fun clearCheck() { checkedRadioButtonId = -1; for (child in children) if (child is RadioButton) child.isChecked = false; listener?.onCheckedChanged(this, -1) }
}

open class ProgressBar(context: Context?) : View(context) {
    constructor(context: Context?, attrs: Any?, defStyleAttr: Int) : this(context) { isIndeterminate = defStyleAttr != android.R.attr.progressBarStyleHorizontal }
    var max: Int = 100
        set(value) { field = value; changed() }
    var progress: Int = 0
        set(value) { field = value; changed() }
    var isIndeterminate: Boolean = true
        set(value) { field = value; changed() }
    var progressTintList: ColorStateList? = null
        set(value) { field = value; changed() }
    var progressBackgroundTintList: ColorStateList? = null
        set(value) { field = value; changed() }
    var indeterminateTintList: ColorStateList? = null
        set(value) { field = value; changed() }
    fun setProgress(value: Int, animate: Boolean) { progress = value }
}

open class DatePicker(context: Context?) : FrameLayout(context) {
    var year: Int = 2000
    var month: Int = 0
    var dayOfMonth: Int = 1
}

/** Short message shown at the bottom of the screen by the renderer. */
class Toast private constructor(val text: String, val duration: Int) {
    fun show() { presenter?.invoke(text, duration == LENGTH_LONG) }
    fun cancel() {}
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        var presenter: ((text: String, long: Boolean) -> Unit)? = null
        @JvmStatic fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast = Toast(text?.toString() ?: "", duration)
        @JvmStatic fun makeText(context: Context?, resource: Int, duration: Int): Toast = Toast((context ?: Context.app).getString(resource), duration)
    }
}
