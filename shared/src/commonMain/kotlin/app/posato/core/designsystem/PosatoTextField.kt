package app.posato.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A labelled text field. The label is the hint inside the empty field and the field's accessible name; there is
 * no floating label, so every field in the app has the same quiet outline.
 */
@Composable
internal fun PosatoTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onSubmit: (() -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    trailingContent: (@Composable () -> Unit)? = null,
    inputModifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoInputField(
            state = state,
            hint = label,
            modifier = inputModifier.fillMaxWidth().escapeLeavesField(focus).semantics {
                contentDescription = label
                errorMessage?.let { error(it) }
            },
            enabled = enabled,
            isError = errorMessage != null,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onSubmit?.let { callback -> KeyboardActionHandler { callback() } },
            lineLimits = lineLimits,
            trailingContent = trailingContent,
        )
        val message = errorMessage ?: supportingText
        message?.let { PosatoFieldMessage(it, isError = errorMessage != null) }
    }
}

/** The outline, hint, and optional leading and trailing content that every Posato text input shares. */
@Composable
internal fun PosatoInputField(
    state: TextFieldState,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val palette = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline = when {
        isError -> palette.error
        focused -> palette.primary
        else -> palette.outline
    }
    val textColor = if (enabled) palette.onSurface else palette.onSurface.copy(alpha = PosatoControlDefaults.DISABLED_ALPHA)
    val textStyle = MaterialTheme.typography.bodyLarge
    BasicTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(color = textColor),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        interactionSource = interaction,
        cursorBrush = SolidColor(palette.primary),
        decorator = { field ->
            Row(
                Modifier.heightIn(min = FieldMinHeight).border(PosatoSpace.Hairline, outline, MaterialTheme.shapes.medium)
                    .padding(start = PosatoSpace.Large, end = if (trailingContent == null) PosatoSpace.Large else PosatoSpace.Tiny),
                horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingContent?.invoke()
                Box(Modifier.weight(1f).padding(vertical = PosatoSpace.Medium), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        Text(hint, style = textStyle, color = palette.onSurfaceVariant, maxLines = 1)
                    }
                    field()
                }
                trailingContent?.invoke()
            }
        },
    )
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

private val FieldMinHeight = 56.dp

@Preview(name = "Text fields", widthDp = 390)
@Preview(name = "Text fields · dark", widthDp = 390, uiMode = 0x20)
@Composable
private fun PosatoTextFieldPreview() {
    PosatoComponentPreview {
        PosatoTextField(
            state = rememberTextFieldState(),
            label = "Add websites",
            supportingText = "Add one, or paste several.",
            trailingContent = { PosatoButton(onClick = {}, modifier = Modifier.padding(PosatoSpace.Small)) { Text("Add") } },
        )
        PosatoTextField(
            state = rememberTextFieldState("invalid"),
            label = "Website domain",
            errorMessage = "Enter a complete domain, such as example.com.",
        )
        PosatoTextField(state = rememberTextFieldState("example.com"), label = "Website domain", enabled = false)
    }
}
