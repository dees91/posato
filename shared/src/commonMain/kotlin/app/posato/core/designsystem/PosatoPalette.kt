package app.posato.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object PosatoPalette {
    val Light: ColorScheme = lightColorScheme(
        primary = Color(0xFF2E5D50),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE4EBE5),
        onPrimaryContainer = Color(0xFF2E5D50),
        secondary = Color(0xFF2E5D50),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDF2EA),
        onSecondaryContainer = Color(0xFF2E5D50),
        tertiary = Color(0xFF805811),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBF3DF),
        onTertiaryContainer = Color(0xFF805811),
        error = Color(0xFFA93228),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFF0EC),
        onErrorContainer = Color(0xFFA93228),
        background = Color(0xFFEEEEE8),
        onBackground = Color(0xFF18231F),
        surface = Color(0xFFFFFEFA),
        onSurface = Color(0xFF18231F),
        surfaceVariant = Color(0xFFF6F6F0),
        onSurfaceVariant = Color(0xFF626B63),
        surfaceContainer = Color(0xFFF6F6F0),
        surfaceContainerLow = Color(0xFFF8F8F2),
        outline = Color(0xFFA3AFA2),
        outlineVariant = Color(0xFFDEDFD5),
        inverseSurface = Color(0xFF18231F),
        inverseOnSurface = Color(0xFFFFFEFA),
        surfaceTint = Color(0xFF2E5D50),
    )

    val Dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFA7C3A2),
        onPrimary = Color(0xFF18231F),
        primaryContainer = Color(0xFF334436),
        onPrimaryContainer = Color(0xFFA7C3A2),
        secondary = Color(0xFFA7C3A2),
        onSecondary = Color(0xFF18231F),
        secondaryContainer = Color(0xFF2A3A2E),
        onSecondaryContainer = Color(0xFFA7C3A2),
        tertiary = Color(0xFFEDC780),
        onTertiary = Color(0xFF18231F),
        tertiaryContainer = Color(0xFF352E20),
        onTertiaryContainer = Color(0xFFEDC780),
        error = Color(0xFFFFA69B),
        onError = Color(0xFF18231F),
        errorContainer = Color(0xFF392823),
        onErrorContainer = Color(0xFFFFA69B),
        background = Color(0xFF141B17),
        onBackground = Color(0xFFF2F2E9),
        surface = Color(0xFF1C2520),
        onSurface = Color(0xFFF2F2E9),
        surfaceVariant = Color(0xFF232E26),
        onSurfaceVariant = Color(0xFFAFB9AE),
        surfaceContainer = Color(0xFF232E26),
        surfaceContainerLow = Color(0xFF202A23),
        outline = Color(0xFF6D816E),
        outlineVariant = Color(0xFF39453B),
        inverseSurface = Color(0xFFF2F2E9),
        inverseOnSurface = Color(0xFF1C2520),
        surfaceTint = Color(0xFFA7C3A2),
    )
}
