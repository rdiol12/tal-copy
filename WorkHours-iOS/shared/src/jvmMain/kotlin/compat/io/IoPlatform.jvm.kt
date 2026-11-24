package compat.io

import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val root: java.io.File by lazy {
    val override = java.lang.System.getProperty("workhours.dataDir")
    (if (override != null) java.io.File(override) else java.io.File(java.lang.System.getProperty("user.home"), ".shifthours")).also { it.mkdirs() }
}

actual fun platformFilesDirectory(): String = java.io.File(root, "files").also { it.mkdirs() }.path
actual fun platformCacheDirectory(): String = java.io.File(root, "cache").also { it.mkdirs() }.path
actual fun platformFileExists(path: String): Boolean = java.io.File(path).exists()
actual fun platformReadFile(path: String): ByteArray? = java.io.File(path).takeIf { it.isFile }?.readBytes()
actual fun platformWriteFile(path: String, bytes: ByteArray): Boolean = try {
    val target = java.io.File(path)
    target.parentFile?.mkdirs()
    val temp = java.io.File(path + ".tmp")
    temp.writeBytes(bytes)
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    true
} catch (e: Exception) { false }
actual fun platformDeleteFile(path: String): Boolean = java.io.File(path).delete()
actual fun platformRenameFile(from: String, to: String): Boolean = java.io.File(from).renameTo(java.io.File(to))
actual fun platformMakeDirectories(path: String): Boolean = java.io.File(path).let { it.isDirectory || it.mkdirs() }
