// Platform hooks behind the android.* look-alikes (see the android/ folder next to compat/).
package compat.android

// ---- Platform hooks ----
/** Shows the system sheet for saving [bytes] as a file named [name]. */
expect fun platformExportFile(name: String, mime: String, bytes: ByteArray)
/** Creates the directory if needed and marks it as not to be included in device backups. */
expect fun platformExcludeFromBackup(directory: String)
/** Calls back whenever the app comes to the foreground / goes to the background. */
expect fun platformObserveForeground(onForeground: () -> Unit, onBackground: () -> Unit)
/** Lets the user pick a file; calls back with its bytes, or null if cancelled. */
expect fun platformImportFile(mimeTypes: List<String>, onResult: (ByteArray?) -> Unit)
/** A light tap of the haptic engine. */
expect fun platformHaptic()
/** Opens a web page or another app for [url]; returns false if nothing can handle it. */
expect fun platformOpenUrl(url: String): Boolean
/** True when the device has a passcode, so the owner can be asked to prove who they are. */
expect fun platformDeviceSecure(): Boolean
/** Asks for Face ID / Touch ID / the device passcode and reports the outcome on the main thread. */
expect fun platformAuthenticate(reason: String, onResult: (Boolean) -> Unit)

/** A local notification to show at a given wall-clock time. */
class ScheduledNotice(val id: String, val title: String, val body: String, val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int)
/** Asks the user to allow notifications (once; later calls report the stored answer). */
expect fun platformRequestNotifications(onResult: (Boolean) -> Unit)
/** Replaces every pending notification whose id starts with [prefix] by [notices]. */
expect fun platformScheduleNotifications(prefix: String, notices: List<ScheduledNotice>)
/** Calls [onCrash] with an exception nothing caught, just before the app is terminated. */
expect fun platformInstallCrashHook(onCrash: (Throwable) -> Unit)
