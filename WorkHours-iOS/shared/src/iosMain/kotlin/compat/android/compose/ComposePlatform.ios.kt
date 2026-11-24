package compat.android.compose

import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

actual fun platformApplyAppearance(appearance: String) {
    val style = when (appearance) {
        "DARK" -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        "LIGHT" -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        else -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
    }
    UIApplication.sharedApplication.windows.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
}
