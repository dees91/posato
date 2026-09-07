package app.posato.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoSearchField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        state = state,
        label = { Text(label) },
        leadingIcon = { PosatoIcon(PosatoIcons.Search, null) },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(modifier = Modifier.size(PosatoSize.Control), onClick = { state.edit { replace(0, length, "") } }) {
                    PosatoIcon(PosatoIcons.Close, "Clear search")
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        onKeyboardAction = KeyboardActionHandler { focus.clearFocus() },
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
