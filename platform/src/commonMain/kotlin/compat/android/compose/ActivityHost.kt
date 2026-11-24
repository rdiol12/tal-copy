// Runs an android.app.Activity inside Compose: creates it, drives its lifecycle, supplies the main-thread
// queue and frame clock, and draws its content view, dialogs, date pickers and toasts.
package compat.android.compose

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.Screen
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.TextMeasure
import android.os.Bundle
import android.os.MainQueue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DatePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compat.android.platformObserveForeground
import compat.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Labels and behaviour the host cannot take from the Activity itself. */
class ActivityHostOptions(
    val confirmLabel: String = "OK",
    val dismissLabel: String = "Cancel",
    /** Null follows the system; the app decides when it has its own LIGHT / DARK setting. */
    val darkTheme: (() -> Boolean)? = null,
)

private const val DAY_MILLIS = 86_400_000L

/**
 * Shows the Activity made by [create]. A new one is made (with the old one's saved state) when the
 * system switches between light and dark or the Activity asks to be recreated, as Android does.
 */
@Composable
fun ActivityHost(options: ActivityHostOptions = ActivityHostOptions(), create: () -> Activity) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val systemDark = isSystemInDarkTheme()
        val metrics = Resources.system.displayMetrics
        metrics.density = density.density
        metrics.scaledDensity = density.density * density.fontScale
        metrics.densityDpi = (160 * density.density).toInt()
        metrics.widthPixels = constraints.maxWidth
        metrics.heightPixels = constraints.maxHeight
        Resources.system.configuration.uiMode = if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO

        val scope = rememberCoroutineScope()
        val measurer = rememberTextMeasurer()
        var toast by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
        var toastSerial by remember { mutableIntStateOf(0) }
        val screenTick = remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        // Installed during composition so that work queued while the Activity is created has somewhere to go.
        remember {
            MainQueue.frames = { callback -> scope.launch { while (true) { val again = withFrameNanos { callback(it) }; if (!again) break } } }
            MainQueue.poster = { delayMillis, action ->
                val job = scope.launch { if (delayMillis > 0) delay(delayMillis); withFrameNanos { }; action() }
                val cancel: () -> Unit = { job.cancel() }
                cancel
            }
            TextMeasure.measurer = { text, paint -> measurer.measure(text, TextStyle(fontSize = with(density) { paint.textSize.toSp() }, fontWeight = FontWeight(paint.typeface?.weight ?: 400)), softWrap = false, maxLines = 1).size.width.toFloat() }
            Toast.presenter = { text, long -> toast = text to long; toastSerial++ }
            Screen.observer = { screenTick.intValue++ }
            Screen.recreate = { scope.launch { withFrameNanos { }; generation++ } }
            0
        }
        DisposableEffect(Unit) {
            onDispose { MainQueue.poster = null; MainQueue.frames = null; TextMeasure.measurer = null; Toast.presenter = null; Screen.observer = null; Screen.recreate = null }
        }

        val saved = remember { arrayOfNulls<Any>(2) }   // saved instance state, retained objects
        val activity = remember(systemDark, generation) {
            Screen.activity?.let { old ->
                saved[0] = old.hostSaveState(); saved[1] = old.onRetainNonConfigurationInstance()
                Screen.dialogs.toList().forEach { it.dismiss() }
                old.hostDestroy()
            }
            create().also {
                it.lastNonConfigurationInstance = saved[1]
                it.hostCreate(saved[0] as Bundle?)
                it.hostStart()
            }
        }
        DisposableEffect(Unit) {
            platformObserveForeground({ Screen.activity?.hostStart() }, { Screen.activity?.hostStop() })
            onDispose { }
        }

        screenTick.intValue
        val dark = options.darkTheme?.invoke() ?: systemDark
        val focusManager = LocalFocusManager.current
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
                BackHandler(enabled = true) { Screen.back() }
                val content = activity.contentView
                if (content != null) key(content) { RenderView(content, Modifier.fillMaxSize().imePadding()) }
                for (dialog in Screen.dialogs.toList()) key(dialog) { DialogLayer(dialog, options) }
                toast?.let { (text, long) ->
                    LaunchedEffect(toastSerial) { delay(if (long) 3500 else 2000); toast = null }
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 24.dp, end = 24.dp, bottom = 72.dp), contentAlignment = Alignment.BottomCenter) {
                        val shape = RoundedCornerShape(24.dp)
                        BasicText(text, Modifier.shadow(6.dp, shape).background(Color(if (dark) 0xFFE7EDF4 else 0xFF2B3340), shape).padding(horizontal = 20.dp, vertical = 12.dp),
                            style = TextStyle(color = Color(if (dark) 0xFF182333 else 0xFFFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogLayer(dialog: Dialog, options: ActivityHostOptions) {
    val window = dialog.window ?: return
    val density = LocalDensity.current
    val gravity = if (window.attributes.gravity != Gravity.CENTER) window.attributes.gravity else window.gravity
    val alignment = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
        Gravity.RIGHT -> if (gravity and 0x00800000 != 0) Alignment.CenterEnd else AbsoluteCenter.Right
        Gravity.LEFT -> if (gravity and 0x00800000 != 0) Alignment.CenterStart else AbsoluteCenter.Left
        else -> when (gravity and Gravity.VERTICAL_GRAVITY_MASK) { Gravity.BOTTOM -> Alignment.BottomCenter; Gravity.TOP -> Alignment.TopCenter; else -> Alignment.Center }
    }
    val dim = if (dialog is AlertDialog || dialog is DatePickerDialog) 0.5f else window.attributes.dimAmount
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)))
            .pointerInput(dialog) { detectTapGestures { if (dialog.cancelOnOutside) dialog.cancel() } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = alignment,
    ) {
        if (dialog is DatePickerDialog) { DatePickerLayer(dialog, options); return@Box }
        val content = window.content ?: return@Box
        val background = window.backgroundDrawable
        val shape = backgroundShapeOf(background)
        var box = Modifier.pointerInput(dialog) { detectTapGestures { } }
        box = when {
            window.layoutWidth > 0 -> box.width(with(density) { window.layoutWidth.toDp() })
            window.layoutWidth == ViewGroup.LayoutParams.MATCH_PARENT -> box.fillMaxWidth()
            dialog is AlertDialog -> box.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth()
            else -> box.padding(horizontal = 24.dp)
        }
        box = when {
            window.layoutHeight > 0 -> box.heightIn(max = with(density) { window.layoutHeight.toDp() })
            window.layoutHeight == ViewGroup.LayoutParams.MATCH_PARENT -> box.fillMaxHeight()
            else -> box.padding(vertical = 24.dp)
        }
        if (background != null) box = box.shadow(8.dp, shape).drawBehind { paintBackground(background) }.clip(shape)
        Box(box, propagateMinConstraints = true) { key(content) { RenderView(content) } }
    }
}

@Composable
private fun DatePickerLayer(dialog: DatePickerDialog, options: ActivityHostOptions) {
    val initial = remember(dialog) { runCatching { LocalDate.of(dialog.year, dialog.month + 1, dialog.dayOfMonth).toEpochDay() * DAY_MILLIS }.getOrNull() }
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    run {
        Surface(Modifier.padding(16.dp).widthIn(max = 420.dp).pointerInput(dialog) { detectTapGestures { } }, shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 8.dp) {
            androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 620.dp)) {
                Box(Modifier.weight(1f, fill = false)) { DatePicker(state = state, showModeToggle = false) }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = { dialog.cancel() }) { Text(options.dismissLabel) }
                    TextButton(onClick = {
                        val millis = state.selectedDateMillis
                        if (millis == null) dialog.cancel() else {
                            val date = LocalDate.ofEpochDay(millis.floorDiv(DAY_MILLIS))
                            dialog.rendererPicked(date.year, date.monthValue - 1, date.dayOfMonth)
                        }
                    }) { Text(options.confirmLabel) }
                }
            }
        }
    }
}

/** Left and right regardless of the layout direction. */
private object AbsoluteCenter {
    val Left: Alignment = androidx.compose.ui.BiasAbsoluteAlignment(-1f, 0f)
    val Right: Alignment = androidx.compose.ui.BiasAbsoluteAlignment(1f, 0f)
}

/** Screenshot tests switch animations off so that every frame shows the final state. */
fun disableViewAnimations() { ValueAnimator.enabled = false }
