// Root of the shared UI: the iOS counterpart of MainActivity.onCreate.
package com.tal.workhours

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tal.workhours.generated.drawableResource
import android.content.Context
import compat.android.compose.CompatHost
import compat.android.compose.CompatResources
import compat.android.platformObserveForeground

/** Call once before the first composition. */
fun initializeApp() {
    CompatResources.drawable = ::drawableResource
    Context.applicationId = "com.tal.workhours"
    AppText.bind()
    // Home-server sync runs while the app is in the foreground (iOS has no counterpart of the Android background job).
    platformObserveForeground(
        onForeground = { HomeSync.foreground(Context.app) },
        onBackground = { HomeSync.background() })
}

@Composable
fun App() {
    // Changing the language rebuilds the whole tree, like an activity restart on Android.
    var languageVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener: () -> Unit = { languageVersion++ }
        AppText.addListener(listener)
        onDispose { AppText.removeListener(listener) }
    }
    key(languageVersion) {
        val appearanceData by WorkRepository.get(Context.app).state.collectAsState()
        WorkHoursTheme(appearanceData?.settings?.appearance ?: "SYSTEM") {
            CompositionLocalProvider(LocalLayoutDirection provides if (AppText.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CompatHost(confirmLabel = tr(R.string.shift_done), dismissLabel = tr(R.string.text_a7c55a8d06)) { WorkHoursApp() }
            }
        }
    }
}
