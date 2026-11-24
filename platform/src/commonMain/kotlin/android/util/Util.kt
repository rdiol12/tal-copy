package android.util

import kotlin.jvm.JvmStatic

import compat.io.ByteArrayInputStream
import compat.io.ByteArrayOutputStream
import compat.io.File
import compat.io.FileNotFoundException
import compat.io.InputStream
import compat.io.OutputStream

/** android.util.AtomicFile: a write either replaces the file completely or not at all. */
class AtomicFile(val baseFile: File) {
    private class Pending : ByteArrayOutputStream()

    fun startWrite(): OutputStream = Pending()
    fun finishWrite(stream: OutputStream?) {
        val pending = stream as? Pending ?: return
        baseFile.writeBytes(pending.toByteArray())
    }
    fun failWrite(stream: OutputStream?) {}
    fun openRead(): InputStream {
        if (!baseFile.exists()) throw FileNotFoundException("${baseFile.path} (No such file or directory)")
        return ByteArrayInputStream(baseFile.readBytes())
    }
    fun readFully(): ByteArray = baseFile.readBytes()
    fun delete() { baseFile.delete() }
}

object Log {
    @JvmStatic fun d(tag: String, message: String): Int { println("D/$tag: $message"); return 0 }
    @JvmStatic fun i(tag: String, message: String): Int { println("I/$tag: $message"); return 0 }
    @JvmStatic fun w(tag: String, message: String): Int { println("W/$tag: $message"); return 0 }
    @JvmStatic fun w(tag: String, message: String, error: Throwable?): Int { println("W/$tag: $message ${error?.message.orEmpty()}"); return 0 }
    @JvmStatic fun e(tag: String, message: String): Int { println("E/$tag: $message"); return 0 }
    @JvmStatic fun e(tag: String, message: String, error: Throwable?): Int { println("E/$tag: $message ${error?.message.orEmpty()}"); return 0 }
}

