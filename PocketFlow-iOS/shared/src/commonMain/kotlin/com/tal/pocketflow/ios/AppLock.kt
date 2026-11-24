package com.tal.pocketflow

import android.app.Activity
import android.content.Context
import compat.android.platformAuthenticate
import compat.android.platformDeviceSecure
import compat.j.Runnable

/** Entry protection with Face ID / Touch ID / the device passcode. The setting is never backed up. */
internal class AppLock(private val activity: Activity?) {
    private var success: Runnable? = null
    private var cancel: Runnable? = null
    private var generation = 0L

    private fun prefs() = activity!!.getSharedPreferences("security", Context.MODE_PRIVATE)
    fun enabled(): Boolean = prefs().getBoolean("entryLock", false)
    fun enabled(enabled: Boolean) { prefs().edit().putBoolean("entryLock", enabled).commit() }
    fun secure(): Boolean = platformDeviceSecure()
    fun busy(): Boolean = success != null

    fun authenticate(onSuccess: Runnable?, onCancel: Runnable?) {
        if (busy()) return
        success = onSuccess; cancel = onCancel
        val token = ++generation
        if (!secure()) { complete(false); return }
        platformAuthenticate("פתיחת PocketFlow") { ok -> if (token == generation && busy()) complete(ok) }
    }
    /** Android delivers the passcode screen's answer through onActivityResult; iOS never does. */
    fun result(request: Int, result: Int): Boolean = false
    /** The app went to the background: an unanswered prompt counts as cancelled. */
    fun background() { if (busy()) { generation++; complete(false) } }
    private fun complete(ok: Boolean) {
        val done = if (ok) success else cancel
        success = null; cancel = null
        done?.run()
    }

    companion object { const val CREDENTIAL_REQUEST = 701 }
}
