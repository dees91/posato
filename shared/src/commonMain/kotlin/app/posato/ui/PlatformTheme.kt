package app.posato.ui

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
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

internal fun PlatformTheme.materialColors(): Colors {
    val primaryTint = if (isLight) LightMoss else DarkMoss

    return if (isLight) {
        lightColors(
            primary = primaryTint,
            primaryVariant = primaryTint,
            secondary = primaryTint,
            secondaryVariant = primaryTint,
            background = background,
            surface = background,
            onBackground = label,
            onSurface = label,
        )
    } else {
        darkColors(
            primary = primaryTint,
            primaryVariant = primaryTint,
            secondary = primaryTint,
            secondaryVariant = primaryTint,
            background = background,
            surface = background,
            onBackground = label,
            onSurface = label,
        )
    }
}

private val LightMoss = Color(0xFF2E5D50)
private val DarkMoss = Color(0xFF76B29E)
