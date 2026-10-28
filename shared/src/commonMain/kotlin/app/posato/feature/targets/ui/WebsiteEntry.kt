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
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_add_short
import app.posato.generated.resources.targets_add_hint
import app.posato.generated.resources.targets_add_websites
import app.posato.generated.resources.targets_added_check
import app.posato.generated.resources.targets_added_duplicates
import app.posato.generated.resources.targets_nothing_added
import app.posato.generated.resources.targets_paste_limit
import org.jetbrains.compose.resources.stringResource

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
        receipt?.tooLong == true -> stringResource(Res.string.targets_paste_limit)
        receipt?.saved == false -> stringResource(Res.string.targets_nothing_added)
        receipt != null && receipt.rejectedIndices.isNotEmpty() -> stringResource(Res.string.targets_added_check, receipt.addedCount)
        else -> null
    }
    PosatoTextField(
        state = browser.websiteDraft,
        label = stringResource(Res.string.targets_add_websites),
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
        supportingText = if (receipt != null && receipt.saved) {
            stringResource(Res.string.targets_added_duplicates, receipt.addedCount, receipt.duplicateCount)
        } else {
            stringResource(Res.string.targets_add_hint)
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
                controlId = "action_add_short",
            ) {
                Text(stringResource(Res.string.action_add_short))
            }
        },
    )
}
