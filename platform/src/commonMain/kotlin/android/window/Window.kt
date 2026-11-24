package android.window

fun interface OnBackInvokedCallback { fun onBackInvoked() }
interface OnBackInvokedDispatcher {
    fun registerOnBackInvokedCallback(priority: Int, callback: OnBackInvokedCallback?)
    fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback?)
    companion object { const val PRIORITY_DEFAULT = 0; const val PRIORITY_OVERLAY = 1000000 }
}
