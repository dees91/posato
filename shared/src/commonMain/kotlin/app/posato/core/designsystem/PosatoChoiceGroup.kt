package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoChoiceGroup(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
        content = content,
    )
}

@Preview(name = "Wrapping choices", widthDp = 280)
@Composable
private fun PosatoChoiceGroupPreview() {
    PosatoComponentPreview {
        PosatoChoiceGroup {
            PosatoDurationChoice("25", "minutes", selected = true, onClick = {})
            PosatoDurationChoice("45", "minutes", selected = false, onClick = {})
            PosatoDurationChoice("60", "minutes", selected = false, onClick = {})
        }
    }
}
