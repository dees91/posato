package app.posato.prototype.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PosatoSpace {
    val Hairline = 1.dp
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Section = 24.dp
    val Spacious = 32.dp
    val Canvas = 48.dp
}

object PosatoSize {
    val Control = 44.dp
    val Icon = 18.dp
    val LargeIcon = 24.dp
    val ItemSymbol = 36.dp
    val MarkWidth = 24.dp
    val MarkHeight = 32.dp
    val ArtWidth = 122.dp
    val ArtHeight = 144.dp
    val Sidebar = 185.dp
    val NavigationSidebar = 224.dp
    val Content = 820.dp
    val CompactBreakpoint = 600.dp
    val Phone = 390.dp
    val Input = 100.dp
    val Menu = 200.dp
}

object PosatoShapes {
    val Tokens = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
    val Phone = RoundedCornerShape(38.dp)
}

object PosatoControlDefaults {
    val ContentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp)
    val CompactPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val Transparent = Color.Transparent
    const val DISABLED_ALPHA = 0.5f
}

enum class PosatoLayout {
    Compact,
    Expanded,
}

enum class PosatoNavigationPlacement {
    Bottom,
    Sidebar,
}

enum class PosatoTone {
    Neutral,
    Positive,
    Caution,
    Critical,
}
