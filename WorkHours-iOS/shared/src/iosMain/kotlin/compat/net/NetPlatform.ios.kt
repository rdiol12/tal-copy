package compat.net

import compat.io.IOException
import compat.toByteArray
import compat.toNSData
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

private class RefuseRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, willPerformHTTPRedirection: NSHTTPURLResponse,
                            newRequest: NSURLRequest, completionHandler: (NSURLRequest?) -> Unit) = completionHandler(null)
}

private val followingSession: NSURLSession by lazy {
    NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration)
}
private val nonFollowingSession: NSURLSession by lazy {
    NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration, delegate = RefuseRedirects(), delegateQueue = null)
}

private class Outcome { var data: NSData? = null; var response: NSURLResponse? = null; var error: NSError? = null }

actual fun platformHttpRequest(
    url: String, method: String, headers: Map<String, String>, body: ByteArray?,
    connectTimeoutMs: Int, readTimeoutMs: Int, followRedirects: Boolean
): HttpResult {
    val target = NSURL.URLWithString(url) ?: throw MalformedURLException(url)
    val request = NSMutableURLRequest.requestWithURL(target)
    request.setHTTPMethod(method)
    request.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
    val timeout = maxOf(connectTimeoutMs, readTimeoutMs)
    if (timeout > 0) request.setTimeoutInterval(timeout / 1000.0)
    headers.forEach { (name, value) -> request.setValue(value, forHTTPHeaderField = name) }
    if (body != null) request.setHTTPBody(body.toNSData())

    val outcome = Outcome()
    val done = dispatch_semaphore_create(0)
    val session = if (followRedirects) followingSession else nonFollowingSession
    session.dataTaskWithRequest(request) { data, response, error ->
        outcome.data = data; outcome.response = response; outcome.error = error
        dispatch_semaphore_signal(done)
    }.resume()
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)

    outcome.error?.let { error ->
        val text = error.localizedDescription
        // NSURLErrorDomain codes.
        throw when (error.code.toInt()) {
            -1003, -1006 -> UnknownHostException(text)
            -1001 -> SocketTimeoutException(text)
            -1004, -1005, -1009 -> ConnectException(text)
            in -1206..-1200 -> SSLException(text)
            else -> IOException(text)
        }
    }
    val http = outcome.response as? NSHTTPURLResponse ?: throw IOException("No HTTP response from $url")
    val responseHeaders = HashMap<String, String>()
    http.allHeaderFields.forEach { (name, value) -> responseHeaders[name.toString()] = value.toString() }
    return HttpResult(http.statusCode.toInt(), outcome.data?.toByteArray() ?: ByteArray(0), responseHeaders)
}
