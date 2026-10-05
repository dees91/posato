package app.posato.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun platformTheme(): PlatformTheme {
    return PlatformTheme(isLight = !isSystemInDarkTheme())
}

internal actual fun platformDevice(): PosatoDevice {
    return PosatoDevice.IPhone
}

internal actual val platformUsesCupertinoChrome: Boolean = false

internal actual val platformUsesMaterialRipple: Boolean = true
