package com.tal.workhours

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initializeApp()
    return ComposeUIViewController { App() }
}
