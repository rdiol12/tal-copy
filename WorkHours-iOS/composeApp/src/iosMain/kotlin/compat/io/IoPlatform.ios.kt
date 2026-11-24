package compat.io

import compat.toByteArray
import compat.toNSData
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

private fun directory(kind: NSSearchPathDirectory): String {
    val base = NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String
        ?: NSTemporaryDirectory().trimEnd('/')
    val path = "$base/ShiftHours"
    platformMakeDirectories(path)
    return path
}

// Application Support is private to the app and included in device backups.
private val filesDirectory: String by lazy { directory(NSApplicationSupportDirectory) }
private val cacheDirectory: String by lazy { directory(NSCachesDirectory) }

actual fun platformFilesDirectory(): String = filesDirectory
actual fun platformCacheDirectory(): String = cacheDirectory
actual fun platformFileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)
actual fun platformReadFile(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()
actual fun platformWriteFile(path: String, bytes: ByteArray): Boolean = bytes.toNSData().writeToFile(path, atomically = true)
actual fun platformDeleteFile(path: String): Boolean = NSFileManager.defaultManager.removeItemAtPath(path, error = null)
actual fun platformRenameFile(from: String, to: String): Boolean {
    val manager = NSFileManager.defaultManager
    if (manager.fileExistsAtPath(to)) manager.removeItemAtPath(to, error = null)
    return manager.moveItemAtPath(from, toPath = to, error = null)
}
actual fun platformMakeDirectories(path: String): Boolean =
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
