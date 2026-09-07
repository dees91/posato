package app.posato.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun platformTheme(): PlatformTheme {
    return PlatformTheme(isLight = !isSystemInDarkTheme())
}

internal actual fun platformNavigationPlacement(): PosatoNavigationPlacement {
    return PosatoNavigationPlacement.Bottom
}
