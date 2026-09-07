package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    FilterChip(
        modifier = modifier.heightIn(min = PosatoSize.Control),
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
internal fun PosatoChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = PosatoSize.Control).semantics { role = Role.RadioButton },
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        color = if (selected) palette.secondaryContainer else palette.surface,
        contentColor = if (selected) palette.onSecondaryContainer else palette.onSurface,
        border = BorderStroke(PosatoSpace.Hairline, if (selected) palette.primary else palette.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(PosatoSpace.Large),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
            supportingContent?.invoke()
        }
    }
}

@Composable
internal fun PosatoSelectionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics { role = Role.Checkbox },
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        color = if (checked) palette.secondaryContainer else palette.surface,
        border = BorderStroke(PosatoSpace.Hairline, if (checked) palette.primary else palette.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(PosatoSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                content()
                supportingContent?.invoke()
            }
        }
    }
}

@Composable
internal fun PosatoDurationChoice(
    valueLabel: String,
    unitLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoChoiceTile(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        supportingContent = { PosatoCaption(unitLabel) },
    ) {
        Text(valueLabel, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(name = "Selection states", widthDp = 390)
@Composable
private fun PosatoSelectionPreview() {
    PosatoComponentPreview {
        PosatoChoiceGroup {
            PosatoToggleButton(selected = true, onClick = {}) { Text("Selected") }
            PosatoToggleButton(selected = false, onClick = {}) { Text("Available") }
            PosatoToggleButton(selected = false, onClick = {}, enabled = false) { Text("Disabled") }
        }
        PosatoChoiceGroup {
            PosatoChoiceTile(selected = true, onClick = {}) { Text("Selected") }
            PosatoChoiceTile(selected = false, onClick = {}, enabled = false) { Text("Disabled") }
        }
        PosatoSelectionRow(checked = true, onCheckedChange = {}, supportingContent = { PosatoCaption("On this device only") }) {
            Text("Selected applications")
        }
        PosatoSelectionRow(checked = false, onCheckedChange = {}) { Text("Available selection") }
        PosatoSelectionRow(checked = true, onCheckedChange = {}, enabled = false) { Text("Disabled selection") }
    }
}

@Preview(name = "Duration choices", widthDp = 390)
@Composable
private fun PosatoDurationChoicePreview() {
    PosatoComponentPreview {
        PosatoChoiceGroup {
            PosatoDurationChoice("25", "minutes", selected = false, onClick = {})
            PosatoDurationChoice("45", "minutes", selected = true, onClick = {})
            PosatoDurationChoice("60", "minutes", selected = false, onClick = {})
        }
    }
}
