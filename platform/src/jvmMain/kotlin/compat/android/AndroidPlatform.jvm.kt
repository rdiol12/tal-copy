package compat.android

actual fun platformExportFile(name: String, mime: String, bytes: ByteArray) {
    java.io.File(java.lang.System.getProperty("user.home"), name).writeBytes(bytes)
}
actual fun platformImportFile(mimeTypes: List<String>, onResult: (ByteArray?) -> Unit) = onResult(null)
actual fun platformExcludeFromBackup(directory: String) { java.io.File(directory).mkdirs() }
actual fun platformObserveForeground(onForeground: () -> Unit, onBackground: () -> Unit) {}
