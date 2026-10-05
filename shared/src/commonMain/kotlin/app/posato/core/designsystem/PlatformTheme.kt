package app.posato.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo

@Immutable
internal data class PlatformTheme(
    val isLight: Boolean,
    val highContrast: Boolean = false,
)

@Composable
internal expect fun platformTheme(): PlatformTheme

@Composable
internal fun PosatoTheme(
    highContrast: Boolean? = null,
    content: @Composable () -> Unit
) {
    val platform = platformTheme()
    val palette = if (platform.isLight) PosatoPalette.Light else PosatoPalette.Dark
    val colors = if (highContrast ?: platform.highContrast) {
        palette.copy(onSurfaceVariant = palette.onSurface, outlineVariant = palette.outline, outline = palette.onSurface)
    } else {
        palette
    }
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides PosatoSize.Control,
        LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = PosatoTypography.Tokens,
            shapes = PosatoShapes.Tokens,
        ) {
            if (platformUsesCupertinoChrome) {
                CompositionLocalProvider(
                    LocalRippleConfiguration provides null,
                    LocalIndication provides PosatoPressHighlight(colors.onSurface.copy(alpha = PRESS_HIGHLIGHT_ALPHA)),
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}

internal enum class PosatoDevice(
    val noun: String
) {
    Mac("Mac"),
    IPhone("iPhone"),
    IPad("iPad"),
}

internal expect fun platformDevice(): PosatoDevice

/** Whether screens take the host's iOS conventions: a sliding navigation stack, navigation bars, and large titles. */
internal expect val platformUsesCupertinoChrome: Boolean

internal fun navigationPlacement(
    device: PosatoDevice,
    landscape: Boolean
): PosatoNavigationPlacement {
    return when (device) {
        PosatoDevice.Mac -> PosatoNavigationPlacement.Sidebar
        PosatoDevice.IPad -> if (landscape) PosatoNavigationPlacement.Sidebar else PosatoNavigationPlacement.Bottom
        PosatoDevice.IPhone -> PosatoNavigationPlacement.Bottom
    }
}

@Composable
internal fun windowNavigationPlacement(device: PosatoDevice): PosatoNavigationPlacement {
    val window = LocalWindowInfo.current
    val landscape by remember(window) {
        derivedStateOf { window.containerSize.width > window.containerSize.height }
    }
    return navigationPlacement(device, landscape)
}

private const val PRESS_HIGHLIGHT_ALPHA = 0.06f
