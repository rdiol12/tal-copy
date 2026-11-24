package com.tal.pocketflow.app

import androidx.compose.ui.window.ComposeUIViewController
import com.tal.pocketflow.App
import com.tal.pocketflow.IncomingLinks
import com.tal.pocketflow.initializeApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initializeApp()
    return ComposeUIViewController { App() }
}

/** Called from Swift when the app is opened through a pocketflow:// link or the Shortcuts action. */
fun handleLink(url: String) {
    initializeApp()
    IncomingLinks.handle(url)
}
