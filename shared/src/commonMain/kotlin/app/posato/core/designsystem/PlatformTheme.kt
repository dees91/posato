package app.posato.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
            if (!platformUsesMaterialRipple) {
                CompositionLocalProvider(
                    LocalRippleConfiguration provides null,
                    LocalIndication provides PosatoPressDim(colors.primary),
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
    Linux("computer"),
    Windows("computer"),
    Android("phone"),
}

internal expect fun platformDevice(): PosatoDevice

/** A full phrase for this device. Each resource is the sentence that interpolating [PosatoDevice.noun] produces today. */
@Composable
internal fun stringResourceForDevice(
    mac: StringResource,
    iphone: StringResource,
    ipad: StringResource,
    computer: StringResource,
    phone: StringResource,
    vararg formatArgs: Any,
): String {
    val resource = when (platformDevice()) {
        PosatoDevice.Mac -> mac
        PosatoDevice.IPhone -> iphone
        PosatoDevice.IPad -> ipad
        PosatoDevice.Linux, PosatoDevice.Windows -> computer
        PosatoDevice.Android -> phone
    }
    return stringResource(resource, *formatArgs)
}

/** The same device split for a count Polish inflects. [quantity] selects the plural form. */
@Composable
internal fun pluralStringResourceForDevice(
    quantity: Int,
    mac: PluralStringResource,
    iphone: PluralStringResource,
    ipad: PluralStringResource,
    computer: PluralStringResource,
    phone: PluralStringResource,
    vararg formatArgs: Any,
): String {
    val resource = when (platformDevice()) {
        PosatoDevice.Mac -> mac
        PosatoDevice.IPhone -> iphone
        PosatoDevice.IPad -> ipad
        PosatoDevice.Linux, PosatoDevice.Windows -> computer
        PosatoDevice.Android -> phone
    }
    return pluralStringResource(resource, quantity, *formatArgs)
}

/** Whether taps show Material's ripple; only Android expects it, the Apple hosts dim or highlight instead. */
internal expect val platformUsesMaterialRipple: Boolean

/** Whether screens take the host's iOS conventions: a sliding navigation stack, navigation bars, and large titles. */
internal expect val platformUsesCupertinoChrome: Boolean

internal fun navigationPlacement(
    device: PosatoDevice,
    landscape: Boolean
): PosatoNavigationPlacement {
    return when (device) {
        PosatoDevice.Mac, PosatoDevice.Linux, PosatoDevice.Windows -> PosatoNavigationPlacement.Sidebar
        PosatoDevice.IPad -> if (landscape) PosatoNavigationPlacement.Sidebar else PosatoNavigationPlacement.Bottom
        PosatoDevice.IPhone, PosatoDevice.Android -> PosatoNavigationPlacement.Bottom
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

/** Whether the current palette is the light one, for the few drawings that differ beyond the color roles. */
@Composable
@ReadOnlyComposable
internal fun posatoIsLight(): Boolean {
    return MaterialTheme.colorScheme.surface.luminance() > HALF_LUMINANCE
}

private const val HALF_LUMINANCE = 0.5f
