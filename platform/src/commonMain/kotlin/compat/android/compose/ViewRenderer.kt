// Draws the android.view / android.widget model objects with Compose. Every View becomes exactly
// one layout node, so a parent's measure policy can pair its visible children with their
// LayoutParams by position. Sizes are in pixels throughout, as on Android.
package compat.android.compose

import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Colours for the parts Android would take from the app theme; the app sets them before drawing. */
object ViewTheme {
    var accent: Int = 0xff255DD2.toInt()
    var ink: Int = 0xff182333.toInt()
    var muted: Int = 0xff4E5D70.toInt()
    var surface: Int = 0xffFFFFFF.toInt()
    var outline: Int = 0xffDBE3ED.toInt()
}

private fun composeColor(argb: Int): Color = Color(argb)
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val UNBOUNDED = Constraints.Infinity

/** Views of these exact classes are drawn and handled entirely by this file; anything else is an app subclass. */
private fun isFrameworkClass(view: View): Boolean {
    val type = view::class
    return type == View::class || type == FrameLayout::class || type == LinearLayout::class || type == ScrollView::class ||
        type == HorizontalScrollView::class || type == RadioGroup::class || type == Spinner::class || type == TextView::class ||
        type == EditText::class || type == AutoCompleteTextView::class || type == ImageView::class || type == ProgressBar::class ||
        type == android.widget.Button::class || type == android.widget.CheckBox::class || type == RadioButton::class ||
        type == android.widget.Switch::class || type == android.widget.ImageButton::class || type == android.widget.Space::class ||
        type == android.widget.DatePicker::class
}

// ---- Gravity ----

/** -1 = left, 0 = centre, 1 = right. */
private fun horizontalBias(gravity: Int, rtl: Boolean): Int {
    val relative = gravity and 0x00800000 != 0
    return when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
        Gravity.CENTER_HORIZONTAL -> 0
        Gravity.LEFT -> if (relative && rtl) 1 else -1
        Gravity.RIGHT -> if (relative && rtl) -1 else 1
        else -> if (rtl) 1 else -1
    }
}
/** -1 = top, 0 = centre, 1 = bottom. */
private fun verticalBias(gravity: Int): Int = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
    Gravity.CENTER_VERTICAL -> 0
    Gravity.BOTTOM -> 1
    else -> -1
}
private fun align(bias: Int, space: Int): Int = when (bias) { 0 -> space / 2; 1 -> space; else -> 0 }

// ---- Backgrounds ----

internal fun backgroundShapeOf(drawable: Drawable?): Shape = when (drawable) {
    is GradientDrawable -> when {
        drawable.shapeKind == GradientDrawable.OVAL -> CircleShape
        drawable.radii != null -> drawable.radii!!.let { RoundedCornerShape(it[0], it[2], it[4], it[6]) }
        drawable.radius > 0f -> RoundedCornerShape(drawable.radius)
        else -> RectangleShape
    }
    is RippleDrawable -> backgroundShapeOf(drawable.content ?: drawable.mask)
    is LayerDrawable -> backgroundShapeOf(drawable.layers.firstOrNull())
    else -> RectangleShape
}

internal fun DrawScope.paintBackground(drawable: Drawable?) = paintDrawable(drawable)

private fun DrawScope.paintDrawable(drawable: Drawable?) {
    when (drawable) {
        null -> {}
        is ColorDrawable -> if (drawable.color ushr 24 != 0) drawRect(composeColor(drawable.color))
        is RippleDrawable -> paintDrawable(drawable.content)
        is LayerDrawable -> drawable.layers.forEach { paintDrawable(it) }
        is GradientDrawable -> {
            val colors = drawable.gradientColors
            val brush: Brush = if (colors != null && colors.size >= 2) {
                val w = size.width; val h = size.height
                val (from, to) = when (drawable.orientationKind) {
                    GradientDrawable.Orientation.TOP_BOTTOM -> Offset(0f, 0f) to Offset(0f, h)
                    GradientDrawable.Orientation.BOTTOM_TOP -> Offset(0f, h) to Offset(0f, 0f)
                    GradientDrawable.Orientation.LEFT_RIGHT -> Offset(0f, 0f) to Offset(w, 0f)
                    GradientDrawable.Orientation.RIGHT_LEFT -> Offset(w, 0f) to Offset(0f, 0f)
                    GradientDrawable.Orientation.TL_BR -> Offset(0f, 0f) to Offset(w, h)
                    GradientDrawable.Orientation.BR_TL -> Offset(w, h) to Offset(0f, 0f)
                    GradientDrawable.Orientation.TR_BL -> Offset(w, 0f) to Offset(0f, h)
                    GradientDrawable.Orientation.BL_TR -> Offset(0f, h) to Offset(w, 0f)
                }
                Brush.linearGradient(colors.map { composeColor(it) }, from, to)
            } else SolidColor(composeColor(drawable.fillColor))
            val filled = colors != null || drawable.fillColor ushr 24 != 0
            val stroke = drawable.strokeWidthPx.toFloat()
            val radius = min(drawable.radius, min(size.width, size.height) / 2f)
            if (drawable.shapeKind == GradientDrawable.OVAL) {
                if (filled) drawOval(brush)
                if (stroke > 0f) drawOval(composeColor(drawable.strokeColor), Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            } else {
                if (filled) drawRoundRect(brush, cornerRadius = CornerRadius(radius, radius))
                if (stroke > 0f && drawable.strokeColor ushr 24 != 0) {
                    val effect = if (drawable.dashWidth > 0f) PathEffect.dashPathEffect(floatArrayOf(drawable.dashWidth, drawable.dashGap)) else null
                    val inner = max(0f, radius - stroke / 2)
                    drawRoundRect(composeColor(drawable.strokeColor), Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), CornerRadius(inner, inner), style = Stroke(stroke, pathEffect = effect))
                }
            }
        }
        else -> {}
    }
}

// ---- Canvas for views that paint themselves ----

private fun paintTextStyle(paint: Paint, density: Density): TextStyle {
    val face = paint.typeface
    return TextStyle(
        color = composeColor(paint.color),
        fontSize = with(density) { paint.textSize.toSp() },
        fontWeight = FontWeight(if (paint.isFakeBoldText) 700 else face?.weight ?: 400),
        fontStyle = if (face?.isItalic == true) FontStyle.Italic else FontStyle.Normal,
    )
}

private class ComposeCanvas(private val scope: DrawScope, private val measurer: TextMeasurer) : android.graphics.Canvas() {
    override val width: Int get() = scope.size.width.toInt()
    override val height: Int get() = scope.size.height.toInt()
    private fun cap(paint: Paint) = when (paint.strokeCap) { Paint.Cap.ROUND -> StrokeCap.Round; Paint.Cap.SQUARE -> StrokeCap.Square; else -> StrokeCap.Butt }
    private fun style(paint: Paint) = if (paint.style == Paint.Style.STROKE) Stroke(max(paint.strokeWidth, 1f), cap = cap(paint)) else Fill
    override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint?) {
        if (paint == null) return
        scope.drawLine(composeColor(paint.color), Offset(startX, startY), Offset(stopX, stopY), max(paint.strokeWidth, 1f), cap(paint))
    }
    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?) {
        if (paint == null) return
        scope.drawRect(composeColor(paint.color), Offset(left, top), Size(right - left, bottom - top), style = style(paint))
    }
    override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint?) {
        if (paint == null) return
        scope.drawRoundRect(composeColor(paint.color), Offset(left, top), Size(right - left, bottom - top), CornerRadius(rx, ry), style = style(paint))
    }
    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?) {
        if (paint == null) return
        scope.drawOval(composeColor(paint.color), Offset(left, top), Size(right - left, bottom - top), style = style(paint))
    }
    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint?) {
        if (paint == null) return
        scope.drawCircle(composeColor(paint.color), radius, Offset(cx, cy), style = style(paint))
    }
    override fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint?) {
        if (paint == null) return
        scope.drawArc(composeColor(paint.color), startAngle, sweepAngle, useCenter, Offset(left, top), Size(right - left, bottom - top), style = style(paint))
    }
    override fun drawPath(path: android.graphics.Path?, paint: Paint?) {
        if (path == null || paint == null) return
        val shape = androidx.compose.ui.graphics.Path()
        for (segment in path.segments) {
            val p = segment.points
            when (segment.verb) {
                'M' -> shape.moveTo(p[0], p[1])
                'L' -> shape.lineTo(p[0], p[1])
                'Q' -> shape.quadraticTo(p[0], p[1], p[2], p[3])
                'C' -> shape.cubicTo(p[0], p[1], p[2], p[3], p[4], p[5])
                else -> shape.close()
            }
        }
        scope.drawPath(shape, composeColor(paint.color), style = style(paint))
    }
    override fun drawText(text: String?, x: Float, y: Float, paint: Paint?) {
        if (text.isNullOrEmpty() || paint == null) return
        val layout = measurer.measure(text, paintTextStyle(paint, scope), softWrap = false, maxLines = 1)
        val left = when (paint.textAlign) { Paint.Align.CENTER -> x - layout.size.width / 2f; Paint.Align.RIGHT -> x - layout.size.width; else -> x }
        scope.drawText(layout, topLeft = Offset(left, y - layout.firstBaseline))
    }
    override fun drawColor(color: Int) { scope.drawRect(composeColor(color)) }
    override fun save(): Int { scope.drawContext.canvas.save(); return 0 }
    override fun restore() { scope.drawContext.canvas.restore() }
    override fun translate(dx: Float, dy: Float) { scope.drawContext.canvas.translate(dx, dy) }
    override fun rotate(degrees: Float) { scope.drawContext.canvas.rotate(degrees) }
    override fun scale(sx: Float, sy: Float) { scope.drawContext.canvas.scale(sx, sy) }
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean { scope.drawContext.canvas.clipRect(left, top, right, bottom); return true }
}

// ---- Layout ----

private fun visibleChildren(group: ViewGroup): List<View> {
    val result = ArrayList<View>(group.childCount)
    for (index in 0 until group.childCount) { val child = group.getChildAt(index); if (child != null && child.visibility != View.GONE) result.add(child) }
    return result
}

/** Constraint for one axis from a LayoutParams size: exact pixels, exactly the space (when [exact]) or up to it. */
private fun axis(spec: Int, available: Int, exact: Boolean): IntArray = when {
    spec >= 0 -> intArrayOf(spec, spec)
    spec == MATCH && exact && available != UNBOUNDED -> intArrayOf(available, available)
    else -> intArrayOf(0, available)
}
private fun constraintsOf(width: IntArray, height: IntArray): Constraints =
    Constraints(minWidth = width[0], maxWidth = max(width[0], width[1]), minHeight = height[0], maxHeight = max(height[0], height[1]))
private fun shrink(value: Int, by: Int): Int = if (value == UNBOUNDED) UNBOUNDED else max(0, value - by)

/** A view with one optional piece of content: padding, minimum size and gravity as on Android. */
private class ContentPolicy(private val view: View, private val gravity: Int, private val fillWidth: Boolean, private val fillHeight: Boolean) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val padH = view.paddingLeft + view.paddingRight; val padV = view.paddingTop + view.paddingBottom
        val innerMaxW = shrink(constraints.maxWidth, padH); val innerMaxH = shrink(constraints.maxHeight, padV)
        val minW = if (constraints.hasFixedWidth && fillWidth) innerMaxW else 0
        val minH = if (constraints.hasFixedHeight && fillHeight) innerMaxH else 0
        val placeables = measurables.map { it.measure(Constraints(minW, max(minW, innerMaxW), minH, max(minH, innerMaxH))) }
        val contentW = placeables.maxOfOrNull { it.width } ?: 0; val contentH = placeables.maxOfOrNull { it.height } ?: 0
        val width = max(contentW + padH, view.minimumWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = max(contentH + padV, view.minimumHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        val rtl = layoutDirection == LayoutDirection.Rtl
        return layout(width, height) {
            for (placeable in placeables) {
                val x = view.paddingLeft + align(horizontalBias(gravity, rtl), width - padH - placeable.width)
                val y = view.paddingTop + align(verticalBias(gravity), height - padV - placeable.height)
                placeable.place(x, y)
            }
        }
    }
}

private class FramePolicy(private val view: ViewGroup, private val children: List<View>, private val overlays: Int) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val padH = view.paddingLeft + view.paddingRight; val padV = view.paddingTop + view.paddingBottom
        val availW = shrink(constraints.maxWidth, padH); val availH = shrink(constraints.maxHeight, padV)
        val count = children.size
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var contentW = 0; var contentH = 0
        fun params(index: Int) = children[index].layoutParams
        fun marginH(index: Int) = (params(index) as? ViewGroup.MarginLayoutParams)?.let { it.leftMargin + it.rightMargin } ?: 0
        fun marginV(index: Int) = (params(index) as? ViewGroup.MarginLayoutParams)?.let { it.topMargin + it.bottomMargin } ?: 0
        // Children that match a parent of unknown size are measured last, against the largest sibling.
        val deferred = ArrayList<Int>()
        for (index in 0 until count) {
            val lp = params(index); val w = lp?.width ?: MATCH; val h = lp?.height ?: MATCH
            if ((w == MATCH && !constraints.hasFixedWidth) || (h == MATCH && !constraints.hasFixedHeight)) { deferred.add(index); continue }
            val placeable = measurables[index].measure(constraintsOf(axis(w, shrink(availW, marginH(index)), true), axis(h, shrink(availH, marginV(index)), true)))
            placeables[index] = placeable
            contentW = max(contentW, placeable.width + marginH(index)); contentH = max(contentH, placeable.height + marginV(index))
        }
        for (index in deferred) {
            val lp = params(index); val w = lp?.width ?: MATCH; val h = lp?.height ?: MATCH
            val widthAxis = if (w == MATCH && !constraints.hasFixedWidth) (if (contentW > 0) intArrayOf(contentW - marginH(index), contentW - marginH(index)) else intArrayOf(0, shrink(availW, marginH(index)))) else axis(w, shrink(availW, marginH(index)), true)
            val heightAxis = if (h == MATCH && !constraints.hasFixedHeight) (if (contentH > 0) intArrayOf(contentH - marginV(index), contentH - marginV(index)) else intArrayOf(0, shrink(availH, marginV(index)))) else axis(h, shrink(availH, marginV(index)), true)
            val placeable = measurables[index].measure(constraintsOf(widthAxis, heightAxis))
            placeables[index] = placeable
            contentW = max(contentW, placeable.width + marginH(index)); contentH = max(contentH, placeable.height + marginV(index))
        }
        val width = max(contentW + padH, view.minimumWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = max(contentH + padV, view.minimumHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        // Overlay views cover the whole frame.
        for (index in count until count + overlays) placeables[index] = measurables[index].measure(Constraints.fixed(width, height))
        val rtl = layoutDirection == LayoutDirection.Rtl
        return layout(width, height) {
            for (index in 0 until count) {
                val placeable = placeables[index] ?: continue
                val lp = params(index); val margins = lp as? ViewGroup.MarginLayoutParams
                var gravity = (lp as? FrameLayout.LayoutParams)?.gravity ?: -1
                if (gravity == -1) gravity = Gravity.TOP or Gravity.START
                val left = view.paddingLeft + (margins?.leftMargin ?: 0); val top = view.paddingTop + (margins?.topMargin ?: 0)
                val spaceW = width - padH - marginH(index) - placeable.width; val spaceH = height - padV - marginV(index) - placeable.height
                placeable.place(left + align(horizontalBias(gravity, rtl), spaceW), top + align(verticalBias(gravity), spaceH))
            }
            for (index in count until count + overlays) placeables[index]?.place(0, 0)
        }
    }
}

private class LinearPolicy(private val view: LinearLayout, private val children: List<View>) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val vertical = view.orientation == LinearLayout.VERTICAL
        val padH = view.paddingLeft + view.paddingRight; val padV = view.paddingTop + view.paddingBottom
        val availW = shrink(constraints.maxWidth, padH); val availH = shrink(constraints.maxHeight, padV)
        val count = children.size
        val placeables = arrayOfNulls<Placeable>(count)
        fun lp(index: Int) = children[index].layoutParams
        fun margins(index: Int) = lp(index) as? ViewGroup.MarginLayoutParams
        fun marginH(index: Int) = margins(index)?.let { it.leftMargin + it.rightMargin } ?: 0
        fun marginV(index: Int) = margins(index)?.let { it.topMargin + it.bottomMargin } ?: 0
        fun weight(index: Int) = (lp(index) as? LinearLayout.LayoutParams)?.weight ?: 0f
        fun widthSpec(index: Int) = lp(index)?.width ?: if (vertical) MATCH else WRAP
        fun heightSpec(index: Int) = lp(index)?.height ?: WRAP

        var used = 0          // space taken along the main axis, margins included
        var cross = 0         // largest child across it, margins included
        var totalWeight = 0f
        var weightedMargins = 0
        val weighted = ArrayList<Int>()
        val crossDeferred = ArrayList<Int>()   // children that match an unknown cross size
        val mainFixed = if (vertical) constraints.hasFixedHeight else constraints.hasFixedWidth
        val crossFixed = if (vertical) constraints.hasFixedWidth else constraints.hasFixedHeight
        val mainAvail = if (vertical) availH else availW
        val crossAvail = if (vertical) availW else availH

        fun measureChild(index: Int, mainAxis: IntArray, crossAxis: IntArray) {
            val placeable = measurables[index].measure(if (vertical) constraintsOf(crossAxis, mainAxis) else constraintsOf(mainAxis, crossAxis))
            placeables[index] = placeable
            used += (if (vertical) placeable.height + marginV(index) else placeable.width + marginH(index))
            cross = max(cross, if (vertical) placeable.width + marginH(index) else placeable.height + marginV(index))
        }
        fun crossAxisOf(index: Int): IntArray? {
            val spec = if (vertical) widthSpec(index) else heightSpec(index)
            val margin = if (vertical) marginH(index) else marginV(index)
            if (spec == MATCH && !crossFixed) return null
            return axis(spec, shrink(crossAvail, margin), true)
        }
        fun resolvedCross(index: Int): IntArray {
            val margin = if (vertical) marginH(index) else marginV(index)
            return if (cross > 0) intArrayOf(max(0, cross - margin), max(0, cross - margin)) else intArrayOf(0, shrink(crossAvail, margin))
        }

        for (index in 0 until count) {
            val w = weight(index)
            if (w > 0f && mainAvail != UNBOUNDED) { weighted.add(index); totalWeight += w; weightedMargins += if (vertical) marginV(index) else marginH(index); continue }
            val crossAxis = crossAxisOf(index)
            if (crossAxis == null) { crossDeferred.add(index); continue }
            val mainSpec = if (vertical) heightSpec(index) else widthSpec(index)
            val mainMargin = if (vertical) marginV(index) else marginH(index)
            val remaining = shrink(if (mainAvail == UNBOUNDED) UNBOUNDED else max(0, mainAvail - used), mainMargin)
            measureChild(index, axis(mainSpec, remaining, mainFixed), crossAxis)
        }
        if (crossDeferred.isNotEmpty() && cross == 0) {
            // Nothing else decides the cross size: ask the children what they would like.
            for (index in crossDeferred) {
                val margin = if (vertical) marginH(index) else marginV(index)
                val wanted = if (vertical) measurables[index].maxIntrinsicWidth(UNBOUNDED) else measurables[index].maxIntrinsicHeight(UNBOUNDED)
                cross = max(cross, min(wanted, shrink(crossAvail, margin)) + margin)
            }
        }
        for (index in crossDeferred) {
            val mainSpec = if (vertical) heightSpec(index) else widthSpec(index)
            val mainMargin = if (vertical) marginV(index) else marginH(index)
            val remaining = shrink(if (mainAvail == UNBOUNDED) UNBOUNDED else max(0, mainAvail - used), mainMargin)
            measureChild(index, axis(mainSpec, remaining, mainFixed), resolvedCross(index))
        }
        // Weighted children share what is left; in a wrapping parent they may take up to their share.
        var leftover = if (mainAvail == UNBOUNDED) 0 else max(0, mainAvail - used - weightedMargins)
        var weightLeft = totalWeight
        for (index in weighted) {
            val share = if (weightLeft > 0f) (leftover * (weight(index) / weightLeft)).roundToInt() else 0
            weightLeft -= weight(index)
            val before = used
            measureChild(index, if (mainFixed) intArrayOf(share, share) else intArrayOf(0, share), crossAxisOf(index) ?: resolvedCross(index))
            leftover = max(0, leftover - (used - before - (if (vertical) marginV(index) else marginH(index))))
        }

        val contentMain = used; val contentCross = cross
        val width = max((if (vertical) contentCross else contentMain) + padH, view.minimumWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = max((if (vertical) contentMain else contentCross) + padV, view.minimumHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        val rtl = layoutDirection == LayoutDirection.Rtl
        return layout(width, height) {
            if (vertical) {
                var y = view.paddingTop + align(verticalBias(view.gravity), max(0, height - padV - contentMain))
                for (index in 0 until count) {
                    val placeable = placeables[index] ?: continue
                    val m = margins(index)
                    var gravity = (lp(index) as? LinearLayout.LayoutParams)?.gravity ?: -1
                    if (gravity == -1) gravity = view.gravity
                    val space = width - padH - marginH(index) - placeable.width
                    y += m?.topMargin ?: 0
                    placeable.place(view.paddingLeft + (m?.leftMargin ?: 0) + align(horizontalBias(gravity, rtl), space), y)
                    y += placeable.height + (m?.bottomMargin ?: 0)
                }
            } else {
                val free = max(0, width - padH - contentMain)
                var x = view.paddingLeft + align(horizontalBias(view.gravity, rtl), free)
                val order = if (rtl) (count - 1 downTo 0) else (0 until count)
                for (index in order) {
                    val placeable = placeables[index] ?: continue
                    val m = margins(index)
                    var gravity = (lp(index) as? LinearLayout.LayoutParams)?.gravity ?: -1
                    if (gravity == -1) gravity = view.gravity
                    val space = height - padV - marginV(index) - placeable.height
                    x += m?.leftMargin ?: 0
                    placeable.place(x, view.paddingTop + (m?.topMargin ?: 0) + align(verticalBias(gravity), space))
                    x += placeable.width + (m?.rightMargin ?: 0)
                }
            }
        }
    }
}

/** The scrolled content of a ScrollView: as wide as the viewport, as tall as it needs. */
private class ScrollContentPolicy(private val fill: Boolean, private val horizontal: Boolean) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val child = if (horizontal)
            Constraints(minWidth = if (fill) constraints.minWidth else 0, maxWidth = UNBOUNDED, minHeight = if (constraints.hasBoundedHeight) constraints.minHeight else 0, maxHeight = constraints.maxHeight)
        else
            Constraints(minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0, maxWidth = constraints.maxWidth, minHeight = if (fill) constraints.minHeight else 0, maxHeight = UNBOUNDED)
        val placeables = measurables.map { it.measure(child) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtLeast(constraints.minWidth)
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceAtLeast(constraints.minHeight)
        return layout(width, height) { placeables.forEach { it.place(0, 0) } }
    }
}

// ---- Text ----

private fun textAlignOf(view: TextView, rtl: Boolean): TextAlign = when (view.textAlignment) {
    View.TEXT_ALIGNMENT_CENTER -> TextAlign.Center
    View.TEXT_ALIGNMENT_VIEW_START, View.TEXT_ALIGNMENT_TEXT_START -> TextAlign.Start
    View.TEXT_ALIGNMENT_VIEW_END, View.TEXT_ALIGNMENT_TEXT_END -> TextAlign.End
    else -> when (horizontalBias(view.gravity, rtl)) { 0 -> TextAlign.Center; 1 -> TextAlign.Right; else -> TextAlign.Left }
}

private fun textStyleOf(view: TextView, rtl: Boolean, color: Int): TextStyle {
    val face = view.typeface
    return TextStyle(
        color = composeColor(color).let { if (view.isEnabled) it else it.copy(alpha = it.alpha * 0.4f) },
        fontSize = view.textSizeSp.sp,
        fontWeight = FontWeight(face?.weight ?: 400),
        fontStyle = if (face?.isItalic == true) FontStyle.Italic else FontStyle.Normal,
        fontFamily = if (face?.isMonospace == true) FontFamily.Monospace else null,
        textAlign = textAlignOf(view, rtl),
        textDirection = when (view.textDirection) {
            View.TEXT_DIRECTION_LTR -> TextDirection.Ltr
            View.TEXT_DIRECTION_RTL -> TextDirection.Rtl
            View.TEXT_DIRECTION_FIRST_STRONG_RTL -> TextDirection.ContentOrRtl
            View.TEXT_DIRECTION_FIRST_STRONG_LTR -> TextDirection.ContentOrLtr
            else -> TextDirection.Content
        },
        letterSpacing = if (view.letterSpacing != 0f) view.letterSpacing.em else androidx.compose.ui.unit.TextUnit.Unspecified,
    )
}

@Composable
private fun TextContent(view: TextView, rtl: Boolean) {
    val value = view.getText().toString()
    val showHint = value.isEmpty() && !view.hint.isNullOrEmpty()
    val color = if (showHint) (if (view.hintColor != 0) view.hintColor else ViewTheme.muted) else if (view.textColorSet) view.textColor else ViewTheme.ink
    BasicText(
        text = if (showHint) view.hint.toString() else if (view.isAllCaps) value.uppercase() else value,
        style = textStyleOf(view, rtl, color),
        maxLines = view.maxLines.coerceAtLeast(1),
        minLines = view.minLines.coerceIn(1, view.maxLines.coerceAtLeast(1)),
        softWrap = !view.singleLine,
        overflow = if (view.ellipsize != null) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

@Composable
private fun EditContent(view: EditText, rtl: Boolean) {
    val model = view.getText().toString()
    var field by remember(view) { mutableStateOf(TextFieldValue(model, TextRange(view.selectionStartIndex.coerceIn(0, model.length), view.selectionEndIndex.coerceIn(0, model.length)))) }
    if (field.text != model) field = TextFieldValue(model, TextRange(model.length))
    val selectionRequest = view.selectionRequest
    LaunchedEffect(view, selectionRequest) {
        if (selectionRequest > 0) field = field.copy(selection = TextRange(view.selectionStartIndex.coerceIn(0, field.text.length), view.selectionEndIndex.coerceIn(0, field.text.length)))
    }
    val requester = remember(view) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val focusRequest = view.rendererFocusRequest
    LaunchedEffect(view, focusRequest) {
        if (focusRequest > 0) runCatching { requester.requestFocus() } else if (focusRequest < 0 && view.hasFocus()) focusManager.clearFocus()
    }
    val type = view.inputType
    val kind = type and InputType.TYPE_MASK_CLASS
    val multiLine = type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 && kind == InputType.TYPE_CLASS_TEXT
    val keyboard = KeyboardOptions(
        keyboardType = when {
            kind == InputType.TYPE_CLASS_NUMBER && type and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0 -> KeyboardType.Decimal
            kind == InputType.TYPE_CLASS_NUMBER -> KeyboardType.Number
            kind == InputType.TYPE_CLASS_PHONE -> KeyboardType.Phone
            else -> KeyboardType.Text
        },
        capitalization = if (kind == InputType.TYPE_CLASS_TEXT && type and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        autoCorrectEnabled = type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0 && kind == InputType.TYPE_CLASS_TEXT,
        imeAction = if (multiLine) ImeAction.Default else ImeAction.Done,
    )
    val ink = if (view.textColorSet) view.textColor else ViewTheme.ink
    val style = textStyleOf(view, rtl, ink)
    BasicTextField(
        value = field,
        onValueChange = { next ->
            val changed = next.text != view.getText().toString()
            field = next
            view.selectionStartIndex = next.selection.start; view.selectionEndIndex = next.selection.end
            if (changed) { if (view is AutoCompleteTextView) view.rendererTypedSuggest(next.text) else view.rendererTyped(next.text) }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { state ->
            view.rendererFocus(state.isFocused)
            if (!state.isFocused && view is AutoCompleteTextView) view.dismissDropDown()
        },
        enabled = view.isEnabled,
        textStyle = style,
        keyboardOptions = keyboard,
        singleLine = !multiLine && (view.singleLine || view.maxLines == 1),
        maxLines = if (multiLine) view.maxLines.coerceAtLeast(1) else 1,
        minLines = if (multiLine) view.minLines.coerceIn(1, view.maxLines.coerceAtLeast(1)) else 1,
        cursorBrush = SolidColor(composeColor(ViewTheme.accent)),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (field.text.isEmpty() && !view.hint.isNullOrEmpty())
                    BasicText(view.hint.toString(), Modifier.fillMaxWidth(), style = style.copy(color = composeColor(if (view.hintColor != 0) view.hintColor else ViewTheme.muted)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
    if (view is AutoCompleteTextView) SuggestionPopup(view)
}

/** Rows of an adapter that match the typed text the way Android's ArrayAdapter filter does: by prefix of any word. */
private fun matchingRows(view: AutoCompleteTextView): List<Int> {
    val adapter = view.suggestionAdapter ?: return emptyList()
    val typed = view.getText().toString().trim().lowercase()
    val result = ArrayList<Int>()
    for (index in 0 until adapter.count) {
        val item = adapter.getItem(index)?.toString() ?: continue
        if (view.popupAll || typed.isEmpty()) { result.add(index); continue }
        val lower = item.lowercase()
        if (lower == typed && !view.popupAll) continue
        if (lower.startsWith(typed) || lower.split(' ').any { it.startsWith(typed) }) result.add(index)
    }
    return result
}

@Composable
private fun SuggestionPopup(view: AutoCompleteTextView) {
    if (!view.popupOpen) return
    val adapter = view.suggestionAdapter ?: return
    val rows = matchingRows(view)
    if (rows.isEmpty()) return
    val density = LocalDensity.current
    val anchorWidth = if (view.dropDownWidth > 0) view.dropDownWidth else (view.parent?.width ?: view.width)
    val offsetY = view.height + view.paddingBottom
    Popup(alignment = Alignment.TopStart, offset = IntOffset(0, offsetY), onDismissRequest = { view.dismissDropDown() }, properties = PopupProperties(focusable = false)) {
        val shape = RoundedCornerShape(with(density) { 12.dp.toPx() })
        Column(
            Modifier.width(with(density) { max(anchorWidth, 1).toDp() }).heightIn(max = 240.dp).shadow(6.dp, shape)
                .drawBehind { paintDrawable(view.dropDownBackground ?: ColorDrawable(ViewTheme.surface)) }.clip(shape).verticalScroll(rememberScrollState())
        ) {
            for (row in rows) {
                val item = adapter.getItem(row)?.toString() ?: ""
                val rowView = remember(adapter, row) { adapter.getView(row, null, null) }
                Box(Modifier.fillMaxWidth().clickable { view.rendererChose(row, item) }) { if (rowView != null) RenderView(rowView, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun SpinnerContent(view: Spinner) {
    var open by remember(view) { mutableStateOf(false) }
    val adapter = view.adapter
    val position = view.selectedItemPosition
    val density = LocalDensity.current
    val selected = remember(adapter, position, view.count) { if (adapter != null && position in 0 until adapter.count) adapter.getView(position, null, view) else null }
    Row(Modifier.fillMaxSize().clickable(enabled = view.isEnabled) { if ((adapter?.count ?: 0) > 0) open = true }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { if (selected != null) RenderView(selected, Modifier.fillMaxWidth()) }
        BasicText("▾", Modifier.width(36.dp), style = TextStyle(color = composeColor(ViewTheme.muted), fontSize = 18.sp, textAlign = TextAlign.Center))
    }
    if (open && adapter != null) {
        Popup(alignment = Alignment.TopStart, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
            val shape = RoundedCornerShape(with(density) { 12.dp.toPx() })
            Column(
                Modifier.width(with(density) { max(view.width, 1).toDp() }).heightIn(max = 360.dp).shadow(6.dp, shape)
                    .drawBehind { paintDrawable(view.popupBackground ?: ColorDrawable(ViewTheme.surface)) }.clip(shape).verticalScroll(rememberScrollState())
            ) {
                for (row in 0 until adapter.count) {
                    val rowView = remember(adapter, row) { adapter.getDropDownView(row, null, view) }
                    Box(Modifier.fillMaxWidth().clickable { open = false; view.rendererPicked(row) }) { if (rowView != null) RenderView(rowView, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun CompoundContent(view: CompoundButton, rtl: Boolean) {
    val accent = composeColor(view.buttonTintList?.defaultColor ?: ViewTheme.accent)
    val idle = composeColor(ViewTheme.muted)
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (view) {
            is RadioButton -> androidx.compose.material3.RadioButton(selected = view.isChecked, onClick = null, enabled = view.isEnabled, colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = idle))
            is android.widget.Switch -> Switch(checked = view.isChecked, onCheckedChange = null, enabled = view.isEnabled)
            else -> Checkbox(checked = view.isChecked, onCheckedChange = null, enabled = view.isEnabled, colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = idle))
        }
        Spacer(Modifier.width(10.dp))
        TextContent(view, rtl)
    }
}

@Composable
private fun ProgressContent(view: ProgressBar) {
    val accent = composeColor(view.progressTintList?.defaultColor ?: ViewTheme.accent)
    val track = composeColor(view.progressBackgroundTintList?.defaultColor ?: ViewTheme.outline)
    val fraction = if (view.max > 0) (view.progress.toFloat() / view.max).coerceIn(0f, 1f) else 0f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val filled = size.width * fraction
        if (filled > 0f) drawRoundRect(accent, topLeft = Offset(if (rtl) size.width - filled else 0f, 0f), size = Size(filled, size.height), cornerRadius = radius)
    }
}

// ---- Input for app subclasses ----

/**
 * Feeds pointer events to a view's dispatchTouchEvent. A ViewGroup subclass sees them before its
 * children ([intercept]) and takes them over by returning true without calling through to the base class.
 */
private fun Modifier.touchEvents(view: View, intercept: Boolean): Modifier = pointerInput(view) {
    awaitPointerEventScope {
        var pressed = false
        var start = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(if (intercept) PointerEventPass.Initial else PointerEventPass.Main)
            val change = event.changes.firstOrNull() ?: continue
            val down = event.changes.count { it.pressed }
            var action = when {
                event.changes.any { it.changedToDown() } -> if (down <= 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN
                event.changes.any { it.changedToUp() } -> if (down == 0) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP
                down > 0 -> MotionEvent.ACTION_MOVE
                else -> -1
            }
            if (action == -1) continue
            if (action == MotionEvent.ACTION_DOWN) { pressed = true; start = change.position }
            if (!pressed) continue
            // A plain view lets go once the finger travels, so that a scrolling parent can take the gesture.
            if (!intercept && action != MotionEvent.ACTION_DOWN && (change.isConsumed || (change.position - start).getDistance() > viewConfiguration.touchSlop)) action = MotionEvent.ACTION_CANCEL
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pressed = false
            val motion = MotionEvent(action, change.position.x, change.position.y, view.windowX + change.position.x, view.windowY + change.position.y, change.uptimeMillis)
            val handled = view.dispatchTouchEvent(motion)
            if (intercept) { if (handled && !motion.reachedBase) event.changes.forEach { it.consume() } }
            else if (handled && action == MotionEvent.ACTION_UP) event.changes.forEach { it.consume() }
        }
    }
}

// ---- One view ----

@Composable
fun RenderView(view: View, modifier: Modifier = Modifier) {
    val tick = remember(view) { mutableIntStateOf(0) }
    val layerTick = remember(view) { mutableIntStateOf(0) }
    val composedVersion = view.version
    DisposableEffect(view) {
        view.rendererObserver = { tick.intValue++ }
        view.rendererLayerObserver = { layerTick.intValue++ }
        if (view.version != composedVersion) tick.intValue++
        view.rendererAttached(true)
        onDispose { view.rendererObserver = null; view.rendererLayerObserver = null; view.rendererAttached(false) }
    }
    tick.intValue   // recompose whenever the view reports a change

    val inherited = LocalLayoutDirection.current
    val direction = when (view.layoutDirection) { View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl; View.LAYOUT_DIRECTION_LTR -> LayoutDirection.Ltr; else -> inherited }
    val rtl = direction == LayoutDirection.Rtl
    val density = LocalDensity.current

    val insetsListener = view.insetsListener
    if (insetsListener != null) {
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val left = insets.getLeft(density, direction); val top = insets.getTop(density); val right = insets.getRight(density, direction); val bottom = insets.getBottom(density)
        remember(view, insetsListener, left, top, right, bottom) { insetsListener.onApplyWindowInsets(view, android.view.WindowInsets(left, top, right, bottom)); 0 }
    }

    val background = view.background
    val shape = backgroundShapeOf(background)
    val click = view.rendererClick; val longClick = view.rendererLongClick
    val custom = !isFrameworkClass(view)

    var chain = modifier
        .graphicsLayer {
            layerTick.intValue
            alpha = if (view.visibility == View.INVISIBLE) 0f else view.alpha
            translationX = view.translationX; translationY = view.translationY
            scaleX = view.scaleX; scaleY = view.scaleY; rotationZ = view.rotation
        }
        .onGloballyPositioned { coordinates ->
            val inParent = coordinates.positionInParent(); val inRoot = coordinates.positionInRoot()
            view.rendererPlaced(inParent.x.roundToInt(), inParent.y.roundToInt(), coordinates.size.width, coordinates.size.height, inRoot.x.roundToInt(), inRoot.y.roundToInt())
        }
    if (view.elevation > 0f) chain = chain.shadow(with(density) { view.elevation.toDp() }, shape)
    if (background != null) chain = chain.drawBehind { paintDrawable(background) }
    if (view.clipToOutline || (view is ViewGroup && view.clipChildren && custom) || ((click != null || view.isClickable) && shape != RectangleShape)) chain = chain.clip(shape)
    if (custom && view is ViewGroup) chain = chain.touchEvents(view, intercept = true)
    if (view !is Spinner && (click != null || longClick != null || view.isClickable)) {
        val source = remember(view) { MutableInteractionSource() }
        chain = chain.combinedClickable(
            interactionSource = source,
            indication = if (click != null || view is CompoundButton) LocalIndication.current else null,
            enabled = view.isEnabled,
            onLongClick = if (longClick != null) { { view.performLongClick(); Unit } } else null,
            onClick = { view.performClick(); Unit },
        )
    }
    if ((custom && view !is ViewGroup) || view.rendererTouch != null) chain = chain.touchEvents(view, intercept = false)

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        when (view) {
            is ScrollView -> {
                val state = rememberScrollState(view.scrollY)
                val request = view.scrollRequest
                LaunchedEffect(view, request) { if (request > 0) { if (view.scrollAnimated) state.animateScrollTo(view.scrollTarget.coerceAtLeast(0)) else state.scrollTo(view.scrollTarget.coerceAtLeast(0)) } }
                LaunchedEffect(view, state) { snapshotFlow { state.value to state.maxValue }.collect { (value, limit) -> view.maxScroll = limit; view.rendererScrolled(0, value) } }
                val children = visibleChildren(view)
                Layout(content = {
                    Layout(content = { children.forEach { key(it) { RenderView(it) } } }, modifier = Modifier.verticalScroll(state), measurePolicy = ScrollContentPolicy(view.isFillViewport, false))
                }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.TOP or Gravity.START, fillWidth = true, fillHeight = true))
            }
            is HorizontalScrollView -> {
                val state = rememberScrollState()
                val request = view.scrollRequest
                LaunchedEffect(view, request) { if (request > 0) state.scrollTo(view.scrollTarget.coerceAtLeast(0)) }
                val children = visibleChildren(view)
                Layout(content = {
                    Layout(content = { children.forEach { key(it) { RenderView(it) } } }, modifier = Modifier.horizontalScroll(state), measurePolicy = ScrollContentPolicy(view.isFillViewport, true))
                }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.TOP or Gravity.START, fillWidth = true, fillHeight = true))
            }
            is Spinner -> Layout(content = { SpinnerContent(view) }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER_VERTICAL or Gravity.START, fillWidth = true, fillHeight = true))
            is LinearLayout -> {
                val children = visibleChildren(view)
                Layout(content = { children.forEach { key(it) { RenderView(it) } } }, modifier = chain, measurePolicy = LinearPolicy(view, children))
            }
            is ViewGroup -> {
                val children = visibleChildren(view)
                val overlays = view.overlay.views.toList()
                Layout(content = {
                    children.forEach { key(it) { RenderView(it) } }
                    overlays.forEach { key(it) { RenderView(it) } }
                }, modifier = chain, measurePolicy = FramePolicy(view, children, overlays.size))
            }
            is EditText -> Layout(content = { EditContent(view, rtl) }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER_VERTICAL or Gravity.START, fillWidth = true, fillHeight = false))
            is CompoundButton -> Layout(content = { CompoundContent(view, rtl) }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER_VERTICAL or Gravity.START, fillWidth = false, fillHeight = false))
            is TextView -> Layout(content = { TextContent(view, rtl) }, modifier = chain, measurePolicy = ContentPolicy(view, view.gravity, fillWidth = true, fillHeight = false))
            is ImageView -> Layout(content = {
                val id = view.imageResource
                if (id != 0) Image(painterResource(id), view.contentDescription?.toString(), Modifier.fillMaxSize(), contentScale = if (view.scaleType == ImageView.ScaleType.CENTER_CROP) ContentScale.Crop else ContentScale.Fit, alpha = view.imageAlpha / 255f, colorFilter = view.tint?.let { ColorFilter.tint(composeColor(it)) })
            }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER, fillWidth = true, fillHeight = true))
            is ProgressBar -> Layout(content = { ProgressContent(view) }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER, fillWidth = true, fillHeight = false))
            else -> {
                if (custom) {
                    val measurer = rememberTextMeasurer()
                    val version = view.version
                    Layout(content = { Canvas(Modifier.fillMaxSize()) { if (version >= 0) view.rendererDraw(ComposeCanvas(this, measurer)) } }, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER, fillWidth = true, fillHeight = true))
                } else Layout(content = {}, modifier = chain, measurePolicy = ContentPolicy(view, Gravity.CENTER, fillWidth = false, fillHeight = false))
            }
        }
    }
}
