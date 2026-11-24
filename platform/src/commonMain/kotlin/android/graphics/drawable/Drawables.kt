package android.graphics.drawable

import android.content.res.ColorStateList
import android.graphics.Rect
import kotlin.jvm.JvmField

/** Backgrounds are descriptions (colour, shape, corners, stroke) that the renderer paints. */
abstract class Drawable {
    abstract class ConstantState { abstract fun newDrawable(): Drawable }
    var alpha: Int = 255
    @JvmField val boundsRect = Rect()
    val bounds: Rect get() = boundsRect
    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) { boundsRect.set(left, top, right, bottom) }
    @compat.j.Fun fun setBounds(bounds: Rect?) { boundsRect.set(bounds) }
    open fun mutate(): Drawable = this
    abstract fun copy(): Drawable
    open val constantState: ConstantState? get() = object : ConstantState() { override fun newDrawable(): Drawable = copy() }
    open val intrinsicWidth: Int get() = -1
    open val intrinsicHeight: Int get() = -1
    var tintColor: Int? = null
    fun setTint(color: Int) { tintColor = color }
    fun setTintList(tint: ColorStateList?) { tintColor = tint?.defaultColor }
    fun setColorFilter(color: Int, mode: Any?) { tintColor = color }
    fun invalidateSelf() {}
}

class ColorDrawable(var color: Int) : Drawable() {
    constructor() : this(0)
    override fun copy(): Drawable = ColorDrawable(color).also { it.alpha = alpha }
}

class GradientDrawable() : Drawable() {
    constructor(orientation: Orientation?, colors: IntArray?) : this() { orientationKind = orientation ?: Orientation.TOP_BOTTOM; gradientColors = colors }
    enum class Orientation { TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR }
    @JvmField var shapeKind: Int = RECTANGLE
    @JvmField var fillColor: Int = 0
    @JvmField var gradientColors: IntArray? = null
    @JvmField var orientationKind: Orientation = Orientation.TOP_BOTTOM
    @JvmField var radius: Float = 0f
    /** Eight values: x and y radius for top-left, top-right, bottom-right, bottom-left. */
    @JvmField var radii: FloatArray? = null
    @JvmField var strokeWidthPx: Int = 0
    @JvmField var strokeColor: Int = 0
    @JvmField var dashWidth: Float = 0f
    @JvmField var dashGap: Float = 0f
    @JvmField var sizeWidth: Int = -1
    @JvmField var sizeHeight: Int = -1

    var shape: Int
        get() = shapeKind
        set(value) { shapeKind = value }
    fun setColor(color: Int) { fillColor = color; gradientColors = null }
    fun setColor(colors: ColorStateList?) { setColor(colors?.defaultColor ?: 0) }
    var colors: IntArray?
        get() = gradientColors
        set(value) { gradientColors = value }
    var orientation: Orientation?
        get() = orientationKind
        set(value) { orientationKind = value ?: Orientation.TOP_BOTTOM }
    var cornerRadius: Float
        get() = radius
        set(value) { radius = value; radii = null }
    var cornerRadii: FloatArray?
        get() = radii
        set(value) { radii = value }
    fun setStroke(width: Int, color: Int) { strokeWidthPx = width; strokeColor = color; dashWidth = 0f; dashGap = 0f }
    fun setStroke(width: Int, color: Int, dashWidth: Float, dashGap: Float) { strokeWidthPx = width; strokeColor = color; this.dashWidth = dashWidth; this.dashGap = dashGap }
    fun setSize(width: Int, height: Int) { sizeWidth = width; sizeHeight = height }
    override val intrinsicWidth: Int get() = sizeWidth
    override val intrinsicHeight: Int get() = sizeHeight
    override fun copy(): Drawable = GradientDrawable().also {
        it.shapeKind = shapeKind; it.fillColor = fillColor; it.gradientColors = gradientColors; it.orientationKind = orientationKind
        it.radius = radius; it.radii = radii; it.strokeWidthPx = strokeWidthPx; it.strokeColor = strokeColor
        it.dashWidth = dashWidth; it.dashGap = dashGap; it.sizeWidth = sizeWidth; it.sizeHeight = sizeHeight; it.alpha = alpha
    }
    companion object {
        const val RECTANGLE = 0
        const val OVAL = 1
        const val LINE = 2
        const val RING = 3
    }
}

/** Press feedback over [content]; [mask] only limits the ripple's shape. */
class RippleDrawable(val color: ColorStateList?, val content: Drawable?, val mask: Drawable?) : Drawable() {
    override fun copy(): Drawable = RippleDrawable(color, content?.copy(), mask?.copy())
}

class LayerDrawable(val layers: Array<Drawable>) : Drawable() {
    val insets = HashMap<Int, IntArray>()
    val numberOfLayers: Int get() = layers.size
    fun getDrawable(index: Int): Drawable? = layers[index]
    fun setLayerInset(index: Int, left: Int, top: Int, right: Int, bottom: Int) { insets[index] = intArrayOf(left, top, right, bottom) }
    override fun copy(): Drawable = LayerDrawable(Array(layers.size) { layers[it].copy() }).also { it.insets.putAll(insets) }
}

/** A drawable resource (vector icon) referenced by id; painted by the renderer. */
class ResourceDrawable(val id: Int) : Drawable() {
    override fun copy(): Drawable = ResourceDrawable(id).also { it.tintColor = tintColor }
}
