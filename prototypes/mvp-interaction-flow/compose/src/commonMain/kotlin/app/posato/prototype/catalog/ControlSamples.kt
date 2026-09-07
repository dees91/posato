package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoChoiceGroup
import app.posato.prototype.designsystem.PosatoDurationChoice
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSelectionRow
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTab
import app.posato.prototype.designsystem.PosatoTabBar
import app.posato.prototype.designsystem.PosatoToggleButton

@Composable
internal fun ControlSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        PosatoSection(titleContent = { Text("Action hierarchy") }) {
            PosatoButtonStyle.entries.forEach { style ->
                PosatoActionRow {
                    PosatoButton(onClick = { onAction("${style.name} callback invoked.") }, style = style) { Text(style.name) }
                    PosatoButton(onClick = {}, style = style, enabled = false) { Text("Unavailable") }
                }
            }
            PosatoCaption("Every action has a minimum 44 dp target. Try Tab and Space to activate a focused control.")
        }
        DurationSamples(onAction)
        ToggleSamples(onAction)
        ContentTabSamples(onAction)
        NavigationSamples(onAction)
        SelectionSamples(onAction)
    }
}

@Composable
private fun ContentTabSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableIntStateOf(0) }
    PosatoSection(modifier = modifier, titleContent = { Text("Content tabs") }) {
        PosatoTabBar(Modifier.fillMaxWidth()) {
            listOf("Websites" to "50", "Apps" to "4").forEachIndexed { index, (label, count) ->
                PosatoTab(
                    modifier = Modifier.weight(1f),
                    selected = selected == index,
                    onClick = {
                        selected = index
                        onAction("$label tab selected.")
                    },
                    countContent = { Text(count) },
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun DurationSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableIntStateOf(DEFAULT_DURATION) }
    PosatoSection(modifier = modifier, titleContent = { Text("Duration choices") }) {
        PosatoChoiceGroup {
            listOf(DEFAULT_DURATION, MEDIUM_DURATION, LONG_DURATION).forEach { minutes ->
                PosatoDurationChoice(
                    valueLabel = minutes.toString(),
                    unitLabel = "minutes",
                    selected = selected == minutes,
                    onClick = {
                        selected = minutes
                        onAction("Duration selected: $minutes minutes.")
                    },
                )
            }
        }
    }
}

@Composable
private fun ToggleSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(false) }
    PosatoSection(modifier = modifier, titleContent = { Text("Tool toggles") }) {
        PosatoActionRow {
            PosatoToggleButton(selected, onClick = {
                selected = !selected
                onAction("Inspector toggle changed.")
            }) { Text("Inspect prototype") }
            PosatoToggleButton(false, onClick = {}, enabled = false) { Text("Unavailable") }
        }
    }
}

@Composable
internal fun SelectionSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(true) }
    PosatoSection(modifier = modifier, titleContent = { Text("Local app selection") }) {
        PosatoSelectionRow(
            checked = selected,
            onCheckedChange = {
                selected = it
                onAction("Local selection callback invoked.")
            },
            supportingContent = { PosatoCaption("Synthetic app · this device only") },
        ) { Text("Example Social") }
        PosatoSelectionRow(
            checked = false,
            onCheckedChange = {},
            enabled = false,
            supportingContent = { PosatoCaption("Unavailable in this example") },
        ) { Text("Pocket Community") }
    }
}

private const val DEFAULT_DURATION = 25
private const val MEDIUM_DURATION = 45
private const val LONG_DURATION = 60
