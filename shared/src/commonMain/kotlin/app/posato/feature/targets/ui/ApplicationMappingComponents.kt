package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_choose_applications
import app.posato.generated.resources.action_remove
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.application_group_mapping_required
import app.posato.generated.resources.application_mapping_empty
import app.posato.generated.resources.application_mapping_retained
import app.posato.generated.resources.operation_error_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationMappingsSection(
    state: TargetsUiState,
    onRetry: () -> Unit,
    onChoose: () -> Unit,
    onRemove: (LocalApplicationMappingId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.shouldShowApplicationMappings()) {
        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isApplicationMappingLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (!state.isApplicationMappingAvailable) {
            Text(stringResource(Res.string.application_group_mapping_required), style = MaterialTheme.typography.bodySmall)
        } else {
            state.applicationMappingFailure?.let { failure ->
                ApplicationMappingFailureNotice(failure, onRetry, Modifier.fillMaxWidth())
            }
            if (state.hasApplicationMappingLoadFailure) {
                return@Column
            }
            if (state.applicationPolicyName == null && state.applicationMappings.isNotEmpty()) {
                Text(stringResource(Res.string.application_mapping_retained), style = MaterialTheme.typography.bodySmall)
            } else if (state.applicationMappings.isEmpty() && state.hasLoadedApplicationMappings) {
                Text(stringResource(Res.string.application_mapping_empty), style = MaterialTheme.typography.bodySmall)
            }
            state.applicationMappings.forEach { mapping ->
                ApplicationMappingRow(
                    name = mapping.displayName,
                    enabled = state.canRemoveApplicationMapping(mapping.id),
                    onRemove = { onRemove(mapping.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.applicationPolicyName != null) {
                Button(onClick = onChoose, enabled = state.canChooseApplications()) {
                    if (state.applicationMappingMutation == ApplicationMappingMutation.CHOOSE) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.action_choose_applications))
                    }
                }
            }
        }
    }
}

private fun TargetsUiState.shouldShowApplicationMappings(): Boolean {
    return applicationPolicyName != null || applicationMappings.isNotEmpty() || hasApplicationMappingLoadFailure
}

@Composable
private fun ApplicationMappingRow(
    name: String,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(
                onClick = onRemove,
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.action_remove)) }
        }
    }
}

@Composable
internal fun TargetsUiState.applicationMappingSupportingText(): String? {
    if (isApplicationMappingLoading || hasApplicationMappingLoadFailure) {
        return null
    }

    return if (!isApplicationMappingAvailable || applicationMappings.isEmpty()) {
        stringResource(Res.string.application_group_mapping_required)
    } else {
        null
    }
}

@Composable
private fun ApplicationMappingFailureNotice(
    failure: ApplicationMappingFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.operation_error_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(failure.applicationMappingMessage()))
            if (failure == ApplicationMappingFailure.LOAD_FAILED || failure == ApplicationMappingFailure.CORRUPTED_MAPPINGS) {
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
        }
    }
}
