package app.posato.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Immutable
internal data class PlatformTheme(
    val isLight: Boolean,
    val background: Color,
    val label: Color,
    val productNameStyle: TextStyle,
    val primaryLineStyle: TextStyle,
    val supportingLineStyle: TextStyle,
    val buildNoteStyle: TextStyle,
)

@Composable
internal expect fun platformTheme(): PlatformTheme

@Composable
internal fun PosatoTheme(content: @Composable () -> Unit) {
    val platformTheme = platformTheme()

    MaterialTheme(
        colorScheme = platformTheme.materialColorScheme(),
        typography = platformTheme.materialTypography(),
        content = content,
    )
}

private fun PlatformTheme.materialColorScheme(): ColorScheme {
    val primaryTint = if (isLight) LightMoss else DarkMoss

    return if (isLight) {
        lightColorScheme(
            primary = primaryTint,
            secondary = primaryTint,
            background = background,
            surface = background,
            onBackground = label,
            onSurface = label,
        )
    } else {
        darkColorScheme(
            primary = primaryTint,
            secondary = primaryTint,
            background = background,
            surface = background,
            onBackground = label,
            onSurface = label,
        )
    }
}

private fun PlatformTheme.materialTypography(): Typography = Typography(
    headlineMedium = primaryLineStyle,
    titleLarge = productNameStyle,
    titleMedium = productNameStyle,
    titleSmall = productNameStyle,
    bodyLarge = supportingLineStyle,
    bodyMedium = supportingLineStyle,
    bodySmall = buildNoteStyle,
)

private val LightMoss = Color(0xFF2E5D50)
private val DarkMoss = Color(0xFF76B29E)
