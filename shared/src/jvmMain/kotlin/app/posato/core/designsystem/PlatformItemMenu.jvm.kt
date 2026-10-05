package app.posato.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformItemMenu(
    label: String,
    items: List<PosatoMenuItem>,
    modifier: Modifier,
) {
    DrawnItemMenu(label, items, modifier)
}
