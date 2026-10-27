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
    return desktopDevice(System.getProperty("os.name").orEmpty())
}

/** Only a Mac is called a Mac; any other desktop takes the Windows entry, a plain computer without Mac or GNOME paths. */
internal fun desktopDevice(osName: String): PosatoDevice {
    return when {
        osName.startsWith("Mac") -> PosatoDevice.Mac
        osName.startsWith("Linux") -> PosatoDevice.Linux
        else -> PosatoDevice.Windows
    }
}

internal actual val platformUsesCupertinoChrome: Boolean = false

internal actual val platformUsesMaterialRipple: Boolean = false
