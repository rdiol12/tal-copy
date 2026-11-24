package android.animation

import android.os.MainQueue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import kotlin.jvm.JvmStatic


open class Animator {
    interface AnimatorListener {
        fun onAnimationStart(animation: Animator?) {}
        fun onAnimationEnd(animation: Animator?) {}
        fun onAnimationCancel(animation: Animator?) {}
        fun onAnimationRepeat(animation: Animator?) {}
    }
    protected val listeners = ArrayList<AnimatorListener>()
    fun addListener(listener: AnimatorListener?) { if (listener != null) listeners.add(listener) }
    fun removeListener(listener: AnimatorListener?) { listeners.remove(listener) }
    fun removeAllListeners() { listeners.clear() }
    open fun start() {}
    open fun cancel() {}
    open fun end() {}
    open val isRunning: Boolean get() = false
    open val isStarted: Boolean get() = isRunning
}

abstract class AnimatorListenerAdapter : Animator.AnimatorListener

/** Animates a float (or int) between the given values on the frame clock. */
class ValueAnimator private constructor(private val values: FloatArray, private val integers: Boolean) : Animator() {
    fun interface AnimatorUpdateListener { fun onAnimationUpdate(animation: ValueAnimator?) }
    private val updates = ArrayList<AnimatorUpdateListener>()
    private var durationMillis = 300L
    private var delayMillis = 0L
    private var easing: Interpolator? = AccelerateDecelerateInterpolator()
    private var running = false
    private var generation = 0
    var animatedFraction: Float = 0f
        private set
    var animatedValue: Any? = if (integers) values[0].toInt() else values[0]
        private set

    fun addUpdateListener(listener: AnimatorUpdateListener?) { if (listener != null) updates.add(listener) }
    fun removeAllUpdateListeners() { updates.clear() }
    fun setDuration(millis: Long): ValueAnimator { durationMillis = millis; return this }
    val duration: Long get() = durationMillis
    var startDelay: Long
        get() = delayMillis
        set(value) { delayMillis = value }
    fun setInterpolator(value: Interpolator?) { easing = value }
    var repeatCount: Int = 0
    var repeatMode: Int = 1
    override val isRunning: Boolean get() = running

    private fun apply(fraction: Float) {
        animatedFraction = fraction
        val eased = easing?.getInterpolation(fraction) ?: fraction
        val value = if (values.size == 1) values[0] else {
            val span = values.size - 1
            val index = minOf((eased * span).toInt().coerceAtLeast(0), span - 1)
            val local = eased * span - index
            values[index] + (values[index + 1] - values[index]) * local
        }
        animatedValue = if (integers) value.toInt() else value
        updates.toList().forEach { it.onAnimationUpdate(this) }
    }

    override fun start() {
        val run = ++generation
        running = true
        listeners.toList().forEach { it.onAnimationStart(this) }
        if (!enabled || durationMillis <= 0) { finish(); return }
        var startNanos = -1L
        MainQueue.eachFrame { nanos ->
            if (run != generation || !running) return@eachFrame false
            if (nanos == Long.MAX_VALUE) { finish(); return@eachFrame false }
            if (startNanos < 0) startNanos = nanos + delayMillis * 1_000_000L
            val fraction = ((nanos - startNanos).toFloat() / (durationMillis * 1_000_000f)).coerceIn(0f, 1f)
            if (fraction >= 1f) { finish(); false } else { apply(fraction); true }
        }
    }
    private fun finish() {
        apply(1f)
        running = false
        listeners.toList().forEach { it.onAnimationEnd(this) }
    }
    override fun cancel() {
        if (!running) return
        running = false; generation++
        listeners.toList().forEach { it.onAnimationCancel(this); it.onAnimationEnd(this) }
    }
    override fun end() { if (running) { generation++; finish() } }

    companion object {
        const val INFINITE = -1
        const val RESTART = 1
        const val REVERSE = 2
        /** Screenshot tests switch animations off so every frame is the final one. */
        var enabled: Boolean = true
        @JvmStatic fun areAnimatorsEnabled(): Boolean = enabled
        @JvmStatic fun ofFloat(vararg values: Float): ValueAnimator = ValueAnimator(values, false)
        @JvmStatic fun ofInt(vararg values: Int): ValueAnimator = ValueAnimator(FloatArray(values.size) { values[it].toFloat() }, true)
    }
}
