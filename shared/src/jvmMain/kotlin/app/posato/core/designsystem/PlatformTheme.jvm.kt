package app.posato.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Compose follows the Mac's appearance; on Linux it cannot, so GNOME's color scheme is read instead. */
@Composable
internal actual fun platformTheme(): PlatformTheme {
    if (platformDevice() == PosatoDevice.Linux) {
        val prefersDark by LinuxColorScheme.prefersDark.collectAsState()
        return PlatformTheme(isLight = !prefersDark)
    }
    return PlatformTheme(isLight = !isSystemInDarkTheme())
}

internal actual fun platformDevice(): PosatoDevice {
    return if (System.getProperty("os.name").orEmpty().startsWith("Linux")) PosatoDevice.Linux else PosatoDevice.Mac
}

internal actual val platformUsesCupertinoChrome: Boolean = false

internal actual val platformUsesMaterialRipple: Boolean = false
