package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoSearchField
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTextField

@Composable
internal fun FormSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        WebsiteFieldSamples(onAction)
        PosatoSection(titleContent = { Text("Search a selection") }) {
            PosatoSearchField(state = rememberTextFieldState("reading"), label = "Search websites")
            PosatoCaption("Clear restores the complete list. Search submission dismisses the keyboard.")
        }
        DurationFieldSamples(onAction)
        SelectionSamples(onAction)
        PosatoCaption("The catalog exposes callbacks and error presentation. Validation policy, saving, and the real app picker belong to callers.")
    }
}

@Composable
private fun WebsiteFieldSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputState = rememberTextFieldState(initialText = "news.example")
    var showError by remember { mutableStateOf(false) }
    PosatoSection(modifier = modifier, titleContent = { Text("Website editor") }) {
        PosatoTextField(
            modifier = Modifier.fillMaxWidth(),
            state = inputState,
            label = "Domain or website URL",
            supportingText = "Only an exact hostname would be saved by the application.",
            errorMessage = if (showError) "Use a valid domain such as news.example." else null,
            placeholder = "news.example",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onSubmit = { onAction("Website submit callback invoked. Nothing was saved.") },
        )
        PosatoActionRow {
            PosatoButton(onClick = { onAction("Website submit callback invoked. Nothing was saved.") }) { Text("Save website") }
            PosatoButton(onClick = { onAction("Editor cancel callback invoked.") }, style = PosatoButtonStyle.Secondary) { Text("Cancel") }
            PosatoButton(onClick = {
                showError = !showError
            }, style = PosatoButtonStyle.Quiet) { Text(if (showError) "Clear error" else "Show error") }
        }
    }
}

@Composable
private fun DurationFieldSamples(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = rememberTextFieldState(initialText = "90")
    val disabled = rememberTextFieldState(initialText = "news.example")
    PosatoSection(modifier = modifier, titleContent = { Text("Numeric and disabled fields") }) {
        PosatoTextField(
            modifier = Modifier.fillMaxWidth(),
            state = duration,
            label = "Custom duration in minutes",
            supportingText = "The screen owns bounds and resolves the end time.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            onSubmit = { onAction("Duration submit callback invoked.") },
        )
        PosatoTextField(
            modifier = Modifier.fillMaxWidth(),
            state = disabled,
            label = "Website",
            enabled = false,
            supportingText = "Editing is unavailable in this example.",
        )
    }
}
