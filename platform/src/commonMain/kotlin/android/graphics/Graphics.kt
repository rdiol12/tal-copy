// android.graphics: value classes plus a Canvas whose drawing calls the renderer implements.
package android.graphics

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

class Rect(@JvmField var left: Int, @JvmField var top: Int, @JvmField var right: Int, @JvmField var bottom: Int) {
    constructor() : this(0, 0, 0, 0)
    constructor(source: Rect?) : this(source?.left ?: 0, source?.top ?: 0, source?.right ?: 0, source?.bottom ?: 0)
    fun set(left: Int, top: Int, right: Int, bottom: Int) { this.left = left; this.top = top; this.right = right; this.bottom = bottom }
    fun set(source: Rect?) { if (source != null) set(source.left, source.top, source.right, source.bottom) }
    fun width(): Int = right - left
    fun height(): Int = bottom - top
    fun centerX(): Int = (left + right) shr 1
    fun centerY(): Int = (top + bottom) shr 1
    fun exactCenterX(): Float = (left + right) * 0.5f
    fun exactCenterY(): Float = (top + bottom) * 0.5f
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun contains(x: Int, y: Int): Boolean = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
    fun offset(dx: Int, dy: Int) { left += dx; right += dx; top += dy; bottom += dy }
    fun inset(dx: Int, dy: Int) { left += dx; right -= dx; top += dy; bottom -= dy }
    fun setEmpty() = set(0, 0, 0, 0)
    override fun equals(other: Any?): Boolean = other is Rect && other.left == left && other.top == top && other.right == right && other.bottom == bottom
    override fun hashCode(): Int = ((left * 31 + top) * 31 + right) * 31 + bottom
    override fun toString(): String = "Rect($left, $top - $right, $bottom)"
}

class RectF(@JvmField var left: Float, @JvmField var top: Float, @JvmField var right: Float, @JvmField var bottom: Float) {
    constructor() : this(0f, 0f, 0f, 0f)
    constructor(source: RectF?) : this(source?.left ?: 0f, source?.top ?: 0f, source?.right ?: 0f, source?.bottom ?: 0f)
    constructor(source: Rect?) : this((source?.left ?: 0).toFloat(), (source?.top ?: 0).toFloat(), (source?.right ?: 0).toFloat(), (source?.bottom ?: 0).toFloat())
    fun set(left: Float, top: Float, right: Float, bottom: Float) { this.left = left; this.top = top; this.right = right; this.bottom = bottom }
    fun set(source: RectF?) { if (source != null) set(source.left, source.top, source.right, source.bottom) }
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) * 0.5f
    fun centerY(): Float = (top + bottom) * 0.5f
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun contains(x: Float, y: Float): Boolean = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun inset(dx: Float, dy: Float) { left += dx; right -= dx; top += dy; bottom -= dy }
}

class Point(@JvmField var x: Int, @JvmField var y: Int) { constructor() : this(0, 0) }
class PointF(@JvmField var x: Float, @JvmField var y: Float) { constructor() : this(0f, 0f) }

object Color {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val TRANSPARENT = 0
    const val RED = -0x10000
    const val GREEN = -0xff0100
    const val BLUE = -0xffff01
    const val GRAY = -0x777778
    const val DKGRAY = -0xbbbbbc
    const val LTGRAY = -0x333334
    const val YELLOW = -0x100
    @JvmStatic fun alpha(color: Int): Int = color ushr 24
    @JvmStatic fun red(color: Int): Int = (color shr 16) and 0xFF
    @JvmStatic fun green(color: Int): Int = (color shr 8) and 0xFF
    @JvmStatic fun blue(color: Int): Int = color and 0xFF
    @JvmStatic fun rgb(red: Int, green: Int, blue: Int): Int = -0x1000000 or (red shl 16) or (green shl 8) or blue
    @JvmStatic fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    @JvmStatic fun parseColor(value: String): Int {
        require(value.startsWith("#")) { "Unknown color" }
        val number = value.substring(1).toLong(16)
        return when (value.length) {
            7 -> (number or 0xFF000000L).toInt()
            9 -> number.toInt()
            else -> throw IllegalArgumentException("Unknown color")
        }
    }
}

class Typeface private constructor(val family: String?, val style: Int) {
    val isBold: Boolean get() = style and BOLD != 0
    val isItalic: Boolean get() = style and ITALIC != 0
    /** Font weight 100..900 implied by the family name and style. */
    val weight: Int get() = when {
        isBold -> 700
        family?.endsWith("-medium") == true -> 500
        family?.endsWith("-light") == true -> 300
        family?.endsWith("-black") == true -> 900
        else -> 400
    }
    val isMonospace: Boolean get() = family == "monospace"
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3
        @JvmField val DEFAULT = Typeface(null, NORMAL)
        @JvmField val DEFAULT_BOLD = Typeface(null, BOLD)
        @JvmField val SANS_SERIF = Typeface("sans-serif", NORMAL)
        @JvmField val MONOSPACE = Typeface("monospace", NORMAL)
        @JvmStatic fun create(family: String?, style: Int): Typeface = Typeface(family, style)
        @JvmStatic fun create(base: Typeface?, style: Int): Typeface = Typeface(base?.family, style)
        @JvmStatic fun defaultFromStyle(style: Int): Typeface = Typeface(null, style)
    }
}

open class Paint(flags: Int) {
    constructor() : this(0)
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    class FontMetrics { @JvmField var ascent = 0f; @JvmField var descent = 0f; @JvmField var top = 0f; @JvmField var bottom = 0f }

    var color: Int = Color.BLACK
    var alpha: Int
        get() = color ushr 24
        set(value) { color = (color and 0xFFFFFF) or (value shl 24) }
    var style: Style? = Style.FILL
    var strokeWidth: Float = 0f
    var strokeCap: Cap? = Cap.BUTT
    var strokeJoin: Join? = Join.MITER
    var textSize: Float = 12f
    var textAlign: Align? = Align.LEFT
    var typeface: Typeface? = null
    var isAntiAlias: Boolean = flags and ANTI_ALIAS_FLAG != 0
    var isFakeBoldText: Boolean = false
    var shader: Any? = null
    var pathEffect: Any? = null
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {}
    fun measureText(text: String?): Float = TextMeasure.width(text ?: "", this)
    fun descent(): Float = textSize * 0.24f
    fun ascent(): Float = -textSize * 0.93f
    fun getTextBounds(text: String?, start: Int, end: Int, bounds: Rect?) { bounds?.set(0, ascent().toInt(), measureText(text?.substring(start, end)).toInt(), descent().toInt()) }
    val fontMetrics: FontMetrics get() = FontMetrics().also { it.ascent = ascent(); it.descent = descent(); it.top = ascent(); it.bottom = descent() }
    fun reset() { color = Color.BLACK; style = Style.FILL; strokeWidth = 0f; textSize = 12f; textAlign = Align.LEFT; typeface = null }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }
}

/** Text measuring is supplied by the renderer; the fallback is a rough estimate. */
object TextMeasure {
    var measurer: ((text: String, paint: Paint) -> Float)? = null
    fun width(text: String, paint: Paint): Float = measurer?.invoke(text, paint) ?: (text.length * paint.textSize * 0.55f)
}

class Path {
    /** Recorded segments: a verb followed by its coordinates. */
    class Segment(val verb: Char, val points: FloatArray)
    val segments = ArrayList<Segment>()
    constructor()
    constructor(source: Path?) { if (source != null) segments.addAll(source.segments) }
    fun moveTo(x: Float, y: Float) { segments.add(Segment('M', floatArrayOf(x, y))) }
    fun lineTo(x: Float, y: Float) { segments.add(Segment('L', floatArrayOf(x, y))) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { segments.add(Segment('Q', floatArrayOf(x1, y1, x2, y2))) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { segments.add(Segment('C', floatArrayOf(x1, y1, x2, y2, x3, y3))) }
    fun close() { segments.add(Segment('Z', FloatArray(0))) }
    fun reset() { segments.clear() }
    fun rewind() = reset()
    val isEmpty: Boolean get() = segments.isEmpty()
}

/** Drawing surface handed to View.onDraw; the renderer supplies the real implementation. */
open class Canvas {
    open val width: Int get() = 0
    open val height: Int get() = 0
    open fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint?) {}
    open fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?) {}
    fun drawRect(rect: RectF?, paint: Paint?) { if (rect != null) drawRect(rect.left, rect.top, rect.right, rect.bottom, paint) }
    fun drawRect(rect: Rect?, paint: Paint?) { if (rect != null) drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint) }
    open fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint?) {}
    fun drawRoundRect(rect: RectF?, rx: Float, ry: Float, paint: Paint?) { if (rect != null) drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint) }
    open fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?) {}
    fun drawOval(rect: RectF?, paint: Paint?) { if (rect != null) drawOval(rect.left, rect.top, rect.right, rect.bottom, paint) }
    open fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint?) {}
    open fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint?) {}
    fun drawArc(oval: RectF?, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint?) { if (oval != null) drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, paint) }
    open fun drawPath(path: Path?, paint: Paint?) {}
    /** [y] is the text baseline; [x] is the left, centre or right edge according to the paint's alignment. */
    open fun drawText(text: String?, x: Float, y: Float, paint: Paint?) {}
    fun drawText(text: CharSequence?, start: Int, end: Int, x: Float, y: Float, paint: Paint?) = drawText(text?.subSequence(start, end)?.toString(), x, y, paint)
    open fun drawColor(color: Int) {}
    open fun save(): Int = 0
    open fun restore() {}
    open fun translate(dx: Float, dy: Float) {}
    open fun rotate(degrees: Float) {}
    open fun rotate(degrees: Float, px: Float, py: Float) { translate(px, py); rotate(degrees); translate(-px, -py) }
    open fun scale(sx: Float, sy: Float) {}
    open fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean = true
    fun clipRect(rect: RectF?): Boolean = rect == null || clipRect(rect.left, rect.top, rect.right, rect.bottom)
}
