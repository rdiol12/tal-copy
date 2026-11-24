package com.tal.workhours

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

class WorkCalendarWidgetProvider : ShiftWidgetProvider() {
    companion object {
        fun refresh(context: Context, data: AppData?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WorkCalendarWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val localized = systemLanguageContext(context)
            val locale = localized.resources.configuration.locales[0]
            val today = LocalDate.now(israelZone)
            val dark = when (data?.settings?.appearance) {
                "DARK" -> true
                "LIGHT" -> false
                else -> localized.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val foreground = if (dark) 0xFFF1F4FF.toInt() else 0xFF17376F.toInt()
            val views = RemoteViews(context.packageName, R.layout.work_calendar_widget)
            views.setInt(R.id.calendar_widget_root, "setLayoutDirection", android.text.TextUtils.getLayoutDirectionFromLocale(locale))
            views.setInt(R.id.calendar_widget_root, "setBackgroundResource",
                if (dark) R.drawable.widget_background_dark else R.drawable.widget_background)
            views.setTextColor(R.id.calendar_widget_title, foreground)
            views.setTextColor(R.id.calendar_widget_period, foreground)
            views.setTextViewText(R.id.calendar_widget_title, localized.getString(R.string.calendar_title))
            views.setOnClickPendingIntent(R.id.calendar_widget_root, PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).setAction("com.tal.workhours.OPEN_WORK_CALENDAR")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.removeAllViews(R.id.calendar_widget_grid)

            if (data == null || !data.setupComplete) {
                views.setTextViewText(R.id.calendar_widget_period,
                    localized.getString(if (data == null) R.string.widget_error else R.string.widget_setup))
                manager.updateAppWidget(ids, views)
                return
            }

            val period = periodFor(today, data.settings.periodStartDay)
            views.setTextViewText(R.id.calendar_widget_period, "${period.from.format(dateFormat)} – ${period.end.format(dateFormat)}")
            val shiftsByDate = data.shifts.groupBy { LocalDate.parse(it.workDate) }
            val leaveByDate = data.leaveRecords.groupBy { LocalDate.parse(it.date) }
            val paidVacationDates = leavePayments(data.leaveRecords)
                .filter { it.record.type == VACATION && it.payPercent > 0 && it.hours > 0 }
                .map { LocalDate.parse(it.record.date) }.toSet()
            val activeDate = data.active?.let { localTime(it.start).toLocalDate() }
            val homeLabel = localized.getString(R.string.calendar_includes_home)
            fun addRow(cells: List<RemoteViews>) {
                val row = RemoteViews(context.packageName, R.layout.work_calendar_widget_row)
                for (cell in cells) row.addView(R.id.calendar_widget_row, cell)
                views.addView(R.id.calendar_widget_grid, row)
            }
            fun cell(label: String, background: Int?, description: String = label, home: Boolean = false): RemoteViews {
                return RemoteViews(context.packageName, R.layout.work_calendar_widget_cell).apply {
                    setTextViewText(R.id.calendar_widget_day, label)
                    setTextColor(R.id.calendar_widget_day, if (background == null) foreground else 0xFF263342.toInt())
                    setInt(R.id.calendar_widget_cell, "setBackgroundColor", background ?: Color.TRANSPARENT)
                    setContentDescription(R.id.calendar_widget_cell, description)
                    setViewVisibility(R.id.calendar_widget_home, if (home) View.VISIBLE else View.GONE)
                    setInt(R.id.calendar_widget_home, "setColorFilter", if (dark && background == null) 0xFF83DED3.toInt() else 0xFF00766F.toInt())
                }
            }

            addRow((0..6).map { index ->
                val name = DayOfWeek.SUNDAY.plus(index.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale)
                cell(name, null)
            })
            val start = period.from.minusDays((period.from.dayOfWeek.value % 7).toLong())
            repeat(6) { week ->
                addRow((0..6).map { column ->
                    val date = start.plusDays((week * 7 + column).toLong())
                    if (!period.contains(date)) return@map cell("", null)
                    val leaves = leaveByDate[date].orEmpty()
                    val missing = isMissingCalendarReport(date, today, shiftsByDate.keys, leaveByDate.keys, activeDate)
                    val home = shiftsByDate[date].orEmpty().any { it.location == HOME } ||
                        date == activeDate && data.active?.location == HOME
                    val background = when {
                        leaves.any { it.type == HOLIDAY } -> 0xFFF6F0FC.toInt()
                        leaves.any { it.type == SICK } -> 0xFFEEF5FC.toInt()
                        date in paidVacationDates -> 0xFFF0F8F1.toInt()
                        missing -> 0xFFFFF2DF.toInt()
                        date == today -> 0xFFDDE8FF.toInt()
                        column >= 5 -> 0xFFF4F4F5.toInt()
                        else -> null
                    }
                    val description = date.format(dateFormat) + if (home) " · $homeLabel" else ""
                    cell(date.dayOfMonth.toString(), background, description, home)
                })
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
