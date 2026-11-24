// Parts of the Android app that have no counterpart in this build: Google Drive backup
// and the Android side of the home-screen widgets. The screens still refer to them,
// so they exist here with the same shape and do nothing.
package com.tal.pocketflow

import android.content.Context
import android.content.SharedPreferences
import compat.j.Fun

internal class DriveState(context: Context?) {
    @kotlin.jvm.JvmField val prefs: SharedPreferences = context!!.getSharedPreferences("google-drive", Context.MODE_PRIVATE)
    fun email(): String = prefs.getString("email", "") ?: ""
    fun connected(): Boolean = false
    fun generation(): Long = prefs.getLong("generation", 0)
    fun automatic(): Boolean = false
    fun days(): Int = 1
    fun automatic(on: Boolean, days: Int) {}
    fun disconnect() {}
    fun schedule(): Boolean = true
}

internal class DriveApi {
    class Remote(@kotlin.jvm.JvmField val id: String, @kotlin.jvm.JvmField val name: String, @kotlin.jvm.JvmField val date: String, @kotlin.jvm.JvmField val size: Long)
}

internal class DriveTask(context: Context?, @kotlin.jvm.JvmField val action: String?, @kotlin.jvm.JvmField val fileId: String?, @kotlin.jvm.JvmField val generation: Long, background: Boolean) {
    fun interface Completion { fun complete(task: DriveTask?) }
    @kotlin.jvm.JvmField var error: Exception? = null
    @kotlin.jvm.JvmField var files: MutableList<DriveApi.Remote>? = null
    private var completion: Completion? = null
    fun attach(callback: Completion?) { completion = callback }
    fun detach() { completion = null }
    fun cancel() {}
    fun start(authorizedToken: String?) {
        error = UnsupportedOperationException("Google Drive backup is not available in this build")
        val callback = completion; completion = null
        android.os.Handler.main.post { callback?.complete(this) }
    }
    companion object {
        const val CONNECT = "connect"
        const val UPLOAD = "upload"
        const val LIST = "list"
        const val DOWNLOAD = "download"
        const val REVOKE = "revoke"
    }
}


internal object WidgetActions {
    const val EXTRA = "pocketflow.widget.action"
    const val MOVEMENT = "movement"
    const val RULE = "rule"
    const val ACCOUNT = "account"
    @kotlin.jvm.JvmStatic fun valid(action: String?): Boolean = MOVEMENT == action || RULE == action || ACCOUNT == action
}
