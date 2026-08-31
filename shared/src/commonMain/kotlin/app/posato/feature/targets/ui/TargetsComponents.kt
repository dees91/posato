package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_add
import app.posato.generated.resources.action_add_application_group
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_edit
import app.posato.generated.resources.action_reload
import app.posato.generated.resources.action_remove
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.action_save
import app.posato.generated.resources.application_group_description
import app.posato.generated.resources.application_group_empty
import app.posato.generated.resources.application_group_input_label
import app.posato.generated.resources.application_group_input_placeholder
import app.posato.generated.resources.application_group_title
import app.posato.generated.resources.domain_input_label
import app.posato.generated.resources.domain_input_placeholder
import app.posato.generated.resources.empty_domains_description
import app.posato.generated.resources.empty_domains_title
import app.posato.generated.resources.operation_error_title
import app.posato.generated.resources.saving_domains
import app.posato.generated.resources.websites_description
import app.posato.generated.resources.websites_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationSectionHeader(modifier: Modifier = Modifier) {
    SectionHeader(Res.string.application_group_title, Res.string.application_group_description, modifier)
}

@Composable
internal fun WebsitesSectionHeader(modifier: Modifier = Modifier) {
    SectionHeader(Res.string.websites_title, Res.string.websites_description, modifier)
}

@Composable
private fun SectionHeader(
    title: StringResource,
    description: StringResource,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.padding(top = 12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ApplicationPolicySection(
    state: TargetsUiState,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentName = state.applicationPolicyName
    if (currentName == null || state.isEditingApplicationPolicy) {
        ApplicationPolicyEditor(state, enabled, onSubmit, onEdit, onCancel, onRemove, currentName, modifier)
    } else {
        TargetRow(
            value = currentName,
            enabled = enabled,
            supportingText = state.applicationMappingSupportingText(),
            onEdit = onEdit,
            onRemove = onRemove,
            modifier = modifier,
        )
    }
}

@Composable
private fun ApplicationPolicyEditor(
    state: TargetsUiState,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    currentName: String?,
    modifier: Modifier = Modifier,
) {
    key(state.applicationEditorSession) {
        val inputState = rememberTextFieldState(initialText = currentName.orEmpty())
        val submit = { onSubmit(inputState.text.toString()) }
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (currentName != null) {
                TargetRow(
                    value = currentName,
                    enabled = false,
                    supportingText = state.applicationMappingSupportingText(),
                    onEdit = onEdit,
                    onRemove = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EditorSurface(Modifier.fillMaxWidth()) {
                if (currentName == null) {
                    Text(stringResource(Res.string.application_group_empty), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    state = inputState,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    label = { Text(stringResource(Res.string.application_group_input_label)) },
                    placeholder = { Text(stringResource(Res.string.application_group_input_placeholder)) },
                    isError = state.applicationPolicyInputFailure != null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = { submit() },
                )
                Text(
                    stringResource(state.applicationPolicyInputFailure.applicationMessage()),
                    color = if (state.applicationPolicyInputFailure == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                EditorActions(
                    addLabel = Res.string.action_add_application_group,
                    editing = state.isEditingApplicationPolicy,
                    enabled = enabled,
                    saving = state.savingMutation == TargetMutation.APPLICATION_POLICY,
                    onSubmit = submit,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
internal fun DomainEditor(
    state: TargetsUiState,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(state.domainEditorSession) {
        val inputState = rememberTextFieldState(initialText = state.editingDomain.orEmpty())
        val submit = { onSubmit(inputState.text.toString()) }
        EditorSurface(modifier) {
            OutlinedTextField(
                state = inputState,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(Res.string.domain_input_label)) },
                placeholder = { Text(stringResource(Res.string.domain_input_placeholder)) },
                isError = state.domainInputFailure != null,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { submit() },
            )
            Text(
                stringResource(state.domainInputFailure.domainMessage()),
                color = if (state.domainInputFailure == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            EditorActions(
                addLabel = Res.string.action_add,
                editing = state.editingDomain != null,
                enabled = enabled,
                saving = state.savingMutation == TargetMutation.DOMAIN,
                onSubmit = submit,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun EditorSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun EditorActions(
    addLabel: StringResource,
    editing: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSubmit, enabled = enabled) { Text(stringResource(if (editing) Res.string.action_save else addLabel)) }
        if (editing) {
            TextButton(onClick = onCancel, enabled = enabled) { Text(stringResource(Res.string.action_cancel)) }
        }
        if (saving) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.saving_domains), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TargetRow(
    value: String,
    enabled: Boolean,
    supportingText: String?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onEdit, enabled = enabled) { Text(stringResource(Res.string.action_edit)) }
            TextButton(
                onClick = onRemove,
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.action_remove)) }
        }
    }
}

@Composable
internal fun EmptyDomains(modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.empty_domains_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(Res.string.empty_domains_description), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun OperationFailureNotice(
    failure: TargetsOperationFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.operation_error_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(failure.operationMessage()))
            when (failure) {
                TargetsOperationFailure.LOAD_FAILED,
                TargetsOperationFailure.CORRUPTED_POLICY -> TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }

                TargetsOperationFailure.REVISION_CONFLICT -> TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_reload)) }

                TargetsOperationFailure.SAVE_FAILED -> Unit
            }
        }
    }
}
