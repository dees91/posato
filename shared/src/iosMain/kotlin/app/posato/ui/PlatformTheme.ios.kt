package app.posato.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGFloatVar
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleBody
import platform.UIKit.UIFontTextStyleFootnote
import platform.UIKit.UIFontTextStyleHeadline
import platform.UIKit.UIFontTextStyleLargeTitle
import platform.UIKit.UITraitCollection
import platform.UIKit.currentTraitCollection
import platform.UIKit.labelColor
import platform.UIKit.resolvedColorWithTraitCollection
import platform.UIKit.systemBackgroundColor

@Composable
internal actual fun platformTheme(): PlatformTheme {
    val isLight = !isSystemInDarkTheme()

    return PlatformTheme(
        isLight = isLight,
        background = UIColor.systemBackgroundColor.toComposeColor(),
        label = UIColor.labelColor.toComposeColor(),
        productNameStyle = platformTextStyle(UIFontTextStyleHeadline, FontWeight.SemiBold),
        primaryLineStyle = platformTextStyle(UIFontTextStyleLargeTitle, FontWeight.SemiBold),
        supportingLineStyle = platformTextStyle(UIFontTextStyleBody, FontWeight.Normal),
        buildNoteStyle = platformTextStyle(UIFontTextStyleFootnote, FontWeight.Normal),
    )
}

@Composable
private fun platformTextStyle(
    textStyle: String?,
    fontWeight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = platformFontSize(textStyle),
    fontWeight = fontWeight,
)

@Composable
private fun platformFontSize(textStyle: String?): TextUnit {
    val preferredPointSize = UIFont.preferredFontForTextStyle(textStyle).pointSize.toFloat()
    val fontScale = LocalDensity.current.fontScale

    return (preferredPointSize / fontScale).sp
}

@OptIn(ExperimentalForeignApi::class)
private fun UIColor.toComposeColor(): Color = memScoped {
    val red = alloc<CGFloatVar>()
    val green = alloc<CGFloatVar>()
    val blue = alloc<CGFloatVar>()
    val alpha = alloc<CGFloatVar>()
    val resolvedColor = resolvedColorWithTraitCollection(UITraitCollection.currentTraitCollection)

    check(resolvedColor.getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr)) {
        "UIKit semantic color could not be converted to RGB"
    }

    Color(
        red = red.value.toFloat(),
        green = green.value.toFloat(),
        blue = blue.value.toFloat(),
        alpha = alpha.value.toFloat(),
    )
}
