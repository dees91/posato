package app.posato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_add
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_edit
import app.posato.generated.resources.action_reload
import app.posato.generated.resources.action_remove
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.action_save
import app.posato.generated.resources.domain_input_description
import app.posato.generated.resources.domain_input_label
import app.posato.generated.resources.domain_input_placeholder
import app.posato.generated.resources.empty_domains_description
import app.posato.generated.resources.empty_domains_title
import app.posato.generated.resources.entry_error_duplicate
import app.posato.generated.resources.entry_error_empty
import app.posato.generated.resources.entry_error_invalid
import app.posato.generated.resources.entry_error_limit
import app.posato.generated.resources.entry_error_too_long
import app.posato.generated.resources.operation_error_corruption
import app.posato.generated.resources.operation_error_load
import app.posato.generated.resources.operation_error_save
import app.posato.generated.resources.operation_error_title
import app.posato.generated.resources.operation_error_update
import app.posato.generated.resources.product_name
import app.posato.generated.resources.saving_domains
import app.posato.generated.resources.websites_description
import app.posato.generated.resources.websites_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExactDomainsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.product_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.websites_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.websites_description),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun ExactDomainEditor(
    state: ExactDomainsUiState,
    onSubmit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputError = state.inputFailure?.inputMessage()

    key(state.editorSession) {
        val inputState = rememberTextFieldState(initialText = state.editingDomain.orEmpty())
        val submit = {
            keyboardController?.hide()
            onSubmit(inputState.text.toString())
        }

        Surface(
            modifier = modifier,
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExactDomainInput(
                    inputState = inputState,
                    failure = inputError,
                    isEnabled = !state.isSaving,
                    onSubmit = submit,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExactDomainEditorActions(
                    isEditing = state.editingDomain != null,
                    isSaving = state.isSaving,
                    onSubmit = submit,
                    onCancelEdit = onCancelEdit,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExactDomainInput(
    inputState: TextFieldState,
    failure: StringResource?,
    isEnabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            state = inputState,
            modifier = Modifier.fillMaxWidth(),
            enabled = isEnabled,
            label = { Text(stringResource(Res.string.domain_input_label)) },
            placeholder = { Text(stringResource(Res.string.domain_input_placeholder)) },
            isError = failure != null,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { onSubmit() },
        )
        Text(
            text = failure?.let { stringResource(it) } ?: stringResource(Res.string.domain_input_description),
            color = if (failure == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ExactDomainEditorActions(
    isEditing: Boolean,
    isSaving: Boolean,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSubmit,
            enabled = !isSaving,
        ) {
            Text(stringResource(if (isEditing) Res.string.action_save else Res.string.action_add))
        }
        if (isEditing) {
            TextButton(
                onClick = onCancelEdit,
                enabled = !isSaving,
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(Res.string.saving_domains),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun OperationFailureNotice(
    failure: ExactDomainsOperationFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.operation_error_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(failure.operationMessage()),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (failure) {
                ExactDomainsOperationFailure.LOAD_FAILED,
                ExactDomainsOperationFailure.CORRUPTED_POLICY,
                -> TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.action_retry))
                }

                ExactDomainsOperationFailure.REVISION_CONFLICT -> TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.action_reload))
                }

                ExactDomainsOperationFailure.SAVE_FAILED -> Unit
            }
        }
    }
}

@Composable
internal fun EmptyDomains(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.empty_domains_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(Res.string.empty_domains_description),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun ExactDomainRow(
    canonicalDomain: String,
    isEnabled: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = canonicalDomain,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                onClick = onEdit,
                enabled = isEnabled,
            ) {
                Text(stringResource(Res.string.action_edit))
            }
            TextButton(
                onClick = onRemove,
                enabled = isEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.action_remove))
            }
        }
    }
}

private fun ExactDomainEntryFailure.inputMessage(): StringResource {
    return when (this) {
        ExactDomainEntryFailure.EMPTY -> Res.string.entry_error_empty
        ExactDomainEntryFailure.TOO_LONG -> Res.string.entry_error_too_long
        ExactDomainEntryFailure.INVALID_DOMAIN -> Res.string.entry_error_invalid
        ExactDomainEntryFailure.DUPLICATE -> Res.string.entry_error_duplicate
        ExactDomainEntryFailure.LIMIT_REACHED -> Res.string.entry_error_limit
    }
}

internal fun ExactDomainsOperationFailure?.operationMessage(): StringResource {
    return when (this) {
        ExactDomainsOperationFailure.REVISION_CONFLICT -> Res.string.operation_error_update

        ExactDomainsOperationFailure.CORRUPTED_POLICY -> Res.string.operation_error_corruption

        ExactDomainsOperationFailure.SAVE_FAILED -> Res.string.operation_error_save

        ExactDomainsOperationFailure.LOAD_FAILED,
        null,
        -> Res.string.operation_error_load
    }
}
