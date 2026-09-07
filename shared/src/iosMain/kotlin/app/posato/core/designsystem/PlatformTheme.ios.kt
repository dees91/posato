package app.posato.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification

@Composable
internal actual fun platformTheme(): PlatformTheme {
    var highContrast by remember { mutableStateOf(UIAccessibilityDarkerSystemColorsEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { highContrast = UIAccessibilityDarkerSystemColorsEnabled() }
        onDispose { center.removeObserver(observer) }
    }
    return PlatformTheme(isLight = !isSystemInDarkTheme(), highContrast = highContrast)
}

internal actual fun platformNavigationPlacement(): PosatoNavigationPlacement {
    return PosatoNavigationPlacement.Bottom
}
