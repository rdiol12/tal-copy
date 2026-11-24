package com.tal.pocketflow

import android.content.Context
import compat.android.ScheduledNotice
import compat.android.platformRequestNotifications
import compat.android.platformScheduleNotifications
import compat.time.LocalDate

/**
 * Optional daily reminders. Android runs a job once a day; iOS gives apps no such slot, so the
 * notifications for the coming days are worked out and scheduled whenever the app is opened or
 * put away (see ReminderPlan.kt for what they say).
 */
internal object ReminderJob {
    private const val PREFIX = "pocketflow-reminder-"

    /** Installed at startup: the notifications to schedule, given today's date and the current hour. */
    var upcoming: (Context, LocalDate, Int) -> List<ScheduledNotice> = { _, _, _ -> emptyList() }

    private fun enabled(context: Context): Boolean = context.getSharedPreferences("reminder-local", 0).getBoolean("enabled", false)

    @kotlin.jvm.JvmStatic fun schedule(context: Context?) {
        val app = context?.applicationContext ?: return
        if (!enabled(app)) return
        platformRequestNotifications { granted ->
            if (granted && enabled(app)) runCatching {
                platformScheduleNotifications(PREFIX, upcoming(app, LocalDate.now(), compat.time.LocalTime.now().hour))
            }
        }
    }

    @kotlin.jvm.JvmStatic fun cancel(context: Context?) { platformScheduleNotifications(PREFIX, emptyList()) }
}
