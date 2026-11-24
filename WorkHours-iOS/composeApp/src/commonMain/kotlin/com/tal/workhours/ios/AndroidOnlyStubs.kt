// Stand-ins for Android features that have no iOS implementation yet:
// home-screen widgets, scheduled Google Drive backup and home-server sync.
// They keep the shared code compiling and do nothing.
package com.tal.workhours

import compat.android.Context

object ShiftWidgetProvider {
    fun refresh(context: Context, data: AppData) {}
}

object HomeSync {
    fun changed(context: Context) {}
    fun foreground(context: Context) {}
    fun background() {}
}

class HomeSyncConflict : IllegalStateException("Home sync conflict")
