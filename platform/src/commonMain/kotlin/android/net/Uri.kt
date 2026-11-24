package android.net

import kotlin.jvm.JvmStatic

/**
 * Stand-in for a document Uri. An export Uri collects the bytes the app writes
 * and hands them to the system "save file" sheet when the stream is closed; an
 * import Uri carries the bytes of the file the user picked.
 */
class Uri(val name: String, val mime: String, val bytes: ByteArray?) {
    /** Set for Uris made with [parse]; document Uris have none. */
    var link: String? = null
        private set
    val authority: String? get() = link?.substringAfter("://", "")?.substringBefore('/')?.ifEmpty { null }
    val scheme: String? get() = link?.substringBefore("://", "")?.ifEmpty { null }
    val lastPathSegment: String? get() = link?.substringAfterLast('/') ?: name
    override fun toString(): String = link ?: "document:$name"
    companion object {
        @JvmStatic fun parse(value: String?): Uri = Uri(value ?: "", "", null).also { it.link = value ?: "" }
    }
}

