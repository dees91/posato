package app.posato.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoSearchField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    PosatoInputField(
        state = state,
        hint = label,
        modifier = modifier.fillMaxWidth().escapeLeavesField(focus).semantics { contentDescription = label },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        onKeyboardAction = KeyboardActionHandler { focus.clearFocus() },
        leadingContent = { PosatoIcon(PosatoIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            if (state.text.isNotEmpty()) {
                PosatoTextButton(onClick = { state.edit { replace(0, length, "") } }, modifier = Modifier.padding(horizontal = PosatoSpace.Medium)) {
                    PosatoIcon(PosatoIcons.Close, "Clear search")
                }
            }
        },
    )
}

@Preview(name = "Empty and populated search", widthDp = 390)
@Composable
private fun PosatoSearchFieldPreview() {
    PosatoComponentPreview {
        PosatoSearchField(state = rememberTextFieldState(), label = "Search websites")
        PosatoSearchField(state = rememberTextFieldState("example"), label = "Search websites")
    }
}
