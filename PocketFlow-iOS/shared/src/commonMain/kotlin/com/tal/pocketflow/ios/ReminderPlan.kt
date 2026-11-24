package com.tal.pocketflow

import android.content.Context
import compat.android.ScheduledNotice
import compat.time.LocalDate

private const val DAYS_AHEAD = 14
private const val HOUR = 9

/**
 * One notification at 09:00 for each of the next two weeks that has something to say, taken from
 * the same alerts the Insights screen shows (renewals coming up, budgets above 80%).
 */
internal fun upcomingReminders(context: Context, today: LocalDate, nowHour: Int): List<ScheduledNotice> {
    val result = ArrayList<ScheduledNotice>()
    val store = Store(context)
    try {
        val analytics = Analytics(store, context)
        for (offset in 0 until DAYS_AHEAD) {
            if (offset == 0 && nowHour >= HOUR) continue          // today's time has passed
            val day = today.plusDays(offset.toLong())
            val alerts = analytics.alerts(day) ?: continue
            if (alerts.isEmpty()) continue
            val body = alerts.take(3).joinToString("\n")
            result.add(ScheduledNotice(day.toString(), "PocketFlow · " + alerts.size + " פריטים לתשומת לבך", body, day.year, day.monthValue, day.dayOfMonth, HOUR, 0))
        }
    } finally {
        store.close()
    }
    return result
}
