// Minimal java.io look-alikes. File operations go through the platform hooks
// at the bottom of this file.
package compat.io

import kotlin.jvm.JvmOverloads

import compat.text.Charset
import compat.text.Charsets

open class IOException @JvmOverloads constructor(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class FileNotFoundException @JvmOverloads constructor(message: String? = null) : IOException(message)
class UnsupportedEncodingException @JvmOverloads constructor(message: String? = null) : IOException(message)

abstract class InputStream : AutoCloseable {
    /** Next byte as 0..255, or -1 at the end of the stream. */
    abstract fun read(): Int

    fun read(buffer: ByteArray): Int = read(buffer, 0, buffer.size)
    open fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var count = 0
        while (count < length) {
            val b = read()
            if (b < 0) break
            buffer[offset + count] = b.toByte()
            count++
        }
        return if (count == 0) -1 else count
    }

    open fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun readAllBytes(): ByteArray = readBytes()
    fun bufferedReader(charset: Charset = Charsets.UTF_8): Reader = Reader(this)
    fun reader(charset: Charset = Charsets.UTF_8): Reader = Reader(this)
    override fun close() {}
}

open class Reader internal constructor(private val source: InputStream) : AutoCloseable {
    private var decoded: String? = null
    private var position = 0
    private fun all(): String = decoded ?: source.readBytes().decodeToString().also { decoded = it }
    fun readText(): String = all().substring(position).also { position = all().length }
    fun readLines(): List<String> = readText().lines()
    /** Reads up to [buffer].size characters; returns the number read, or -1 at the end. */
    fun read(buffer: CharArray): Int = read(buffer, 0, buffer.size)
    fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val text = all()
        if (position >= text.length) return -1
        val count = minOf(length, text.length - position)
        for (i in 0 until count) buffer[offset + i] = text[position + i]
        position += count
        return count
    }
    fun read(): Int { val text = all(); return if (position < text.length) text[position++].code else -1 }
    override fun close() = source.close()
}
class InputStreamReader(source: InputStream, charset: Charset) : Reader(source) {
    constructor(source: InputStream) : this(source, Charsets.UTF_8)
    constructor(source: InputStream, charsetName: String) : this(source, Charsets.UTF_8)
}

abstract class OutputStream : AutoCloseable {
    abstract fun write(byte: Int)
    open fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
    open fun write(bytes: ByteArray, offset: Int, length: Int) {
        for (i in offset until offset + length) write(bytes[i].toInt())
    }
    open fun flush() {}
    override fun close() {}
}

open class ByteArrayOutputStream(initialCapacity: Int = 256) : OutputStream() {
    private var buffer = ByteArray(maxOf(16, initialCapacity))
    private var count = 0

    private fun ensure(extra: Int) {
        if (count + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, count + extra))
    }
    override fun write(byte: Int) { ensure(1); buffer[count++] = byte.toByte() }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensure(length)
        bytes.copyInto(buffer, count, offset, offset + length)
        count += length
    }
    fun size(): Int = count
    fun reset() { count = 0 }
    fun toByteArray(): ByteArray = buffer.copyOf(count)
    fun toString(charsetName: String): String = toByteArray().decodeToString()
    fun toString(charset: Charset): String = toByteArray().decodeToString()
    override fun toString(): String = toByteArray().decodeToString()
}

class ByteArrayInputStream(private val bytes: ByteArray) : InputStream() {
    private var position = 0
    override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val n = minOf(length, bytes.size - position)
        bytes.copyInto(buffer, offset, position, position + n)
        position += n
        return n
    }
    override fun readBytes(): ByteArray = bytes.copyOfRange(position, bytes.size).also { position = bytes.size }
    fun available(): Int = bytes.size - position
}

class File(val path: String) {
    constructor(parent: File, child: String) : this(parent.path.trimEnd('/') + "/" + child)
    constructor(parent: String, child: String) : this(parent.trimEnd('/') + "/" + child)

    val name: String get() = path.substringAfterLast('/')
    val absolutePath: String get() = path
    val parentFile: File? get() = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { File(it) }

    fun exists(): Boolean = platformFileExists(path)
    /** Directories are only ever created, never tested for, so an existing path counts as a file. */
    val isFile: Boolean get() = exists()
    fun length(): Long = platformReadFile(path)?.size?.toLong() ?: 0L
    fun delete(): Boolean = platformDeleteFile(path)
    fun mkdirs(): Boolean = platformMakeDirectories(path)
    fun renameTo(target: File): Boolean = platformRenameFile(path, target.path)
    fun readBytes(): ByteArray = platformReadFile(path) ?: throw FileNotFoundException("$path (No such file or directory)")
    fun readText(charset: Charset = Charsets.UTF_8): String = readBytes().decodeToString()
    fun writeBytes(bytes: ByteArray) {
        if (!platformWriteFile(path, bytes)) throw IOException("Could not write $path")
    }
    fun writeText(text: String, charset: Charset = Charsets.UTF_8) = writeBytes(text.encodeToByteArray())
    fun inputStream(): InputStream = ByteArrayInputStream(readBytes())
    fun outputStream(): OutputStream = object : ByteArrayOutputStream() {
        private var closed = false
        override fun close() { if (!closed) { closed = true; writeBytes(toByteArray()) } }
    }

    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
}

// ---- Platform hooks ----

/** Private, backed-up directory for the app's own data files. */
expect fun platformFilesDirectory(): String
expect fun platformCacheDirectory(): String
expect fun platformFileExists(path: String): Boolean
expect fun platformReadFile(path: String): ByteArray?
/** Atomic replace: the file is either fully written or left as it was. */
expect fun platformWriteFile(path: String, bytes: ByteArray): Boolean
expect fun platformDeleteFile(path: String): Boolean
expect fun platformRenameFile(from: String, to: String): Boolean
expect fun platformMakeDirectories(path: String): Boolean

/** Reads the whole file up front; fine for the small documents the apps handle. */
class FileInputStream(file: File) : InputStream() {
    constructor(path: String) : this(File(path))
    private val source = ByteArrayInputStream(file.readBytes())
    override fun read(): Int = source.read()
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source.read(buffer, offset, length)
    override fun readBytes(): ByteArray = source.readBytes()
}
/** Collects the bytes and writes the file when closed. */
class FileOutputStream(private val file: File) : ByteArrayOutputStream() {
    constructor(path: String) : this(File(path))
    private var closed = false
    override fun flush() { file.writeBytes(toByteArray()) }
    override fun close() { if (!closed) { closed = true; file.writeBytes(toByteArray()) } }
}
