package app.posato.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.awt.SystemColor
import javax.swing.UIManager

@Composable
internal actual fun platformTheme(): PlatformTheme {
    val isLight = !isSystemInDarkTheme()
    val background = SystemColor.window.toComposeColor(invert = !isLight)

    return PlatformTheme(
        isLight = isLight,
        background = background,
        label = SystemColor.windowText.toComposeColor(invert = !isLight),
        productNameStyle = platformTextStyle("Label.font", FontWeight.Medium),
        primaryLineStyle = platformTextStyle("InternalFrame.titleFont", FontWeight.SemiBold),
        supportingLineStyle = platformTextStyle("Label.font", FontWeight.Normal),
        buildNoteStyle = platformTextStyle("ToolTip.font", FontWeight.Normal),
    )
}

private fun platformTextStyle(
    fontKey: String,
    fontWeight: FontWeight,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = platformFontSize(fontKey),
        fontWeight = fontWeight,
    )

private fun platformFontSize(fontKey: String): TextUnit =
    checkNotNull(UIManager.getFont(fontKey)) {
        "System font is not available for $fontKey"
    }.size2D.sp

private fun java.awt.Color.toComposeColor(invert: Boolean): Color =
    Color(
        red = red.platformChannel(invert),
        green = green.platformChannel(invert),
        blue = blue.platformChannel(invert),
    )

private fun Int.platformChannel(invert: Boolean): Int =
    if (invert) {
        MAX_COLOR_CHANNEL - this
    } else {
        this
    }

private const val MAX_COLOR_CHANNEL = 255
