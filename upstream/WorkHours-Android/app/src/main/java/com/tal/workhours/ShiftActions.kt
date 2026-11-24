package com.tal.workhours

/** Idle generation protects against replay of a widget rendered before a completed shift. */
fun idleShiftMarker(data: AppData): Long = data.shifts.maxOfOrNull { it.end } ?: 0L

/** Call under WorkRepository's mutex; repeat/stale starts are idempotent. */
fun startTimedShift(data: AppData, now: Long, location: String, reason: String = "", expectedIdle: Long = idleShiftMarker(data)): AppData {
    if (!data.setupComplete || data.active != null || expectedIdle != idleShiftMarker(data)) return data
    require(now > 0 && location in setOf(OFFICE, HOME)) { tr(R.string.text_bccb639eb1) }
    require(reason.length <= 2000) { tr(R.string.text_58ee2bc711) }
    require(data.shifts.none { it.end > now }) { tr(R.string.text_e4f2184a47) }
    return data.copy(active = ActiveShift(now, location, if (location == HOME) reason else ""))
}

/** Expected start must match: an old close must never close a later active shift. */
fun closeTimedShift(data: AppData, now: Long, expectedStart: Long): AppData {
    val active = data.active ?: return data
    if (!data.setupComplete || active.start != expectedStart) return data
    require(now > active.start) { tr(R.string.text_6f6a3321b0) }
    return data.copy(active = null, shifts = data.shifts + Shift(start = active.start, end = now,
        location = active.location, reason = active.reason, note = active.note, source = "timer"))
}

/** A manual editor opened before a widget change must not overwrite a newer active shift. */
fun requireUnchangedActive(data: AppData, expected: ActiveShift?) {
    require(expected != null && data.active == expected) { tr(R.string.shift_changed) }
}
