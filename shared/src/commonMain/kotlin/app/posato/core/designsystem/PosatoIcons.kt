package app.posato.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object PosatoIcons {
    val Pause = lineIcon("Pause", "M8 5V19M16 5V19")
    val Items = lineIcon("Items", "M9 6H20M9 12H20M9 18H20M4 6H5M4 12H5M4 18H5")
    val Apps = lineIcon("Apps", "M4 4H10V10H4ZM14 4H20V10H14ZM4 14H10V20H4ZM14 14H20V20H14Z")
    val Globe = lineIcon("Globe", "M4 12A8 8 0 1 0 20 12A8 8 0 1 0 4 12M4 12H20M12 4C7 8 7 16 12 20C17 16 17 8 12 4")
    val Cloud = lineIcon("Cloud", "M7 18H18A4 4 0 0 0 18.3 10A6 6 0 0 0 6.7 9A4.5 4.5 0 0 0 7 18Z")
    val Clock = lineIcon("Clock", "M4 12A8 8 0 1 0 20 12A8 8 0 1 0 4 12M12 7V12L15 14")
    val Check = lineIcon("Check", "M4 12A8 8 0 1 0 20 12A8 8 0 1 0 4 12M8 12L11 15L16 9")
    val Mac = lineIcon("Mac", "M3 4H21V17H3ZM8 21H16M12 17V21")
    val Phone = lineIcon("Phone", "M9 2H15Q17 2 17 4V20Q17 22 15 22H9Q7 22 7 20V4Q7 2 9 2M11 18H13")
    val Arrow = lineIcon("Arrow", "M5 12H19M14 7L19 12L14 17")
    val Chevron = lineIcon("Chevron", "M9 5L16 12L9 19")
    val ChevronUp = lineIcon("ChevronUp", "M5 15L12 8L19 15")
    val ChevronDown = lineIcon("ChevronDown", "M5 9L12 16L19 9")
    val Search = lineIcon("Search", "M3 10A7 7 0 1 0 17 10A7 7 0 1 0 3 10M15 15L21 21")
    val Close = lineIcon("Close", "M6 6L18 18M18 6L6 18")
    val Edit = lineIcon("Edit", "M15 4L20 9M4 15L15 4Q17 2 19 4L20 5Q22 7 20 9L9 20L3 21ZM4 15L9 20")
    val Remove = lineIcon("Remove", "M4 6H20M9 6V3H15V6M6 6L7 21H17L18 6M10 10V17M14 10V17")
    val More = ImageVector.Builder("More", ICON_VIEWPORT.dp, ICON_VIEWPORT.dp, ICON_VIEWPORT, ICON_VIEWPORT)
        .addPath(
            pathData = PathParser().parsePathString(
                "M2.5 12A1.5 1.5 0 1 0 5.5 12A1.5 1.5 0 1 0 2.5 12Z" +
                    "M10.5 12A1.5 1.5 0 1 0 13.5 12A1.5 1.5 0 1 0 10.5 12Z" +
                    "M18.5 12A1.5 1.5 0 1 0 21.5 12A1.5 1.5 0 1 0 18.5 12Z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
}

@Composable
internal fun PosatoIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(modifier = modifier.size(PosatoSize.Icon), imageVector = imageVector, contentDescription = contentDescription, tint = tint)
}

private fun lineIcon(
    name: String,
    path: String
): ImageVector {
    return ImageVector.Builder(name, ICON_VIEWPORT.dp, ICON_VIEWPORT.dp, ICON_VIEWPORT, ICON_VIEWPORT)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = ICON_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

private const val ICON_VIEWPORT = 24f
private const val ICON_STROKE = 1.6f
