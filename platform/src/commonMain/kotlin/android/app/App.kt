// android.app: one Activity on screen plus a stack of dialogs, all drawn by the renderer.
package android.app

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import compat.j.Fun
import compat.j.Runnable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** What the renderer watches: the activity's content view and the open dialogs, bottom to top. */
object Screen {
    val dialogs = ArrayList<Dialog>()
    var activity: Activity? = null
    var observer: (() -> Unit)? = null
    /** Installed by the host: replaces the activity with a fresh one that receives the old one's saved state. */
    var recreate: (() -> Unit)? = null
    fun changed() { observer?.invoke() }
    /** Handles the system "back" action: the top dialog first, then the activity. */
    fun back(): Boolean {
        val top = dialogs.lastOrNull()
        if (top != null) { if (top.cancelable) top.cancel(); return true }
        val current = activity ?: return false
        current.dispatchBack()
        return true
    }
}

open class Activity : Context() {
    val window: Window = Window()
    var intent: Intent? = Intent()
    var isDestroyed: Boolean = false
        private set
    var isFinishing: Boolean = false
        private set
    val isChangingConfigurations: Boolean get() = false
    var title: CharSequence? = null
    @JvmField var contentView: View? = null
    private var backCallbacks = ArrayList<android.window.OnBackInvokedCallback>()
    val onBackInvokedDispatcher: android.window.OnBackInvokedDispatcher = object : android.window.OnBackInvokedDispatcher {
        override fun registerOnBackInvokedCallback(priority: Int, callback: android.window.OnBackInvokedCallback?) { if (callback != null) backCallbacks.add(callback) }
        override fun unregisterOnBackInvokedCallback(callback: android.window.OnBackInvokedCallback?) { backCallbacks.remove(callback) }
    }
    internal fun dispatchBack() { val callback = backCallbacks.lastOrNull(); if (callback != null) callback.onBackInvoked() else onBackPressed() }

    protected open fun onCreate(savedInstanceState: Bundle?) {}
    protected open fun onStart() {}
    protected open fun onResume() {}
    protected open fun onPause() {}
    protected open fun onStop() {}
    protected open fun onDestroy() { isDestroyed = true }
    protected open fun onNewIntent(intent: Intent?) {}
    protected open fun onSaveInstanceState(outState: Bundle?) {}
    protected open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
    open fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>?, grantResults: IntArray?) {}
    open fun onBackPressed() {}
    open fun onRetainNonConfigurationInstance(): Any? = null
    var lastNonConfigurationInstance: Any? = null
    open fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {}

    fun setTheme(resource: Int) {}
    @Fun fun setContentView(view: View?) { contentView = view; window.content = view; Screen.changed() }
    fun setContentView(view: View?, params: ViewGroup.LayoutParams?) { view?.layoutParams = params; setContentView(view) }
    fun <T : View> findViewById(id: Int): T? = contentView?.findViewById(id)
    val currentFocus: View? get() = null
    fun finish() { isFinishing = true }
    fun recreate() { Screen.recreate?.invoke() }
    fun runOnUiThread(action: Runnable?) { if (action != null) android.os.Handler.main.post(action) }
    fun startActivity(intent: Intent?) {
        val link = intent?.data?.toString()
        if (intent?.action == Intent.ACTION_VIEW && link != null && compat.android.platformOpenUrl(link)) return
        throw android.content.ActivityNotFoundException("No handler")
    }
    /**
     * File pickers only: "create document" hands back a Uri whose output stream opens the system
     * save sheet when closed; "open document" lets the user pick a file and hands back its bytes.
     */
    fun startActivityForResult(intent: Intent?, requestCode: Int) {
        when (intent?.action) {
            Intent.ACTION_CREATE_DOCUMENT -> {
                val uri = android.net.Uri(intent.getStringExtra(Intent.EXTRA_TITLE) ?: "file", intent.type ?: "application/octet-stream", null)
                android.os.Handler.main.post { onActivityResult(requestCode, RESULT_OK, Intent().setData(uri)) }
            }
            Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT -> {
                val types = intent.extraMimeTypes() ?: listOf(intent.type ?: "*/*")
                compat.android.platformImportFile(types) { bytes ->
                    android.os.Handler.main.post {
                        if (bytes == null) onActivityResult(requestCode, RESULT_CANCELED, null)
                        else onActivityResult(requestCode, RESULT_OK, Intent().setData(android.net.Uri("import", intent.type ?: "", bytes)))
                    }
                }
            }
            else -> throw android.content.ActivityNotFoundException("No handler")
        }
    }
    fun checkSelfPermission(permission: String?): Int = android.content.pm.PackageManager.PERMISSION_GRANTED
    fun requestPermissions(permissions: Array<String>?, requestCode: Int) {}
    fun getSystemService(name: String?): Any? = null
    fun overridePendingTransition(enter: Int, exit: Int) {}
    fun setRequestedOrientation(orientation: Int) {}
    fun moveTaskToBack(nonRoot: Boolean): Boolean = false

    // Lifecycle entry points for the app's host.
    fun hostCreate(state: Bundle?) { Screen.activity = this; onCreate(state) }
    fun hostStart() { onStart(); onResume() }
    fun hostStop() { onPause(); onStop() }
    fun hostDestroy() { onPause(); onStop(); onDestroy(); if (Screen.activity === this) Screen.activity = null }
    fun hostNewIntent(intent: Intent?) = onNewIntent(intent)
    fun hostSaveState(): Bundle = Bundle().also { onSaveInstanceState(it) }

    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
        const val RESULT_FIRST_USER = 1
    }
}

open class Dialog(val context: Context?) : DialogInterface {
    constructor(context: Context?, theme: Int) : this(context)
    val window: Window? = Window()
    var isShowing: Boolean = false
        private set
    @JvmField var cancelable: Boolean = true
    @JvmField var cancelOnOutside: Boolean = true
    private var dismissListener: DialogInterface.OnDismissListener? = null
    private var cancelListener: DialogInterface.OnCancelListener? = null
    private var showListener: DialogInterface.OnShowListener? = null
    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) { dismissListener = listener }
    fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) { cancelListener = listener }
    fun setOnShowListener(listener: DialogInterface.OnShowListener?) { showListener = listener }
    fun setCancelable(value: Boolean) { cancelable = value; if (!value) cancelOnOutside = false }
    fun setCanceledOnTouchOutside(value: Boolean) { cancelOnOutside = value; if (value) cancelable = true }
    fun requestWindowFeature(feature: Int): Boolean = true
    @Fun open fun setContentView(view: View?) { window?.content = view; Screen.changed() }
    open fun setTitle(title: CharSequence?) {}
    fun <T : View> findViewById(id: Int): T? = window?.content?.findViewById(id)
    val currentFocus: View? get() = null
    protected open fun onCreate(savedInstanceState: Bundle?) {}
    private var created = false
    open fun show() {
        if (isShowing) return
        if (!created) { created = true; onCreate(null) }
        isShowing = true
        Screen.dialogs.add(this)
        Screen.changed()
        showListener?.onShow(this)
    }
    override fun dismiss() {
        if (!isShowing) return
        isShowing = false
        Screen.dialogs.remove(this)
        Screen.changed()
        dismissListener?.onDismiss(this)
    }
    override fun cancel() {
        if (!isShowing) return
        cancelListener?.onCancel(this)
        dismiss()
    }
    fun hide() = dismiss()
}

/** Title, message, optional custom view or item list, and up to three buttons, built from ordinary views. */
open class AlertDialog protected constructor(context: Context?) : Dialog(context) {
    private val density = (context ?: Context.app).resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()
    private val root = LinearLayout(context).also { it.orientation = LinearLayout.VERTICAL; it.setPadding(0, dp(20), 0, dp(8)) }
    private val titleView = TextView(context).also { it.textSizeSp = 20f; it.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); it.setPadding(dp(24), 0, dp(24), dp(12)); it.visibility = View.GONE }
    private val messageView = TextView(context).also { it.textSizeSp = 15f; it.setPadding(dp(24), 0, dp(24), dp(12)); it.visibility = View.GONE }
    private val customHost = FrameLayout(context)
    private val buttonRow = LinearLayout(context).also { it.orientation = LinearLayout.HORIZONTAL; it.gravity = Gravity.END or Gravity.CENTER_VERTICAL; it.setPadding(dp(12), dp(4), dp(12), 0) }
    private val buttons = HashMap<Int, Button>()
    private var titleSlot = FrameLayout(context)
    /** Ink for the default title, message and item text; the theme sets it. */
    @JvmField var inkColor: Int = defaultInk
    @JvmField var mutedColor: Int = defaultMuted

    init {
        titleSlot.addView(titleView, FrameLayout.LayoutParams(-1, -2))
        root.addView(titleSlot, LinearLayout.LayoutParams(-1, -2))
        root.addView(messageView, LinearLayout.LayoutParams(-1, -2))
        root.addView(customHost, LinearLayout.LayoutParams(-1, 0, 1f).also { it.height = -2 })
        root.addView(buttonRow, LinearLayout.LayoutParams(-1, -2))
        titleView.setTextColor(inkColor); messageView.setTextColor(mutedColor)
        window?.content = root
        window?.backgroundDrawable = android.graphics.drawable.GradientDrawable().also { it.setColor(defaultSurface); it.cornerRadius = 28 * density }
    }

    override fun setTitle(title: CharSequence?) { titleView.setText(title); titleView.visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE }
    fun setCustomTitle(view: View?) { titleSlot.removeAllViews(); if (view != null) titleSlot.addView(view, FrameLayout.LayoutParams(-1, -2)) }
    fun setMessage(message: CharSequence?) { messageView.setText(message); messageView.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE }
    fun setView(view: View?) { customHost.removeAllViews(); if (view != null) customHost.addView(view, FrameLayout.LayoutParams(-1, -2)) }
    fun setButton(which: Int, text: CharSequence?, listener: DialogInterface.OnClickListener?) {
        buttons.remove(which)?.let { buttonRow.removeView(it) }
        if (text == null) return
        val button = Button(context)
        button.setText(text); button.textSizeSp = 14f
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.setTextColor(defaultAccent)
        button.setPadding(dp(12), dp(10), dp(12), dp(10)); button.minimumHeight = dp(48); button.minimumWidth = dp(64)
        button.setOnClickListener { listener?.onClick(this, which); dismiss() }
        buttons[which] = button
        // Android order: neutral at the start, then negative, then positive at the end.
        val order = listOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_POSITIVE)
        buttonRow.removeAllViews()
        for (key in order) buttons[key]?.let { buttonRow.addView(it, LinearLayout.LayoutParams(-2, -2)) }
    }
    fun getButton(which: Int): Button? = buttons[which]
    fun setItems(items: Array<out CharSequence?>, listener: DialogInterface.OnClickListener?) {
        val list = LinearLayout(context).also { it.orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { index, label ->
            val row = TextView(context)
            row.setText(label); row.textSizeSp = 16f; row.setTextColor(inkColor)
            row.setPadding(dp(24), dp(12), dp(24), dp(12)); row.minimumHeight = dp(48); row.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            row.setOnClickListener { listener?.onClick(this, index); dismiss() }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        setView(ScrollView(context).also { it.addView(list) })
    }

    class Builder(val context: Context?) {
        constructor(context: Context?, theme: Int) : this(context)
        private val steps = ArrayList<(AlertDialog) -> Unit>()
        private var cancelable = true
        fun setTitle(title: CharSequence?): Builder { steps.add { it.setTitle(title) }; return this }
        fun setTitle(resource: Int): Builder = setTitle((context ?: Context.app).getString(resource))
        fun setCustomTitle(view: View?): Builder { steps.add { it.setCustomTitle(view) }; return this }
        fun setMessage(message: CharSequence?): Builder { steps.add { it.setMessage(message) }; return this }
        fun setMessage(resource: Int): Builder = setMessage((context ?: Context.app).getString(resource))
        fun setView(view: View?): Builder { steps.add { it.setView(view) }; return this }
        fun setIcon(resource: Int): Builder = this
        fun setCancelable(value: Boolean): Builder { cancelable = value; return this }
        fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder { steps.add { it.setButton(DialogInterface.BUTTON_POSITIVE, text, listener) }; return this }
        fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder { steps.add { it.setButton(DialogInterface.BUTTON_NEGATIVE, text, listener) }; return this }
        fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder { steps.add { it.setButton(DialogInterface.BUTTON_NEUTRAL, text, listener) }; return this }
        fun setPositiveButton(resource: Int, listener: DialogInterface.OnClickListener?): Builder = setPositiveButton(label(resource), listener)
        fun setNegativeButton(resource: Int, listener: DialogInterface.OnClickListener?): Builder = setNegativeButton(label(resource), listener)
        fun setItems(items: Array<out CharSequence?>, listener: DialogInterface.OnClickListener?): Builder { steps.add { it.setItems(items, listener) }; return this }
        fun setOnDismissListener(listener: DialogInterface.OnDismissListener?): Builder { steps.add { it.setOnDismissListener(listener) }; return this }
        fun setOnCancelListener(listener: DialogInterface.OnCancelListener?): Builder { steps.add { it.setOnCancelListener(listener) }; return this }
        private fun label(resource: Int): String = when (resource) { android.R.string.ok -> "OK"; android.R.string.cancel -> "Cancel"; else -> (context ?: Context.app).getString(resource) }
        fun create(): AlertDialog { val dialog = AlertDialog(context); dialog.setCancelable(cancelable); steps.forEach { it(dialog) }; return dialog }
        fun show(): AlertDialog = create().also { it.show() }
    }

    companion object {
        const val BUTTON_POSITIVE = -1
        const val BUTTON_NEGATIVE = -2
        const val BUTTON_NEUTRAL = -3
        /** Default colours for dialogs the app does not style itself; the app's theme sets them. */
        var defaultSurface: Int = -0x1
        var defaultInk: Int = -0xe0e0e1
        var defaultMuted: Int = -0xaaaaab
        var defaultAccent: Int = -0xdaa22e
    }
}

/** A date chooser; the renderer shows the platform's calendar and reports the picked day. */
class DatePickerDialog(context: Context?, private val listener: OnDateSetListener?, @JvmField var year: Int, @JvmField var month: Int, @JvmField var dayOfMonth: Int) : Dialog(context) {
    constructor(context: Context?, theme: Int, listener: OnDateSetListener?, year: Int, month: Int, dayOfMonth: Int) : this(context, listener, year, month, dayOfMonth)
    fun interface OnDateSetListener { fun onDateSet(view: android.widget.DatePicker?, year: Int, month: Int, dayOfMonth: Int) }
    val datePicker = android.widget.DatePicker(context)
    /** Called by the renderer with the chosen date (month is zero-based, as on Android). */
    fun rendererPicked(year: Int, month: Int, dayOfMonth: Int) {
        this.year = year; this.month = month; this.dayOfMonth = dayOfMonth
        listener?.onDateSet(datePicker, year, month, dayOfMonth)
        dismiss()
    }
    fun updateDate(year: Int, month: Int, dayOfMonth: Int) { this.year = year; this.month = month; this.dayOfMonth = dayOfMonth; Screen.changed() }
}
