package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTextField

@Composable
internal fun WebsiteEntry(
    browser: TargetsBrowserState,
    enabled: Boolean,
    onSubmit: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val submit: () -> Unit = {
        if (enabled) {
            browser.submit(onSubmit)
            focus.requestFocus()
        }
    }
    val receipt = browser.lastReceipt
    val error = when {
        receipt?.tooLong == true -> "Paste up to 65,536 characters at a time."
        receipt?.saved == false -> "Nothing was added. Your draft is kept so you can retry."
        receipt != null && receipt.rejectedIndices.isNotEmpty() -> "${receipt.addedCount} added. Check the remaining entries or the list capacity."
        else -> null
    }
    PosatoTextField(
        state = browser.websiteDraft,
        label = "Add websites",
        modifier = modifier.onPreviewKeyEvent { event ->
            val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
            if (enter && !event.isShiftPressed) {
                if (event.type == KeyEventType.KeyDown) submit()
                true
            } else {
                false
            }
        },
        inputModifier = Modifier.focusRequester(focus),
        placeholder = "website.example",
        supportingText = if (receipt != null && receipt.saved) {
            "${receipt.addedCount} added · ${receipt.duplicateCount} already on your list"
        } else {
            "Add one, or paste several. The www variant is included; other subdomains are not."
        },
        errorMessage = error,
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 2),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        onSubmit = submit,
        trailingContent = {
            PosatoButton(
                onClick = submit,
                enabled = enabled && browser.websiteDraft.text.isNotBlank(),
                modifier = Modifier.padding(PosatoSpace.Small),
            ) {
                Text("Add")
            }
        },
    )
}
