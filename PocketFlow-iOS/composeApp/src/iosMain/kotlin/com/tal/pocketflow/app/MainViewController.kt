package com.tal.pocketflow.app

import androidx.compose.ui.window.ComposeUIViewController
import com.tal.pocketflow.App
import com.tal.pocketflow.initializeApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initializeApp()
    return ComposeUIViewController { App() }
}
