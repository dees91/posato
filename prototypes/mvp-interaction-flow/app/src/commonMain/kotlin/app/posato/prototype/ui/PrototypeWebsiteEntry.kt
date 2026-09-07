package app.posato.prototype.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTextField
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeWebsiteEntry

@Composable
internal fun PrototypeWebsiteEntry(
    browser: PrototypeItemBrowserState,
    result: PrototypeWebsiteEntry,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = browser.websiteDraft
    val focus = remember { FocusRequester() }
    val submit: () -> Unit = {
        onAction(ItemAction.AddDomains(draft.text.toString()))
        focus.requestFocus()
    }
    LaunchedEffect(result.revision) {
        browser.acceptSubmission(result)
    }
    val showingResult = result.revision > 0 && draft.text.toString() == result.remainder
    PosatoTextField(
        modifier = modifier.onPreviewKeyEvent { event ->
            val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
            if (enter && !event.isShiftPressed) {
                if (event.type == KeyEventType.KeyDown) submit()
                true
            } else {
                false
            }
        },
        state = draft,
        inputModifier = Modifier.focusRequester(focus),
        label = "Add websites",
        placeholder = "website.example",
        supportingText = if (showingResult) result.message else "Add one, or paste several separated by commas or new lines.",
        errorMessage = result.message.takeIf { showingResult && result.invalidCount > 0 },
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 2),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        onSubmit = submit,
        trailingContent = {
            PosatoButton(
                modifier = Modifier.padding(PosatoSpace.Small),
                onClick = submit,
                enabled = draft.text.isNotBlank(),
            ) { Text("Add") }
        },
    )
}
