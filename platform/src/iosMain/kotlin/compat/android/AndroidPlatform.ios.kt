package compat.android

import compat.toByteArray
import compat.toNSData
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
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

actual fun platformExcludeFromBackup(directory: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    NSURL.fileURLWithPath(directory, isDirectory = true).setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
}

actual fun platformObserveForeground(onForeground: () -> Unit, onBackground: () -> Unit) {
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ -> onForeground() }
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ -> onBackground() }
}

actual fun platformHaptic() {
    platform.UIKit.UIImpactFeedbackGenerator(platform.UIKit.UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
}
actual fun platformOpenUrl(url: String): Boolean {
    val target = NSURL.URLWithString(url) ?: return false
    if (!UIApplication.sharedApplication.canOpenURL(target)) return false
    UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
    return true
}

actual fun platformDeviceSecure(): Boolean =
    platform.LocalAuthentication.LAContext().canEvaluatePolicy(platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication, null)

actual fun platformAuthenticate(reason: String, onResult: (Boolean) -> Unit) {
    platform.LocalAuthentication.LAContext().evaluatePolicy(platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication, reason) { success, _ ->
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { onResult(success) }
    }
}

actual fun platformRequestNotifications(onResult: (Boolean) -> Unit) {
    val options = platform.UserNotifications.UNAuthorizationOptionAlert or platform.UserNotifications.UNAuthorizationOptionSound
    platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, _ ->
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { onResult(granted) }
    }
}

actual fun platformScheduleNotifications(prefix: String, notices: List<ScheduledNotice>) {
    val center = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
    center.getPendingNotificationRequestsWithCompletionHandler { pending ->
        val stale = pending.orEmpty().mapNotNull { (it as? platform.UserNotifications.UNNotificationRequest)?.identifier }.filter { it.startsWith(prefix) }
        if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
        for (notice in notices) {
            val content = platform.UserNotifications.UNMutableNotificationContent()
            content.setTitle(notice.title)
            content.setBody(notice.body)
            content.setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
            val at = platform.Foundation.NSDateComponents()
            at.year = notice.year.toLong(); at.month = notice.month.toLong(); at.day = notice.day.toLong()
            at.hour = notice.hour.toLong(); at.minute = notice.minute.toLong()
            val trigger = platform.UserNotifications.UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, false)
            center.addNotificationRequest(platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(prefix + notice.id, content, trigger), null)
        }
    }
}

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun platformInstallCrashHook(onCrash: (Throwable) -> Unit) {
    val previous = setUnhandledExceptionHook { error -> runCatching { onCrash(error) } }
    if (previous != null) setUnhandledExceptionHook { error -> runCatching { onCrash(error) }; previous(error) }
}
