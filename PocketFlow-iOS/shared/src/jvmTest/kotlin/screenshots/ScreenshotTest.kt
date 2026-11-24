// Runs the converted app off-screen with Compose for Desktop, drives it through its screens the way a
// user would (through the views' own click handlers) and saves PNGs to build/screenshots. CI prints
// them into the job log, which makes it possible to look at the screens without an iPhone.
package screenshots

import android.app.Screen
import android.content.Context
import android.view.View
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.tal.pocketflow.App
import com.tal.pocketflow.Store
import com.tal.pocketflow.initializeApp
import compat.android.compose.disableViewAnimations
import compat.time.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenshotTest {
    private val output = File(System.getProperty("workhours.screenshotDir") ?: "build/screenshots").also { it.mkdirs() }
    private val scale = 2f
    private val width = 390
    private val height = 844

    private fun seed() {
        val context = Context.app
        context.getDatabasePath("pocketflow.db").delete()
        for (name in listOf("prefs", "security", "tools", "google-drive", "backup-history")) context.getSharedPreferences(name, 0).edit().clear().commit()
        val store = Store(context)
        val today = LocalDate.now()
        val card = Store.Account()
        card.name = "ויזה"; card.type = "credit"; card.closing = 31; card.charge = 10; card.start = today.minusMonths(6).withDayOfMonth(1).toString()
        store.save(card)
        val rows = listOf(
            Triple("מצרכים", "סופר השכונה", 48250L), Triple("תחבורה ודלק", "תחנת דלק", 31000L), Triple("אוכל ושתייה", "קפה", 4600L),
            Triple("חשבונות", "חברת החשמל", 39800L), Triple("פנאי", "קולנוע", 9200L), Triple("בית", "חנות כלי בית", 17550L),
        )
        rows.forEachIndexed { index, (category, merchant, amount) ->
            val tx = Store.Tx()
            tx.date = today.minusDays((index * 2).toLong()).toString(); tx.amount = amount; tx.account = card.id; tx.category = category
            tx.merchant = merchant; tx.note = ""; tx.tag = ""; tx.kind = "expense"; tx.count = 1; tx.status = 1
            store.save(tx)
        }
        store.close()
    }

    @Test
    fun captureScreens() {
        initializeApp()
        disableViewAnimations()
        seed()
        val scene = ImageComposeScene((width * scale).toInt(), (height * scale).toInt(), Density(scale)) { App() }
        try {
            var time = 0L
            fun settle(frames: Int = 30) = repeat(frames) { scene.render(time); time += 16_000_000L; Thread.sleep(8) }
            fun shot(name: String) {
                settle()
                val png = scene.render(time).encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
                File(output, "$name.png").writeBytes(png.bytes)
            }
            fun content(): View = Screen.activity!!.contentView!!
            fun tap(tag: String) {
                val view = Screen.dialogs.lastOrNull()?.window?.content?.findViewWithTag<View>(tag) ?: content().findViewWithTag<View>(tag)
                assertNotNull("no view tagged $tag", view)
                assertTrue("view $tag did not handle the click", view!!.performClick())
                settle(6)
            }
            fun home() { while (Screen.dialogs.isNotEmpty()) Screen.back(); Screen.back(); settle(6) }

            shot("01-home")
            val screens = listOf(11 to "02-snapshot", 1 to "03-movements", 2 to "04-graphs", 3 to "05-recurring", 4 to "06-accounts",
                5 to "07-settings", 6 to "08-backup", 7 to "09-insights", 8 to "10-forecast", 9 to "11-budgets", 10 to "12-shortcuts")
            for ((target, name) in screens) {
                tap("launcher-app-$target")
                shot(name)
                home()
            }
            tap("launcher-app-1")
            tap("add-movement")
            shot("13-add-movement")
            home()
            tap("launcher-app-1")
            tap("menu-toggle")
            shot("14-drawer")
        } finally {
            scene.close()
        }
    }
}
