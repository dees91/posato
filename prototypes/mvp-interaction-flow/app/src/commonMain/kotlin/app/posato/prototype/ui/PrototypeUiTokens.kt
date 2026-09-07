package app.posato.prototype.ui

import androidx.compose.ui.unit.dp

internal object PrototypeUiTokens {
    val OverlayWidth = 440.dp
    val DesktopWidth = 1_060.dp
    val DesktopHeight = 780.dp
    const val ENLARGED_TEXT_SCALE: Float = 1.35f
}

internal enum class PrototypeAppearance { System, Light, Dark }

internal enum class PrototypeControlTab(
    val label: String
) {
    Moments("Moments"),
    Walkthrough("Walkthrough"),
    FreePlay("Free play"),
    State("State")
}

internal data class PrototypeDisplayOptions(
    val appearance: PrototypeAppearance = PrototypeAppearance.System,
    val highContrast: Boolean = false,
    val enlargedText: Boolean = false,
    val tab: PrototypeControlTab = PrototypeControlTab.Moments
)
