package android.view.animation

fun interface Interpolator { fun getInterpolation(input: Float): Float }

class LinearInterpolator : Interpolator { override fun getInterpolation(input: Float): Float = input }

class DecelerateInterpolator(private val factor: Float) : Interpolator {
    constructor() : this(1f)
    override fun getInterpolation(input: Float): Float =
        if (factor == 1f) 1f - (1f - input) * (1f - input) else (1.0 - kotlin.math.exp(2.0 * factor * kotlin.math.ln((1f - input).toDouble().coerceAtLeast(1e-9)))).toFloat()
}

class AccelerateDecelerateInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float = (kotlin.math.cos((input + 1) * kotlin.math.PI) / 2.0).toFloat() + 0.5f
}

/** Cubic Bezier easing from (0,0) to (1,1) with the two given control points. */
class PathInterpolator(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) : Interpolator {
    private fun bezier(t: Float, a: Float, b: Float): Float { val u = 1 - t; return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t }
    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f
        var low = 0f; var high = 1f
        repeat(24) { val mid = (low + high) / 2; if (bezier(mid, x1, x2) < input) low = mid else high = mid }
        return bezier((low + high) / 2, y1, y2)
    }
}
