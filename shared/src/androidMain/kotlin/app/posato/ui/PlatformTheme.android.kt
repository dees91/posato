package app.posato.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun platformTheme(): PlatformTheme {
    val isLight = !isSystemInDarkTheme()
    val colorScheme = if (isLight) {
        lightColorScheme()
    } else {
        darkColorScheme()
    }
    val typography = Typography()

    return PlatformTheme(
        isLight = isLight,
        background = colorScheme.background,
        label = colorScheme.onBackground,
        productNameStyle = typography.titleMedium,
        primaryLineStyle = typography.headlineMedium,
        supportingLineStyle = typography.bodyLarge,
        buildNoteStyle = typography.bodySmall,
    )
}
