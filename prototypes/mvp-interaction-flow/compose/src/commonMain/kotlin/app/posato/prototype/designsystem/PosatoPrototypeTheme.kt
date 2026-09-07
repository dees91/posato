package app.posato.prototype.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun PosatoPrototypeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) PosatoPalette.Dark else PosatoPalette.Light
    val colors = if (highContrast) {
        palette.copy(onSurfaceVariant = palette.onSurface, outlineVariant = palette.outline, outline = palette.onSurface)
    } else {
        palette
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides PosatoSize.Control) {
        MaterialTheme(
            colorScheme = colors,
            typography = PosatoTypography.Tokens,
            shapes = PosatoShapes.Tokens,
            content = content,
        )
    }
}
