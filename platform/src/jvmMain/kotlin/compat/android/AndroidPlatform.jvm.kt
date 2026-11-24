package compat.android

actual fun platformExportFile(name: String, mime: String, bytes: ByteArray) {
    java.io.File(java.lang.System.getProperty("user.home"), name).writeBytes(bytes)
}
actual fun platformImportFile(mimeTypes: List<String>, onResult: (ByteArray?) -> Unit) = onResult(null)
actual fun platformExcludeFromBackup(directory: String) { java.io.File(directory).mkdirs() }
actual fun platformObserveForeground(onForeground: () -> Unit, onBackground: () -> Unit) {}
actual fun platformHaptic() {}
actual fun platformOpenUrl(url: String): Boolean = false
actual fun platformDeviceSecure(): Boolean = false
actual fun platformAuthenticate(reason: String, onResult: (Boolean) -> Unit) = onResult(false)
actual fun platformRequestNotifications(onResult: (Boolean) -> Unit) = onResult(false)
/** Kept so tests can look at what would have been scheduled. */
var scheduledNoticesForTests: List<ScheduledNotice> = emptyList()
actual fun platformScheduleNotifications(prefix: String, notices: List<ScheduledNotice>) { scheduledNoticesForTests = notices }
