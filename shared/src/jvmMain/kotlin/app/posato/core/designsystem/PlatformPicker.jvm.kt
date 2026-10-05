package app.posato.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun PlatformPicker(
    label: String,
    value: String,
    options: List<PosatoPickerOption>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    DrawnPicker(label, value, options, selected, onSelect, modifier)
}
