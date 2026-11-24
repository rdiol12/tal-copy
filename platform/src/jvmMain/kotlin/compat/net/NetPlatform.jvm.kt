package compat.net

actual fun platformHttpRequest(
    url: String, method: String, headers: Map<String, String>, body: ByteArray?,
    connectTimeoutMs: Int, readTimeoutMs: Int, followRedirects: Boolean
): HttpResult {
    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        connection.instanceFollowRedirects = followRedirects
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = method
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body) } }
        val code = try { connection.responseCode } catch (e: java.net.UnknownHostException) { throw UnknownHostException(e.message) }
            catch (e: java.net.SocketTimeoutException) { throw SocketTimeoutException(e.message) }
            catch (e: java.net.ConnectException) { throw ConnectException(e.message) }
            catch (e: javax.net.ssl.SSLException) { throw SSLException(e.message) }
            catch (e: java.io.IOException) { throw compat.io.IOException(e.message) }
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        val responseHeaders = connection.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
        return HttpResult(code, bytes, responseHeaders)
    } finally { connection.disconnect() }
}
