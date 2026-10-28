package app.posato.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.posato.generated.resources.Res
import app.posato.generated.resources.picker_selected
import app.posato.generated.resources.picker_value
import org.jetbrains.compose.resources.stringResource

/**
 * A setting that takes one of a few named values: its label, and the current value as a button that opens the
 * choices in place, with a check on the current one. No dialog stands between the person and the choice.
 */
@Composable
internal fun PosatoPickerRow(
    label: String,
    options: List<PosatoPickerOption>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose",
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = PosatoSize.Control),
            horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f).clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            val value = selected?.let { options.getOrNull(it)?.title } ?: placeholder
            PlatformPicker(label, value, options, selected, onSelect)
        }
        PosatoDivider()
    }
}

/** The host's own pop-up button, where the platform has one. */
@Composable
internal expect fun PlatformPicker(
    label: String,
    value: String,
    options: List<PosatoPickerOption>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal fun DrawnPicker(
    label: String,
    value: String,
    options: List<PosatoPickerOption>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val openMenu = rememberMenuOpening { expanded = true }
    val description = stringResource(Res.string.picker_value, label, value)
    Box(modifier) {
        // The button reads as the setting and its value, "Pause set, My set", rather than the value alone.
        PosatoTextButton(
            onClick = openMenu,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    openMenu()
                    true
                }
            },
        ) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = PosatoSize.Menu))
            PosatoIcon(PosatoIcons.ChevronDown, null)
        }
        DropdownMenu(
            modifier = Modifier.widthIn(min = PosatoSize.Menu),
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(PosatoSpace.Hairline, MaterialTheme.colorScheme.outlineVariant),
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    // A choice reads as a button that is selected or not, as the row menus' actions do.
                    modifier = Modifier.heightIn(min = PosatoSize.Control).semantics {
                        role = Role.Button
                        this.selected = index == selected
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Tiny)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            option.detail?.let { PosatoCaption(it) }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                    trailingIcon = if (index == selected) {
                        { PosatoIcon(PosatoIcons.Check, stringResource(Res.string.picker_selected), tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(horizontal = PosatoSpace.Large, vertical = PosatoSpace.Small),
                )
            }
        }
    }
}

@Preview(name = "Picker row", widthDp = 390)
@Composable
private fun PosatoPickerRowPreview() {
    PosatoComponentPreview {
        PosatoPickerRow(
            label = "Pause set",
            options = listOf(PosatoPickerOption("My set", "Default · 1 website"), PosatoPickerOption("Work", "1 website")),
            selected = 0,
            onSelect = {},
        )
    }
}
