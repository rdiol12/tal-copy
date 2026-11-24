package com.tal.workhours

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Native launcher Chronometer ticks without a service or per-second worker. */
open class ShiftWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateFromDisk(context)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(START_OFFICE, START_HOME, STOP)) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id !in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, javaClass))) return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeout(8000) {
                        val repo = WorkRepository.get(context)
                        repo.load()
                        val action = intent.action
                        val marker = intent.getLongExtra(EXPECTED_MARKER, Long.MIN_VALUE)
                        val changed = repo.change { current ->
                            val now = System.currentTimeMillis()
                            when (action) {
                                START_OFFICE -> startTimedShift(current, now, OFFICE, expectedIdle = marker)
                                START_HOME -> startTimedShift(current, now, HOME, expectedIdle = marker)
                                STOP -> closeTimedShift(current, now, marker)
                                else -> current
                            }
                        }
                        refresh(context, changed)
                    }
                } catch (e: Exception) {
                    showError(context)
                } finally { pending.finish() }
            }
        } else if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED)) {
            updateFromDisk(context)
        } else super.onReceive(context, intent)
    }

    private fun updateFromDisk(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { withTimeout(8000) { refresh(context, WorkRepository.get(context).load()) } }
            catch (e: Exception) { showError(context) }
            finally { pending.finish() }
        }
    }

    companion object {
        private const val START_OFFICE = "com.tal.workhours.widget.START_OFFICE"
        private const val START_HOME = "com.tal.workhours.widget.START_HOME"
        private const val STOP = "com.tal.workhours.widget.STOP"
        private const val EXPECTED_MARKER = "expectedShiftMarker"
        private val providers = listOf(ShiftWidgetProvider::class.java, CompactShiftWidgetProvider::class.java)

        /** Safe after a committed data write: launcher failures cannot roll back saved hours. */
        fun refresh(context: Context, data: AppData) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                providers.forEach { provider ->
                    manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                        manager.updateAppWidget(id, views(context, id, data, provider))
                    }
                }
            }.onFailure { android.util.Log.w("WorkHoursWidget", "Widget refresh failed") }
            runCatching { WorkCalendarWidgetProvider.refresh(context, data) }
                .onFailure { android.util.Log.w("WorkHoursWidget", "Calendar widget refresh failed") }
        }
        private fun showError(context: Context) {
            runCatching { WorkCalendarWidgetProvider.refresh(context, null) }
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                providers.forEach { provider ->
                    manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                        manager.updateAppWidget(id, views(context, id, null, provider))
                    }
                }
            }
        }
        private fun command(context: Context, id: Int, action: String, marker: Long, provider: Class<out ShiftWidgetProvider>): PendingIntent {
            val intent = Intent(context, provider).setAction(action)
                .setData(Uri.Builder().scheme("workhours").authority("widget").appendPath(id.toString())
                    .appendPath(action.substringAfterLast('.')).appendPath(marker.toString()).build())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXPECTED_MARKER, marker)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        private fun views(context: Context, id: Int, data: AppData?, provider: Class<out ShiftWidgetProvider>): RemoteViews {
            val localized = systemLanguageContext(context)
            fun text(key: Int) = localized.getString(key)
            val dark=when(data?.settings?.appearance) {
                "DARK" -> true
                "LIGHT" -> false
                else -> (localized.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val compact = provider == CompactShiftWidgetProvider::class.java
            val layout = if (compact) {
                if (dark) R.layout.shift_widget_compact_dark else R.layout.shift_widget_compact
            } else if (dark) R.layout.shift_widget_dark else R.layout.shift_widget
            val v = RemoteViews(context.packageName, layout)
            v.setInt(R.id.widget_root, "setLayoutDirection", android.text.TextUtils.getLayoutDirectionFromLocale(localized.resources.configuration.locales[0]))
            val open = PendingIntent.getActivity(context, id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_title, open)
            v.setOnClickPendingIntent(R.id.widget_open, open)
            v.setTextViewText(R.id.widget_title, text(R.string.app_name))
            v.setTextViewText(R.id.widget_open, text(R.string.widget_open))
            val ready = data?.setupComplete == true
            val active = data?.active
            v.setViewVisibility(R.id.widget_start_group, if (ready && active == null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.widget_stop, if (ready && active != null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.widget_elapsed, if (ready && active != null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.widget_idle, if (ready && active == null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.widget_status, when {
                data == null -> text(R.string.widget_error)
                !ready -> text(R.string.widget_setup)
                active == null -> text(R.string.widget_inactive)
                else -> text(R.string.shift_active) + " · " + text(if (active.location == HOME) R.string.location_home else R.string.location_office)
            })
            v.setTextViewText(R.id.widget_start_office, text(R.string.widget_start_office))
            v.setTextViewText(R.id.widget_start_home, text(R.string.widget_start_home))
            v.setTextViewText(R.id.widget_stop, text(R.string.widget_stop))
            v.setTextViewText(R.id.widget_idle, "00:00")
            // Reconstruct monotonic base after reboot using the persisted wall-clock start.
            val elapsed = if (active == null) 0L else (System.currentTimeMillis()-active.start).coerceAtLeast(0L)
            v.setChronometer(R.id.widget_elapsed, SystemClock.elapsedRealtime()-elapsed, "%s", ready && active != null)
            if (ready) {
                val marker = idleShiftMarker(data)
                v.setOnClickPendingIntent(R.id.widget_start_office, command(context,id,START_OFFICE,marker,provider))
                v.setOnClickPendingIntent(R.id.widget_start_home, command(context,id,START_HOME,marker,provider))
                if (active != null) v.setOnClickPendingIntent(R.id.widget_stop, command(context,id,STOP,active.start,provider))
            }
            return v
        }
    }
}
