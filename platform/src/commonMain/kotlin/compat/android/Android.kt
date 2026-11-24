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
