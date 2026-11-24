// java.net look-alikes: just enough of URL / HttpURLConnection / URI for the
// app's small JSON-over-HTTPS client. The request itself is done by the
// platform hook at the bottom (NSURLSession on iOS).
package compat.net

import compat.io.ByteArrayInputStream
import compat.io.ByteArrayOutputStream
import compat.io.IOException
import compat.io.InputStream
import compat.io.OutputStream

class UnknownHostException(message: String? = null) : IOException(message)
class SocketTimeoutException(message: String? = null) : IOException(message)
class ConnectException(message: String? = null) : IOException(message)
class SSLException(message: String? = null) : IOException(message)
class MalformedURLException(message: String? = null) : IOException(message)
class URISyntaxException(val input: String, val reason: String) : Exception("$reason: $input")

/** RFC 3986 split of an absolute or relative reference; components are null when absent, like java.net.URI. */
class URI(private val text: String) {
    val scheme: String?
    val rawAuthority: String?
    val userInfo: String?
    val host: String?
    val port: Int
    val rawPath: String
    val rawQuery: String?
    val rawFragment: String?

    init {
        if (text.any { it.isWhitespace() || it == '"' || it == '<' || it == '>' || it == '\\' || it == '^' || it == '`' || it == '{' || it == '|' || it == '}' })
            throw URISyntaxException(text, "Illegal character")
        var rest = text
        rawFragment = rest.indexOf('#').takeIf { it >= 0 }?.let { i -> rest.substring(i + 1).also { rest = rest.substring(0, i) } }
        rawQuery = rest.indexOf('?').takeIf { it >= 0 }?.let { i -> rest.substring(i + 1).also { rest = rest.substring(0, i) } }
        val colon = rest.indexOf(':')
        val schemeCandidate = if (colon > 0) rest.substring(0, colon) else null
        scheme = schemeCandidate?.takeIf { s -> s[0].isLetter() && s.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' } }
        if (scheme != null) rest = rest.substring(colon + 1)
        if (rest.startsWith("//")) {
            val end = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
            val authority = rest.substring(2, end)
            rawAuthority = authority
            rest = rest.substring(end)
            val at = authority.lastIndexOf('@')
            userInfo = if (at >= 0) authority.substring(0, at) else null
            val hostPort = if (at >= 0) authority.substring(at + 1) else authority
            val portColon = if (hostPort.startsWith("[")) hostPort.indexOf(':', hostPort.indexOf(']').coerceAtLeast(0)) else hostPort.lastIndexOf(':')
            val hostText = if (portColon >= 0) hostPort.substring(0, portColon) else hostPort
            val portText = if (portColon >= 0) hostPort.substring(portColon + 1) else ""
            if (portText.isNotEmpty() && portText.toIntOrNull() == null) throw URISyntaxException(text, "Illegal port")
            host = hostText.takeIf { it.isNotEmpty() }
            port = portText.toIntOrNull() ?: -1
        } else {
            rawAuthority = null; userInfo = null; host = null; port = -1
        }
        rawPath = rest
    }

    val path: String? get() = rawPath
    val query: String? get() = rawQuery
    val fragment: String? get() = rawFragment
    val authority: String? get() = rawAuthority
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is URI && other.text == text
    override fun hashCode(): Int = text.hashCode()
}

class URL(private val spec: String) {
    init {
        if (!spec.startsWith("http://") && !spec.startsWith("https://")) throw MalformedURLException("unsupported protocol: $spec")
    }
    fun openConnection(): URLConnection = HttpURLConnection(spec)
    override fun toString(): String = spec
}

open class URLConnection internal constructor()

class HttpURLConnection internal constructor(private val url: String) : URLConnection() {
    var instanceFollowRedirects: Boolean = true
    var connectTimeout: Int = 0
    var readTimeout: Int = 0
    var requestMethod: String = "GET"
    var doOutput: Boolean = false
    var useCaches: Boolean = false
    private val headers = LinkedHashMap<String, String>()
    private val body = ByteArrayOutputStream()
    private var result: HttpResult? = null

    fun setRequestProperty(name: String, value: String) { headers[name] = value }
    val outputStream: OutputStream get() = body

    private fun execute(): HttpResult = result ?: platformHttpRequest(
        url, requestMethod, headers, if (doOutput || body.size() > 0) body.toByteArray() else null,
        connectTimeout, readTimeout, instanceFollowRedirects).also { result = it }

    /** Sends the request on first use, like the JDK class. */
    val responseCode: Int get() = execute().code
    val inputStream: InputStream get() {
        val r = execute()
        if (r.code >= 400) throw IOException("Server returned HTTP response code: ${r.code} for URL: $url")
        return ByteArrayInputStream(r.body)
    }
    val errorStream: InputStream? get() = result?.takeIf { it.code >= 400 }?.let { ByteArrayInputStream(it.body) }
    fun getHeaderField(name: String): String? = execute().headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    fun disconnect() {}
}

class HttpResult(val code: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap())

// ---- Platform hook ----
/** Blocking HTTP(S) request; call it off the main thread. Timeouts are in milliseconds, 0 = platform default. */
expect fun platformHttpRequest(
    url: String, method: String, headers: Map<String, String>, body: ByteArray?,
    connectTimeoutMs: Int, readTimeoutMs: Int, followRedirects: Boolean
): HttpResult
