package app.posato.core.designsystem

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable

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
            content = content,
        )
    }
}

internal expect fun platformNavigationPlacement(): PosatoNavigationPlacement
