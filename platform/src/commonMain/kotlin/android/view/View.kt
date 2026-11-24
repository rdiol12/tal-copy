// A small model of android.view: views are plain objects holding the
// properties the app sets; compat.android.compose renders the tree with
// Compose and reports sizes and input back. Only what PocketFlow uses exists.
package android.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

open class View(context: Context?) {
    fun interface OnClickListener { fun onClick(v: View?) }
    fun interface OnLongClickListener { fun onLongClick(v: View?): Boolean }
    fun interface OnTouchListener { fun onTouch(v: View?, event: MotionEvent?): Boolean }
    fun interface OnFocusChangeListener { fun onFocusChange(v: View?, hasFocus: Boolean) }
    fun interface OnLayoutChangeListener {
        fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int)
    }
    fun interface OnDragListener { fun onDrag(v: View?, event: DragEvent?): Boolean }

    val context: Context = context ?: Context.app

    @JvmField internal var observer: (() -> Unit)? = null
    /** Bumped on every change so the renderer can cheaply tell whether to redraw. */
    var version: Int = 0
        private set
    internal fun changed() { version++; observer?.invoke() }
    /** Alpha, translation, scale and rotation only need the view's layer redrawn, not its content rebuilt. */
    var rendererLayerObserver: (() -> Unit)? = null
    internal fun transformed() { rendererLayerObserver?.invoke() }

    var parent: ViewGroup? = null
        internal set
    var layoutParams: ViewGroup.LayoutParams? = null
        set(value) { field = value; changed(); parent?.changed() }
    var id: Int = NO_ID
    var tag: Any? = null
    private var tags: HashMap<Int, Any?>? = null
    fun setTag(key: Int, value: Any?) { (tags ?: HashMap<Int, Any?>().also { tags = it })[key] = value }
    fun getTag(key: Int): Any? = tags?.get(key)

    var visibility: Int = VISIBLE
        set(value) { if (field != value) { field = value; changed(); parent?.changed() } }
    var alpha: Float = 1f
        set(value) { field = value; transformed() }
    var isEnabled: Boolean = true
        set(value) { field = value; changed() }
    var isClickable: Boolean = false
        set(value) { field = value; changed() }
    var isLongClickable: Boolean = false
    var isFocusable: Boolean = false
    var isFocusableInTouchMode: Boolean = false
    var isSelected: Boolean = false
        set(value) { field = value; changed() }
    var isActivated: Boolean = false
        set(value) { field = value; changed() }
    var contentDescription: CharSequence? = null
    var elevation: Float = 0f
        set(value) { field = value; changed() }
    var translationX: Float = 0f
        set(value) { field = value; transformed() }
    var translationY: Float = 0f
        set(value) { field = value; transformed() }
    var translationZ: Float = 0f
    var scaleX: Float = 1f
        set(value) { field = value; transformed() }
    var scaleY: Float = 1f
        set(value) { field = value; transformed() }
    var rotation: Float = 0f
        set(value) { field = value; transformed() }
    var pivotX: Float = 0f
    var pivotY: Float = 0f
    var minimumWidth: Int = 0
        set(value) { field = value; changed() }
    var minimumHeight: Int = 0
        set(value) { field = value; changed() }
    var background: Drawable? = null
        set(value) { field = value; changed() }
    var foreground: Drawable? = null
    var clipToOutline: Boolean = false
        set(value) { field = value; changed() }
    var layoutDirection: Int = LAYOUT_DIRECTION_INHERIT
        set(value) { field = value; changed() }
    var textDirection: Int = 0
    var textAlignment: Int = 0
        set(value) { field = value; changed() }
    var importantForAccessibility: Int = 0
    var overScrollMode: Int = 0
    var isVerticalScrollBarEnabled: Boolean = true
    var isHorizontalScrollBarEnabled: Boolean = true
    var isSaveEnabled: Boolean = true
    var isHapticFeedbackEnabled: Boolean = true
    var stateListAnimator: Any? = null
    var outlineProvider: Any? = null

    fun setBackgroundColor(color: Int) { background = ColorDrawable(color) }
    fun setBackgroundResource(id: Int) {}
    fun setBackgroundDrawableResource(id: Int) {}
    fun setBackgroundDrawable(drawable: Drawable?) { background = drawable }

    var paddingLeft: Int = 0; private set
    var paddingTop: Int = 0; private set
    var paddingRight: Int = 0; private set
    var paddingBottom: Int = 0; private set
    val paddingStart: Int get() = paddingLeft
    val paddingEnd: Int get() = paddingRight
    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) { paddingLeft = left; paddingTop = top; paddingRight = right; paddingBottom = bottom; changed() }
    fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) = setPadding(start, top, end, bottom)

    // Geometry in pixels, written by the renderer after layout.
    var left: Int = 0; internal set
    var top: Int = 0; internal set
    var width: Int = 0; internal set
    var height: Int = 0; internal set
    val right: Int get() = left + width
    val bottom: Int get() = top + height
    val measuredWidth: Int get() = width
    val measuredHeight: Int get() = height
    var x: Float
        get() = left + translationX
        set(value) { translationX = value - left }
    var y: Float
        get() = top + translationY
        set(value) { translationY = value - top }
    /** Position inside the window, written by the renderer. */
    @JvmField internal var windowX: Int = 0
    @JvmField internal var windowY: Int = 0
    fun getLocationOnScreen(out: IntArray?) { out?.let { it[0] = windowX; it[1] = windowY } }
    fun getLocationInWindow(out: IntArray?) = getLocationOnScreen(out)
    fun getGlobalVisibleRect(out: android.graphics.Rect?): Boolean { out?.set(windowX, windowY, windowX + width, windowY + height); return width > 0 && height > 0 }
    fun getHitRect(out: android.graphics.Rect?) { out?.set(left, top, right, bottom) }
    val isLaidOut: Boolean get() = width > 0 || height > 0
    val isShown: Boolean get() = visibility == VISIBLE && (parent?.isShown ?: true)
    var isAttachedToWindow: Boolean = false
        private set
    /** Called by the renderer when the view enters or leaves the screen. */
    fun rendererAttached(attached: Boolean) {
        if (isAttachedToWindow == attached) return
        isAttachedToWindow = attached
        if (attached) { onAttachedToWindow(); attachListeners?.toList()?.forEach { it.onViewAttachedToWindow(this) } }
        else { onDetachedFromWindow(); attachListeners?.toList()?.forEach { it.onViewDetachedFromWindow(this) } }
    }
    fun rendererDraw(canvas: Canvas) = onDraw(canvas)
    fun rendererPlaced(left: Int, top: Int, width: Int, height: Int, windowX: Int, windowY: Int) { this.windowX = windowX; this.windowY = windowY; placed(left, top, width, height) }
    /** Set by the renderer; called whenever something that affects drawing changes. */
    var rendererObserver: (() -> Unit)?
        get() = observer
        set(value) { observer = value }
    fun rendererFocus(focused: Boolean) { if (hasFocusState != focused) { hasFocusState = focused; focusListener?.onFocusChange(this, focused) } }
    val rendererClick: OnClickListener? get() = clickListener
    val rendererLongClick: OnLongClickListener? get() = longClickListener
    val rendererTouch: OnTouchListener? get() = touchListener
    val rendererDrag: OnDragListener? get() = dragListener
    val rendererFocusRequest: Int get() = focusRequest
    fun rendererScrolled(newX: Int, newY: Int) { val oldX = scrollX; val oldY = scrollY; if (oldX == newX && oldY == newY) return; scrollX = newX; scrollY = newY; scrollListener?.onScrollChange(this, newX, newY, oldX, oldY) }
    val rootView: View get() = parent?.rootView ?: this
    val resources: android.content.res.Resources get() = context.resources

    private var layoutListeners: ArrayList<OnLayoutChangeListener>? = null
    fun addOnLayoutChangeListener(listener: OnLayoutChangeListener?) { if (listener != null) (layoutListeners ?: ArrayList<OnLayoutChangeListener>().also { layoutListeners = it }).add(listener) }
    fun removeOnLayoutChangeListener(listener: OnLayoutChangeListener?) { layoutListeners?.remove(listener) }
    /** Called by the renderer when the view's bounds are known or change. */
    internal fun placed(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int) {
        if (newLeft == left && newTop == top && newWidth == width && newHeight == height) return
        val oldLeft = left; val oldTop = top; val oldRight = right; val oldBottom = bottom
        val oldWidth = width; val oldHeight = height
        left = newLeft; top = newTop; width = newWidth; height = newHeight
        if (oldWidth != newWidth || oldHeight != newHeight) onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)
        onLayout(true, left, top, right, bottom)
        layoutListeners?.toList()?.forEach { it.onLayoutChange(this, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) }
    }
    protected open fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {}
    protected open fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    protected open fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {}
    protected fun setMeasuredDimension(width: Int, height: Int) { measuredOverrideWidth = width; measuredOverrideHeight = height }
    @JvmField internal var measuredOverrideWidth: Int = -1
    @JvmField internal var measuredOverrideHeight: Int = -1
    protected open fun onDraw(canvas: Canvas?) {}
    protected open fun onAttachedToWindow() {}
    protected open fun onDetachedFromWindow() {}
    fun setWillNotDraw(value: Boolean) {}

    interface OnAttachStateChangeListener {
        fun onViewAttachedToWindow(v: View?)
        fun onViewDetachedFromWindow(v: View?)
    }
    private var attachListeners: ArrayList<OnAttachStateChangeListener>? = null
    fun addOnAttachStateChangeListener(listener: OnAttachStateChangeListener?) { if (listener != null) (attachListeners ?: ArrayList<OnAttachStateChangeListener>().also { attachListeners = it }).add(listener) }
    fun removeOnAttachStateChangeListener(listener: OnAttachStateChangeListener?) { attachListeners?.remove(listener) }
    fun interface OnScrollChangeListener { fun onScrollChange(v: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) }
    @JvmField internal var scrollListener: OnScrollChangeListener? = null
    fun setOnScrollChangeListener(listener: OnScrollChangeListener?) { scrollListener = listener }
    fun interface OnApplyWindowInsetsListener { fun onApplyWindowInsets(v: View?, insets: WindowInsets?): WindowInsets? }
    @JvmField internal var insetsListener: OnApplyWindowInsetsListener? = null
    fun setOnApplyWindowInsetsListener(listener: OnApplyWindowInsetsListener?) { insetsListener = listener; changed() }
    fun requestApplyInsets() { changed() }
    var fitsSystemWindows: Boolean = false
    var systemUiVisibility: Int = 0
    var backgroundTintList: android.content.res.ColorStateList? = null
        set(value) { field = value; changed() }
    val overlay: ViewOverlay by lazy { ViewOverlay(this) }
    fun getLocalVisibleRect(out: android.graphics.Rect?): Boolean {
        // Visible part in the view's own coordinates, clipped by scrolling ancestors.
        var visibleTop = 0; var visibleBottom = height; var visibleLeft = 0; var visibleRight = width
        var ancestor = parent
        while (ancestor != null) {
            visibleTop = maxOf(visibleTop, ancestor.windowY - windowY); visibleBottom = minOf(visibleBottom, ancestor.windowY + ancestor.height - windowY)
            visibleLeft = maxOf(visibleLeft, ancestor.windowX - windowX); visibleRight = minOf(visibleRight, ancestor.windowX + ancestor.width - windowX)
            ancestor = ancestor.parent
        }
        out?.set(visibleLeft, visibleTop, visibleRight, visibleBottom)
        return visibleRight > visibleLeft && visibleBottom > visibleTop && isShown
    }
    fun setLayerType(type: Int, paint: Any?) {}

    @JvmField internal var clickListener: OnClickListener? = null
    @JvmField internal var longClickListener: OnLongClickListener? = null
    @JvmField internal var touchListener: OnTouchListener? = null
    @JvmField internal var dragListener: OnDragListener? = null
    @JvmField internal var focusListener: OnFocusChangeListener? = null
    fun setOnClickListener(listener: OnClickListener?) { clickListener = listener; if (listener != null) isClickable = true; changed() }
    fun setOnLongClickListener(listener: OnLongClickListener?) { longClickListener = listener; if (listener != null) isLongClickable = true; changed() }
    fun setOnTouchListener(listener: OnTouchListener?) { touchListener = listener; changed() }
    fun setOnDragListener(listener: OnDragListener?) { dragListener = listener }
    var onFocusChangeListener: OnFocusChangeListener?
        get() = focusListener
        set(value) { focusListener = value }
    fun hasOnClickListeners(): Boolean = clickListener != null
    open fun performClick(): Boolean { val l = clickListener ?: return false; l.onClick(this); return true }
    open fun performLongClick(): Boolean = longClickListener?.onLongClick(this) ?: false
    fun callOnClick(): Boolean = performClick()
    open fun onTouchEvent(event: MotionEvent?): Boolean = false
    /**
     * Children handle their own input natively, so the base implementation only reports whether this
     * view's own listener or onTouchEvent took the event; the renderer uses [MotionEvent.reachedBase]
     * to tell "passed through" from "intercepted by an override".
     */
    open fun dispatchTouchEvent(event: MotionEvent?): Boolean { event?.reachedBase = true; return (touchListener?.onTouch(this, event) ?: false) || onTouchEvent(event) }
    fun performHapticFeedback(feedbackConstant: Int): Boolean { compat.android.platformHaptic(); return true }
    fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean = performHapticFeedback(feedbackConstant)
    fun playSoundEffect(effect: Int) {}
    fun announceForAccessibility(text: CharSequence?) {}
    fun sendAccessibilityEvent(type: Int) {}
    fun setAccessibilityDelegate(delegate: Any?) {}
    fun setTooltipText(text: CharSequence?) {}

    var hasFocusState: Boolean = false
        internal set
    fun hasFocus(): Boolean = hasFocusState
    val isFocused: Boolean get() = hasFocusState
    @JvmField internal var focusRequest: Int = 0
    open fun requestFocus(): Boolean { focusRequest++; changed(); return true }
    open fun clearFocus() { focusRequest = -kotlin.math.abs(focusRequest) - 1; changed() }
    fun findFocus(): View? = if (hasFocusState) this else null

    open fun invalidate() { changed() }
    fun postInvalidate() = invalidate()
    fun postInvalidateOnAnimation() = invalidate()
    open fun requestLayout() { changed(); parent?.changed() }
    fun forceLayout() = requestLayout()
    fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {}
    fun layout(l: Int, t: Int, r: Int, b: Int) {}
    fun bringToFront() { parent?.let { p -> p.removeView(this); p.addView(this) } }
    fun post(action: compat.j.Runnable?): Boolean { if (action != null) android.os.Handler.main.post(action); return true }
    fun postDelayed(action: compat.j.Runnable?, delayMillis: Long): Boolean { if (action != null) android.os.Handler.main.postDelayed(action, delayMillis); return true }
    fun postOnAnimation(action: compat.j.Runnable?) { post(action) }
    fun removeCallbacks(action: compat.j.Runnable?): Boolean { android.os.Handler.main.removeCallbacks(action); return true }
    @JvmField internal var animationGeneration: Int = 0
    private var animator: ViewPropertyAnimator? = null
    fun animate(): ViewPropertyAnimator = animator ?: ViewPropertyAnimator(this).also { animator = it }
    fun clearAnimation() {}
    fun startDragAndDrop(data: Any?, shadow: Any?, localState: Any?, flags: Int): Boolean = DragController.start?.invoke(this, localState) ?: false

    open fun <T : View> findViewById(id: Int): T? { @Suppress("UNCHECKED_CAST") return if (this.id == id) this as T else null }
    open fun <T : View> findViewWithTag(tag: Any?): T? { @Suppress("UNCHECKED_CAST") return if (tag != null && tag == this.tag) this as T else null }
    open fun canScrollVertically(direction: Int): Boolean = false
    open fun scrollTo(x: Int, y: Int) {}
    open fun scrollBy(x: Int, y: Int) {}
    var scrollX: Int = 0
    var scrollY: Int = 0

    class DragShadowBuilder(val view: View?) { constructor() : this(null) }
    class MeasureSpec {
        companion object {
            const val UNSPECIFIED = 0
            const val EXACTLY = 1 shl 30
            const val AT_MOST = 2 shl 30
            @JvmStatic fun makeMeasureSpec(size: Int, mode: Int): Int = (size and 0x3fffffff) or mode
            @JvmStatic fun getSize(spec: Int): Int = spec and 0x3fffffff
            @JvmStatic fun getMode(spec: Int): Int = spec and (3 shl 30)
        }
    }

    companion object {
        const val NO_ID = -1
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
        const val LAYOUT_DIRECTION_LTR = 0
        const val LAYOUT_DIRECTION_RTL = 1
        const val LAYOUT_DIRECTION_INHERIT = 2
        const val LAYOUT_DIRECTION_LOCALE = 3
        const val TEXT_ALIGNMENT_INHERIT = 0
        const val TEXT_ALIGNMENT_GRAVITY = 1
        const val TEXT_ALIGNMENT_TEXT_START = 2
        const val TEXT_ALIGNMENT_TEXT_END = 3
        const val TEXT_ALIGNMENT_CENTER = 4
        const val TEXT_ALIGNMENT_VIEW_START = 5
        const val TEXT_ALIGNMENT_VIEW_END = 6
        const val TEXT_DIRECTION_INHERIT = 0
        const val TEXT_DIRECTION_FIRST_STRONG = 1
        const val TEXT_DIRECTION_ANY_RTL = 2
        const val TEXT_DIRECTION_LTR = 3
        const val TEXT_DIRECTION_RTL = 4
        const val TEXT_DIRECTION_LOCALE = 5
        const val TEXT_DIRECTION_FIRST_STRONG_LTR = 6
        const val TEXT_DIRECTION_FIRST_STRONG_RTL = 7
        const val IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0
        const val IMPORTANT_FOR_ACCESSIBILITY_YES = 1
        const val IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4
        const val IMPORTANT_FOR_ACCESSIBILITY_NO = 2
        const val OVER_SCROLL_NEVER = 2
        const val LAYER_TYPE_NONE = 0
        const val LAYER_TYPE_SOFTWARE = 1
        const val LAYER_TYPE_HARDWARE = 2
        const val FOCUS_DOWN = 130
        const val FOCUS_UP = 33
        const val DRAG_FLAG_GLOBAL = 256
        const val SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 8192
        const val SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 16
        private var nextId = 1
        @JvmStatic fun generateViewId(): Int = nextId++
    }
}

/** Animates a view's alpha, translation, scale and rotation to the requested values on the frame clock. */
class ViewPropertyAnimator internal constructor(private val view: View) {
    private val targets = HashMap<Int, Float>()
    private var end: compat.j.Runnable? = null
    private var durationMillis = 300L
    private var delayMillis = 0L
    private var easing: android.view.animation.Interpolator? = android.view.animation.AccelerateDecelerateInterpolator()
    private fun read(key: Int): Float = when (key) { 0 -> view.alpha; 1 -> view.translationX; 2 -> view.translationY; 3 -> view.scaleX; 4 -> view.scaleY; else -> view.rotation }
    private fun write(key: Int, value: Float) { when (key) { 0 -> view.alpha = value; 1 -> view.translationX = value; 2 -> view.translationY = value; 3 -> view.scaleX = value; 4 -> view.scaleY = value; else -> view.rotation = value } }
    fun alpha(value: Float): ViewPropertyAnimator { targets[0] = value; return this }
    fun translationX(value: Float): ViewPropertyAnimator { targets[1] = value; return this }
    fun translationY(value: Float): ViewPropertyAnimator { targets[2] = value; return this }
    fun scaleX(value: Float): ViewPropertyAnimator { targets[3] = value; return this }
    fun scaleY(value: Float): ViewPropertyAnimator { targets[4] = value; return this }
    fun rotation(value: Float): ViewPropertyAnimator { targets[5] = value; return this }
    fun setDuration(millis: Long): ViewPropertyAnimator { durationMillis = millis; return this }
    fun setStartDelay(millis: Long): ViewPropertyAnimator { delayMillis = millis; return this }
    fun setInterpolator(interpolator: android.view.animation.Interpolator?): ViewPropertyAnimator { easing = interpolator; return this }
    fun withEndAction(action: compat.j.Runnable?): ViewPropertyAnimator { end = action; return this }
    fun withStartAction(action: compat.j.Runnable?): ViewPropertyAnimator { action?.run(); return this }
    fun start() {
        val run = ++view.animationGeneration
        val goals = HashMap(targets); targets.clear()
        val finished = end; end = null
        val starts = goals.keys.associateWith { read(it) }
        fun finish() { for ((key, value) in goals) write(key, value); finished?.run() }
        if (!android.animation.ValueAnimator.enabled || durationMillis <= 0) { android.os.MainQueue.post(0) { if (run == view.animationGeneration) finish() }; return }
        var startNanos = -1L
        android.os.MainQueue.eachFrame { nanos ->
            if (run != view.animationGeneration) return@eachFrame false
            if (nanos == Long.MAX_VALUE) { finish(); return@eachFrame false }
            if (startNanos < 0) startNanos = nanos + delayMillis * 1_000_000L
            val fraction = ((nanos - startNanos).toFloat() / (durationMillis * 1_000_000f)).coerceIn(0f, 1f)
            if (fraction >= 1f) { finish(); return@eachFrame false }
            val eased = easing?.getInterpolation(fraction) ?: fraction
            for ((key, value) in goals) write(key, starts.getValue(key) + (value - starts.getValue(key)) * eased)
            true
        }
    }
    /** Stops where it is; the end action is dropped, as on Android. */
    fun cancel() { view.animationGeneration++; targets.clear(); end = null }
}

open class ViewGroup(context: Context?) : View(context) {
    open class LayoutParams(@JvmField var width: Int, @JvmField var height: Int) {
        constructor(source: LayoutParams?) : this(source?.width ?: WRAP_CONTENT, source?.height ?: WRAP_CONTENT)
        companion object {
            const val MATCH_PARENT = -1
            const val FILL_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
    open class MarginLayoutParams(width: Int, height: Int) : LayoutParams(width, height) {
        @JvmField var leftMargin: Int = 0
        @JvmField var topMargin: Int = 0
        @JvmField var rightMargin: Int = 0
        @JvmField var bottomMargin: Int = 0
        var marginStart: Int
            get() = leftMargin
            set(value) { leftMargin = value }
        var marginEnd: Int
            get() = rightMargin
            set(value) { rightMargin = value }
        fun setMargins(left: Int, top: Int, right: Int, bottom: Int) { leftMargin = left; topMargin = top; rightMargin = right; bottomMargin = bottom }
    }

    @JvmField internal val children = ArrayList<View>()
    val childCount: Int get() = children.size
    fun getChildAt(index: Int): View? = children.getOrNull(index)
    fun indexOfChild(child: View?): Int = children.indexOf(child)

    open fun addView(child: View?) = addView(child, -1)
    open fun addView(child: View?, index: Int) {
        if (child == null) return
        child.parent?.removeView(child)
        if (index < 0 || index > children.size) children.add(child) else children.add(index, child)
        child.parent = this
        changed()
    }
    open fun addView(child: View?, params: LayoutParams?) { child?.layoutParams = params; addView(child, -1) }
    open fun addView(child: View?, index: Int, params: LayoutParams?) { child?.layoutParams = params; addView(child, index) }
    open fun addView(child: View?, width: Int, height: Int) { child?.layoutParams = LayoutParams(width, height); addView(child, -1) }
    open fun removeView(child: View?) { if (children.remove(child)) { child?.parent = null; changed() } }
    open fun removeViewAt(index: Int) { if (index in children.indices) removeView(children[index]) }
    open fun removeAllViews() { children.forEach { it.parent = null }; children.clear(); changed() }
    fun removeAllViewsInLayout() = removeAllViews()

    var clipChildren: Boolean = true
    var clipToPadding: Boolean = true
    var layoutTransition: Any? = null
    var descendantFocusability: Int = 0
    var isMotionEventSplittingEnabled: Boolean = true
    @JvmField internal var disallowIntercept: Boolean = false
    open fun requestDisallowInterceptTouchEvent(disallow: Boolean) { disallowIntercept = disallow; parent?.requestDisallowInterceptTouchEvent(disallow) }
    open fun onInterceptTouchEvent(event: MotionEvent?): Boolean = false

    override fun <T : View> findViewById(id: Int): T? {
        super.findViewById<T>(id)?.let { return it }
        for (child in children) child.findViewById<T>(id)?.let { return it }
        return null
    }
    override fun <T : View> findViewWithTag(tag: Any?): T? {
        super.findViewWithTag<T>(tag)?.let { return it }
        for (child in children) child.findViewWithTag<T>(tag)?.let { return it }
        return null
    }

    companion object {
        const val FOCUS_BEFORE_DESCENDANTS = 0x20000
        const val FOCUS_AFTER_DESCENDANTS = 0x40000
        const val FOCUS_BLOCK_DESCENDANTS = 0x60000
    }
}

object Gravity {
    const val NO_GRAVITY = 0
    const val TOP = 48
    const val BOTTOM = 80
    const val LEFT = 3
    const val RIGHT = 5
    const val CENTER_VERTICAL = 16
    const val CENTER_HORIZONTAL = 1
    const val CENTER = 17
    const val FILL_HORIZONTAL = 7
    const val FILL_VERTICAL = 112
    const val FILL = 119
    const val START = 8388611
    const val END = 8388613
    const val HORIZONTAL_GRAVITY_MASK = 7
    const val VERTICAL_GRAVITY_MASK = 112
    const val RELATIVE_HORIZONTAL_GRAVITY_MASK = 8388615
}

/** Pointer event in the coordinates of the view that receives it. */
class MotionEvent(private var actionCode: Int, private val px: Float, private val py: Float, private val windowPx: Float, private val windowPy: Float, val eventTime: Long) {
    var action: Int
        get() = actionCode
        set(value) { actionCode = value }
    /** True once the event reached View.dispatchTouchEvent, i.e. no override swallowed it first. */
    @JvmField var reachedBase: Boolean = false
    val actionMasked: Int get() = actionCode
    val x: Float get() = px
    val y: Float get() = py
    val rawX: Float get() = windowPx
    val rawY: Float get() = windowPy
    val pointerCount: Int get() = 1
    val downTime: Long get() = eventTime
    fun getX(pointerIndex: Int): Float = px
    fun getY(pointerIndex: Int): Float = py
    fun getPointerId(pointerIndex: Int): Int = 0
    fun findPointerIndex(pointerId: Int): Int = 0
    val actionIndex: Int get() = 0
    fun recycle() {}
    fun offsetLocation(dx: Float, dy: Float) {}

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
        const val INVALID_POINTER_ID = -1
        @JvmStatic fun obtain(source: MotionEvent?): MotionEvent = MotionEvent(source!!.actionCode, source.px, source.py, source.windowPx, source.windowPy, source.eventTime)
        @JvmStatic fun obtain(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float, metaState: Int): MotionEvent = MotionEvent(action, x, y, x, y, eventTime)
    }
}

class DragEvent(val action: Int, val x: Float, val y: Float, val localState: Any?) {
    companion object {
        const val ACTION_DRAG_STARTED = 1
        const val ACTION_DRAG_LOCATION = 2
        const val ACTION_DROP = 3
        const val ACTION_DRAG_ENDED = 4
        const val ACTION_DRAG_ENTERED = 5
        const val ACTION_DRAG_EXITED = 6
    }
}

class VelocityTracker private constructor() {
    private val times = ArrayList<Long>()
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    var xVelocity: Float = 0f; private set
    var yVelocity: Float = 0f; private set
    fun addMovement(event: MotionEvent?) {
        if (event == null) return
        times.add(event.eventTime); xs.add(event.rawX); ys.add(event.rawY)
        while (times.size > 20) { times.removeAt(0); xs.removeAt(0); ys.removeAt(0) }
    }
    /** Average velocity over the last 100 ms of samples, in pixels per [units] milliseconds. */
    fun computeCurrentVelocity(units: Int) = computeCurrentVelocity(units, Float.MAX_VALUE)
    fun computeCurrentVelocity(units: Int, maxVelocity: Float) {
        xVelocity = 0f; yVelocity = 0f
        if (times.size < 2) return
        val last = times.size - 1
        var first = last
        while (first > 0 && times[last] - times[first - 1] <= 100) first--
        val dt = (times[last] - times[first]).toFloat()
        if (dt <= 0f) return
        xVelocity = ((xs[last] - xs[first]) / dt * units).coerceIn(-maxVelocity, maxVelocity)
        yVelocity = ((ys[last] - ys[first]) / dt * units).coerceIn(-maxVelocity, maxVelocity)
    }
    fun getXVelocity(id: Int): Float = xVelocity
    fun getYVelocity(id: Int): Float = yVelocity
    fun clear() { times.clear(); xs.clear(); ys.clear() }
    fun recycle() = clear()
    companion object { @JvmStatic fun obtain(): VelocityTracker = VelocityTracker() }
}

class ViewConfiguration private constructor(private val density: Float) {
    val scaledTouchSlop: Int get() = (8 * density).toInt()
    val scaledMinimumFlingVelocity: Int get() = (50 * density).toInt()
    val scaledMaximumFlingVelocity: Int get() = (8000 * density).toInt()
    val scaledPagingTouchSlop: Int get() = (16 * density).toInt()
    companion object {
        @JvmStatic fun get(context: Context?): ViewConfiguration = ViewConfiguration((context ?: Context.app).resources.displayMetrics.density)
        @JvmStatic fun getLongPressTimeout(): Int = 400
        @JvmStatic fun getTapTimeout(): Int = 100
    }
}

/** The activity or dialog window: status bar colours and soft-input mode are accepted and ignored. */
class Window internal constructor() {
    @JvmField internal var content: View? = null
    val decorView: View get() = content ?: View(null).also { content = it }
    var statusBarColor: Int = 0
    var navigationBarColor: Int = 0
    var attributes: WindowManager.LayoutParams = WindowManager.LayoutParams()
    fun setSoftInputMode(mode: Int) {}
    fun setBackgroundDrawable(drawable: Drawable?) { backgroundDrawable = drawable }
    fun setBackgroundDrawableResource(id: Int) { backgroundDrawable = null }
    @JvmField internal var backgroundDrawable: Drawable? = null
    fun setLayout(width: Int, height: Int) { layoutWidth = width; layoutHeight = height }
    @JvmField internal var layoutWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    @JvmField internal var layoutHeight: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    fun setGravity(gravity: Int) { this.gravity = gravity }
    @JvmField internal var gravity: Int = Gravity.CENTER
    fun setDimAmount(amount: Float) {}
    fun addFlags(flags: Int) {}
    fun clearFlags(flags: Int) {}
    fun setFlags(flags: Int, mask: Int) {}
    fun setDecorFitsSystemWindows(fits: Boolean) {}
    fun setWindowAnimations(resource: Int) {}
    fun requestFeature(feature: Int): Boolean = true
    companion object { const val FEATURE_NO_TITLE = 1 }
}

class WindowManager {
    class LayoutParams {
        @JvmField var width: Int = ViewGroup.LayoutParams.MATCH_PARENT
        @JvmField var height: Int = ViewGroup.LayoutParams.MATCH_PARENT
        @JvmField var gravity: Int = Gravity.CENTER
        @JvmField var dimAmount: Float = 0.5f
        @JvmField var softInputMode: Int = 0
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
            const val SOFT_INPUT_ADJUST_RESIZE = 16
            const val SOFT_INPUT_ADJUST_PAN = 32
            const val SOFT_INPUT_STATE_VISIBLE = 4
            const val SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5
            const val SOFT_INPUT_STATE_HIDDEN = 2
            const val SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3
            const val FLAG_SECURE = 8192
            const val FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = Int.MIN_VALUE
            const val FLAG_DIM_BEHIND = 2
            const val FLAG_KEEP_SCREEN_ON = 128
        }
    }
}

object HapticFeedbackConstants {
    const val LONG_PRESS = 0
    const val VIRTUAL_KEY = 1
    const val KEYBOARD_TAP = 3
    const val CLOCK_TICK = 4
    const val CONTEXT_CLICK = 6
    const val CONFIRM = 16
    const val REJECT = 17
}

/** Installed by the renderer: starts dragging [view]; drag events then go to the views with a drag listener. */
object DragController { var start: ((view: View, localState: Any?) -> Boolean)? = null }

/** Views drawn above a view's content (used for transition overlays). */
class ViewOverlay internal constructor(private val owner: View) {
    val views = ArrayList<View>()
    fun add(view: View?) { if (view != null && view !in views) { views.add(view); owner.changed() } }
    fun remove(view: View?) { if (views.remove(view)) owner.changed() }
    fun clear() { if (views.isNotEmpty()) { views.clear(); owner.changed() } }
}
typealias ViewGroupOverlay = ViewOverlay

class WindowInsets(val systemWindowInsetLeft: Int, val systemWindowInsetTop: Int, val systemWindowInsetRight: Int, val systemWindowInsetBottom: Int) {
    fun consumeSystemWindowInsets(): WindowInsets = this
}
