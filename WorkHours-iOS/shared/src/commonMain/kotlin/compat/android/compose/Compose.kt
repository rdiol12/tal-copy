// Compose-side look-alikes of the Android APIs the screens use: LocalContext,
// painterResource(id), BackHandler, document pickers, and the imperative
// DatePickerDialog / TimePickerDialog / Toast (rendered by CompatHost).
package compat.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import compat.android.Context
import compat.android.Uri
import compat.android.platformImportFile
import compat.time.LocalDate
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource

val LocalContext = staticCompositionLocalOf { Context.app }

/** Installed by the app: maps an R.drawable id to its bundled resource. */
object CompatResources {
    var drawable: (Int) -> DrawableResource = { error("CompatResources.drawable is not installed") }
}

@Composable
fun painterResource(id: Int): Painter = org.jetbrains.compose.resources.painterResource(CompatResources.drawable(id))

/** On iOS this is driven by the edge-swipe "back" gesture. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.compose.ui.backhandler.BackHandler(enabled, onBack)
}

// ---- Document pickers ----

object ActivityResultContracts {
    class CreateDocument(val mimeType: String = "*/*")
    class OpenDocument
}

class ActivityResultLauncher<I> internal constructor(private val onLaunch: (I) -> Unit) {
    fun launch(input: I) = onLaunch(input)
}

/**
 * "Save as": the callback receives a Uri right away; whatever the app writes
 * to it is handed to the system save sheet when the stream is closed.
 */
@Composable
fun rememberLauncherForActivityResult(contract: ActivityResultContracts.CreateDocument, onResult: (Uri?) -> Unit): ActivityResultLauncher<String> {
    val callback by rememberUpdatedState(onResult)
    val mime = contract.mimeType
    return remember(mime) { ActivityResultLauncher<String> { name -> callback(Uri(name, mime, null)) } }
}

/** "Open": shows the system file picker and passes the picked file's content on. */
@Composable
fun rememberLauncherForActivityResult(contract: ActivityResultContracts.OpenDocument, onResult: (Uri?) -> Unit): ActivityResultLauncher<Array<String>> {
    val callback by rememberUpdatedState(onResult)
    return remember {
        ActivityResultLauncher<Array<String>> { types ->
            platformImportFile(types.toList()) { bytes -> callback(bytes?.let { Uri("import", "", it) }) }
        }
    }
}

// ---- Imperative dialogs ----

private object CompatUi {
    var datePicker by mutableStateOf<DatePickerDialog?>(null)
    var timePicker by mutableStateOf<TimePickerDialog?>(null)
    var toast by mutableStateOf<Toast?>(null)
}

/** android.app.DatePickerDialog: month is zero-based, as on Android. */
class DatePickerDialog(
    context: Context?,
    internal val listener: (Any?, Int, Int, Int) -> Unit,
    internal val year: Int,
    internal val month: Int,
    internal val dayOfMonth: Int
) {
    fun show() { CompatUi.datePicker = this }
    fun dismiss() { if (CompatUi.datePicker === this) CompatUi.datePicker = null }
}

class TimePickerDialog(
    context: Context?,
    internal val listener: (Any?, Int, Int) -> Unit,
    internal val hourOfDay: Int,
    internal val minute: Int,
    internal val is24HourView: Boolean
) {
    fun show() { CompatUi.timePicker = this }
    fun dismiss() { if (CompatUi.timePicker === this) CompatUi.timePicker = null }
}

class Toast private constructor(internal val text: String, internal val duration: Int) {
    fun show() { CompatUi.toast = this }
    fun cancel() { if (CompatUi.toast === this) CompatUi.toast = null }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(context: Context?, text: CharSequence, duration: Int): Toast = Toast(text.toString(), duration)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Wrap the app content in this once; it draws the dialogs requested through the classes above. */
@Composable
fun CompatHost(confirmLabel: String, dismissLabel: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContext provides Context.app) {
        Box(Modifier.fillMaxSize()) {
            content()

            CompatUi.datePicker?.let { request ->
                val initial = remember(request) {
                    runCatching { LocalDate.of(request.year, request.month + 1, request.dayOfMonth).toEpochDay() * MILLIS_PER_DAY }.getOrNull()
                }
                val state = rememberDatePickerState(initialSelectedDateMillis = initial)
                androidx.compose.material3.DatePickerDialog(
                    onDismissRequest = { request.dismiss() },
                    confirmButton = {
                        TextButton(onClick = {
                            val millis = state.selectedDateMillis
                            request.dismiss()
                            if (millis != null) {
                                val date = LocalDate.ofEpochDay(millis.floorDiv(MILLIS_PER_DAY))
                                request.listener(null, date.year, date.monthValue - 1, date.dayOfMonth)
                            }
                        }) { Text(confirmLabel) }
                    },
                    dismissButton = { TextButton(onClick = { request.dismiss() }) { Text(dismissLabel) } }
                ) { DatePicker(state = state) }
            }

            CompatUi.timePicker?.let { request ->
                val state = rememberTimePickerState(request.hourOfDay, request.minute, request.is24HourView)
                AlertDialog(
                    onDismissRequest = { request.dismiss() },
                    confirmButton = {
                        TextButton(onClick = {
                            request.dismiss()
                            request.listener(null, state.hour, state.minute)
                        }) { Text(confirmLabel) }
                    },
                    dismissButton = { TextButton(onClick = { request.dismiss() }) { Text(dismissLabel) } },
                    text = { TimePicker(state = state) }
                )
            }

            CompatUi.toast?.let { toast ->
                LaunchedEffect(toast) {
                    delay(if (toast.duration == Toast.LENGTH_LONG) 3500 else 2000)
                    toast.cancel()
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 6.dp
                ) { Text(toast.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
            }
        }
    }
}

/** Makes the system chrome (status bar, pickers) follow the app's LIGHT / DARK / SYSTEM setting. */
expect fun platformApplyAppearance(appearance: String)
