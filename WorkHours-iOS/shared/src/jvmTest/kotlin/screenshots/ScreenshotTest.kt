// Renders the shared UI off-screen with Compose for Desktop and saves PNGs to
// build/screenshots. CI prints them into the job log, which makes it possible
// to look at the screens without an iPhone. Same Skia renderer as on iOS.
package screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.tal.workhours.App
import com.tal.workhours.AppData
import com.tal.workhours.AppSettings
import com.tal.workhours.AppText
import com.tal.workhours.OFFICE
import com.tal.workhours.HOME
import com.tal.workhours.Shift
import com.tal.workhours.WorkRepository
import com.tal.workhours.initializeApp
import com.tal.workhours.timestamp
import compat.android.Context
import compat.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

class ScreenshotTest {
    private val output = File(System.getProperty("workhours.screenshotDir") ?: "build/screenshots").also { it.mkdirs() }
    private val scale = 2f
    private val width = 390
    private val height = 844

    /** A phone-sized scene. [taps] are in dp and are applied one after another before the picture is taken. */
    private fun shot(name: String, taps: List<Pair<Int, Int>> = emptyList(), content: @Composable () -> Unit) {
        val scene = ImageComposeScene((width * scale).toInt(), (height * scale).toInt(), Density(scale)) { content() }
        try {
            var time = 0L
            fun settle(frames: Int = 40) = repeat(frames) { scene.render(time); time += 16_000_000L; Thread.sleep(15) }
            settle()
            for ((x, y) in taps) {
                val at = Offset(x * scale, y * scale)
                scene.sendPointerEvent(PointerEventType.Move, at)
                scene.sendPointerEvent(PointerEventType.Press, at)
                scene.render(time); time += 16_000_000L
                scene.sendPointerEvent(PointerEventType.Release, at)
                settle()
            }
            val png = scene.render(time).encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
            File(output, "$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
    }

    private fun sampleData(): AppData {
        val today = LocalDate.now()
        val days = (1L..12L).map { today.minusDays(it) }.filter { it.dayOfWeek.value in setOf(7, 1, 2, 3, 4) }.take(6)
        val shifts = days.mapIndexed { index, day ->
            Shift(start = timestamp(day.atTime(9, 0)), end = timestamp(day.atTime(if (index % 2 == 0) 17 else 19, 30)),
                location = if (index % 3 == 0) HOME else OFFICE)
        }
        return AppData(shifts = shifts, settings = AppSettings(hourlyWage = 65.0, monthlyHours = 182.0), setupComplete = true)
    }

    @Test
    fun captureScreens() {
        initializeApp()
        val repo = WorkRepository.get(Context.app)
        runBlocking { runCatching { repo.load() }; repo.restore(AppData()) }
        AppText.select("en")
        shot("01-first-run-en") { App() }

        runBlocking { repo.restore(sampleData()) }
        shot("02-home-en") { App() }
        // Launcher icons, positions in dp (see 02-home-en.png).
        shot("04-snapshot-en", taps = listOf(76 to 378)) { App() }
        shot("05-summaries-en", taps = listOf(195 to 378)) { App() }
        shot("06-entries-en", taps = listOf(313 to 378)) { App() }
        shot("07-leave-en", taps = listOf(76 to 493)) { App() }
        shot("08-backup-en", taps = listOf(313 to 493)) { App() }
        shot("09-widgets-page-en", taps = listOf(241 to 668)) { App() }
        shot("10-manual-entry-en", taps = listOf(302 to 746)) { App() }
        AppText.select("he")
        shot("03-home-he") { App() }
        shot("11-snapshot-he", taps = listOf(313 to 378)) { App() }
        AppText.select("")
    }
}
