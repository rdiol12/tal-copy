// Stand-in for an Android feature that has no iOS implementation yet:
// home-screen widgets. It keeps the shared code compiling and does nothing.
package com.tal.workhours

import compat.android.Context

object ShiftWidgetProvider {
    fun refresh(context: Context, data: AppData) {}
}
