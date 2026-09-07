package app.posato.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun PosatoTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    supportingText: String? = null,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onSubmit: (() -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    trailingContent: (@Composable () -> Unit)? = null,
    inputModifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        OutlinedTextField(
            modifier = inputModifier.fillMaxWidth().semantics { errorMessage?.let { error(it) } },
            state = state,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = errorMessage != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.small,
            lineLimits = lineLimits,
            trailingIcon = trailingContent,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onSubmit?.let { callback -> KeyboardActionHandler { callback() } },
        )
        val message = errorMessage ?: supportingText
        message?.let { PosatoFieldMessage(it, isError = errorMessage != null) }
    }
}

@Composable
internal fun PosatoFieldMessage(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Text(
        modifier = modifier,
        text = message,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Preview(name = "Text fields", widthDp = 390)
@Preview(name = "Text fields · dark", widthDp = 390, uiMode = 0x20)
@Composable
private fun PosatoTextFieldPreview() {
    PosatoComponentPreview {
        PosatoTextField(
            state = rememberTextFieldState(),
            label = "Add websites",
            placeholder = "example.com",
            supportingText = "Add one, or paste several.",
            trailingContent = { PosatoButton(onClick = {}, style = PosatoButtonStyle.Quiet) { Text("Add") } },
        )
        PosatoTextField(
            state = rememberTextFieldState("invalid"),
            label = "Website domain",
            errorMessage = "Enter a complete domain, such as example.com.",
        )
        PosatoTextField(state = rememberTextFieldState("example.com"), label = "Website domain", enabled = false)
    }
}
