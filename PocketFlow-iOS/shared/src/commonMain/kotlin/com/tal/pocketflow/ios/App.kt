// Entry point of the iOS build: the converted MainActivity runs unchanged inside ActivityHost.
package com.tal.pocketflow

import android.app.AlertDialog
import android.content.Context
import androidx.compose.runtime.Composable
import com.tal.pocketflow.res.Res
import com.tal.pocketflow.res.*
import compat.android.compose.ActivityHost
import compat.android.compose.ActivityHostOptions
import compat.android.compose.CompatResources
import compat.android.compose.ViewTheme
import org.jetbrains.compose.resources.DrawableResource

private fun drawableResource(id: Int): DrawableResource = when (id) {
    R.drawable.nav_accounts -> Res.drawable.nav_accounts
    R.drawable.nav_backup -> Res.drawable.nav_backup
    R.drawable.nav_clock -> Res.drawable.nav_clock
    R.drawable.nav_expand_more -> Res.drawable.nav_expand_more
    R.drawable.nav_graphs -> Res.drawable.nav_graphs
    R.drawable.nav_home -> Res.drawable.nav_home
    R.drawable.nav_insights -> Res.drawable.nav_insights
    R.drawable.nav_menu -> Res.drawable.nav_menu
    R.drawable.nav_records -> Res.drawable.nav_records
    R.drawable.nav_settings -> Res.drawable.nav_settings
    R.drawable.nav_shortcuts -> Res.drawable.nav_shortcuts
    R.drawable.nav_snapshot -> Res.drawable.nav_snapshot
    R.drawable.pocketflow_logo -> Res.drawable.pocketflow_logo
    R.drawable.ic_wallet -> Res.drawable.ic_wallet
    else -> error("Unknown drawable id $id")
}

private var initialized = false

/** One-time setup; safe to call more than once. */
fun initializeApp() {
    if (initialized) return
    initialized = true
    Context.applicationId = "com.tal.pocketflow"
    CompatResources.drawable = ::drawableResource
    installSqlite()
    ReminderJob.upcoming = ::upcomingReminders
    // Reminders are re-planned whenever the app is put away, so they reflect the latest data.
    compat.android.platformObserveForeground({}, { runCatching { ReminderJob.schedule(Context.app) } })
}

/** Colours for the parts Android takes from the app theme (dialogs, check boxes, cursors). */
private fun applyTheme() {
    val palette = AppTheme.palette(Context.app)!!
    ViewTheme.accent = palette.primary; ViewTheme.ink = palette.ink; ViewTheme.muted = palette.muted
    ViewTheme.surface = palette.surface; ViewTheme.outline = palette.outline
    AlertDialog.defaultSurface = palette.surface; AlertDialog.defaultInk = palette.ink
    AlertDialog.defaultMuted = palette.muted; AlertDialog.defaultAccent = palette.primary
}

@Composable
fun App() {
    initializeApp()
    ActivityHost(ActivityHostOptions(confirmLabel = "אישור", dismissLabel = "ביטול", darkTheme = { AppTheme.dark(Context.app) })) {
        applyTheme()
        // A link that launched the app is handed over once the screen exists.
        android.os.Handler.main.post { IncomingLinks.deliver() }
        MainActivity()
    }
}
