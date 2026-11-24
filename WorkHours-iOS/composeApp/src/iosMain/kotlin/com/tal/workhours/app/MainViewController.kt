package com.tal.workhours.app

import androidx.compose.ui.window.ComposeUIViewController
import com.tal.workhours.App
import com.tal.workhours.initializeApp
import platform.UIKit.UIViewController

/** Entry point called from Swift (iosApp/iOSApp.swift). */
fun MainViewController(): UIViewController {
    initializeApp()
    return ComposeUIViewController { App() }
}
