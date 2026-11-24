package android.content.res

import android.graphics.drawable.Drawable
import android.graphics.drawable.ResourceDrawable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

class DisplayMetrics {
    @JvmField var density: Float = 1f
    @JvmField var scaledDensity: Float = 1f
    @JvmField var widthPixels: Int = 0
    @JvmField var heightPixels: Int = 0
    @JvmField var densityDpi: Int = 160
}

class Configuration {
    @JvmField var uiMode: Int = UI_MODE_NIGHT_NO
    @JvmField var orientation: Int = ORIENTATION_PORTRAIT
    @JvmField var screenWidthDp: Int = 0
    @JvmField var screenHeightDp: Int = 0
    @JvmField var fontScale: Float = 1f
    var layoutDirection: Int = 1
    companion object {
        const val UI_MODE_NIGHT_MASK = 48
        const val UI_MODE_NIGHT_NO = 16
        const val UI_MODE_NIGHT_YES = 32
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
}

/** Screen metrics and night mode are written by the renderer before the first frame. */
class Resources internal constructor() {
    val displayMetrics = DisplayMetrics()
    val configuration = Configuration()
    fun getDrawable(id: Int): Drawable = ResourceDrawable(id)
    fun getDrawable(id: Int, theme: Any?): Drawable = ResourceDrawable(id)
    fun getColor(id: Int): Int = 0
    fun getColor(id: Int, theme: Any?): Int = 0
    fun getDimensionPixelSize(id: Int): Int = 0
    fun getIdentifier(name: String?, type: String?, pkg: String?): Int = 0
    fun getString(id: Int): String = android.content.Context.stringResolver(id, emptyArray())
    companion object {
        @JvmField val system = Resources()
        @JvmStatic fun getSystem(): Resources = system
    }
}

class ColorStateList private constructor(val defaultColor: Int) {
    companion object { @JvmStatic fun valueOf(color: Int): ColorStateList = ColorStateList(color) }
}
