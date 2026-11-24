package android.net

/**
 * Stand-in for a document Uri. An export Uri collects the bytes the app writes
 * and hands them to the system "save file" sheet when the stream is closed; an
 * import Uri carries the bytes of the file the user picked.
 */
class Uri(val name: String, val mime: String, val bytes: ByteArray?) {
    override fun toString(): String = "document:$name"
}

