package compat.android

import compat.toByteArray
import compat.toNSData
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal fun topViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow ?: application.windows.firstOrNull() as? UIWindow
    var top = window?.rootViewController
    while (true) top = top?.presentedViewController ?: break
    return top
}

/** Writes the bytes to a temporary file and opens the system "Save to Files" sheet for it. */
actual fun platformExportFile(name: String, mime: String, bytes: ByteArray) {
    val safeName = name.replace('/', '_').ifBlank { "export" }
    val path = NSTemporaryDirectory().trimEnd('/') + "/" + safeName
    if (!bytes.toNSData().writeToFile(path, atomically = true)) return
    val url = NSURL.fileURLWithPath(path)
    dispatch_async(dispatch_get_main_queue()) {
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ImportDelegate(private val onResult: (ByteArray?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val data = url?.let { NSData.dataWithContentsOfURL(it) }
        activeImport = null
        onResult(data?.toByteArray())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activeImport = null
        onResult(null)
    }
}

// The picker only holds its delegate weakly, so keep it alive until it reports back.
private var activeImport: ImportDelegate? = null

actual fun platformImportFile(mimeTypes: List<String>, onResult: (ByteArray?) -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        val presenter = topViewController()
        if (presenter == null) { onResult(null); return@dispatch_async }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
        val delegate = ImportDelegate(onResult)
        activeImport = delegate
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}
