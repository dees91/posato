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
import app.posato.feature.targets.data.LocalApplicationMapping
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_allow_and_review_applications
import app.posato.generated.resources.action_choose_applications
import app.posato.generated.resources.action_clear_applications
import app.posato.generated.resources.action_remove
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.action_review_applications
import app.posato.generated.resources.application_group_mapping_required
import app.posato.generated.resources.application_mapping_access_denied
import app.posato.generated.resources.application_mapping_access_required
import app.posato.generated.resources.application_mapping_access_restricted
import app.posato.generated.resources.application_mapping_access_unavailable
import app.posato.generated.resources.application_mapping_empty
import app.posato.generated.resources.application_mapping_retained
import app.posato.generated.resources.application_mapping_selected_count
import app.posato.generated.resources.operation_error_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationMappingsSection(
    state: TargetsUiState,
    onRetry: () -> Unit,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    onRemove: (LocalApplicationMappingId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.shouldShowApplicationMappings()) {
        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isApplicationMappingLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            state.applicationMappingFailure?.let { failure ->
                ApplicationMappingFailureNotice(failure, onRetry, Modifier.fillMaxWidth())
            }
            if (state.hasApplicationMappingLoadFailure) {
                return@Column
            }
            LoadedApplicationMappings(state, onChoose, onClear, onRemove)
        }
    }
}

@Composable
private fun LoadedApplicationMappings(
    state: TargetsUiState,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    onRemove: (LocalApplicationMappingId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ApplicationMappingAccessNotice(state)
        if (state.applicationPolicyName == null && state.applicationMappings.isNotEmpty()) {
            Text(stringResource(Res.string.application_mapping_retained), style = MaterialTheme.typography.bodySmall)
        } else if (state.applicationMappings.isEmpty() && state.hasLoadedApplicationMappings) {
            Text(stringResource(Res.string.application_mapping_empty), style = MaterialTheme.typography.bodySmall)
        }
        val numberedMappingCount = state.applicationMappings.count { mapping ->
            mapping.display is LocalApplicationMappingDisplay.Numbered
        }
        if (numberedMappingCount > 0) {
            ApplicationMappingSummary(
                count = numberedMappingCount,
                enabled = state.canClearApplicationMappings(),
                onClear = onClear,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.applicationMappings.filter { mapping ->
            mapping.display is LocalApplicationMappingDisplay.Named
        }.forEach { mapping ->
            ApplicationMappingRow(
                name = (mapping.display as LocalApplicationMappingDisplay.Named).value,
                enabled = state.canRemoveApplicationMapping(mapping.id),
                onRemove = { onRemove(mapping.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ApplicationSelectionButton(state, onChoose)
    }
}

@Composable
private fun ApplicationMappingAccessNotice(state: TargetsUiState) {
    if (state.applicationPolicyName == null) {
        state.applicationMappingAccessMessage()?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ApplicationSelectionButton(
    state: TargetsUiState,
    onChoose: () -> Unit,
) {
    if (state.applicationPolicyName == null || !state.canPresentApplicationSelectionAction()) {
        return
    }

    Button(onClick = onChoose, enabled = state.canChooseApplications()) {
        if (state.applicationMappingMutation == ApplicationMappingMutation.CHOOSE) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            val action = when {
                state.applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED &&
                    state.applicationMappings.isNotEmpty() -> Res.string.action_allow_and_review_applications

                state.applicationMappings.isEmpty() -> Res.string.action_choose_applications

                else -> Res.string.action_review_applications
            }
            Text(stringResource(action))
        }
    }
}

@Composable
private fun ApplicationMappingSummary(
    count: Int,
    enabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.application_mapping_selected_count, count),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClear,
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.action_clear_applications)) }
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

    return applicationMappingAccessMessage()
        ?: if (applicationMappings.isEmpty()) stringResource(Res.string.application_group_mapping_required) else null
}

@Composable
private fun TargetsUiState.applicationMappingAccessMessage(): String? {
    return when {
        !isApplicationMappingAvailable -> {
            stringResource(Res.string.application_mapping_access_unavailable)
        }

        applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED -> {
            stringResource(Res.string.application_mapping_access_required)
        }

        applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_DENIED -> {
            stringResource(Res.string.application_mapping_access_denied)
        }

        applicationMappingsAccess == LocalApplicationMappingsAccess.RESTRICTED -> {
            stringResource(Res.string.application_mapping_access_restricted)
        }

        else -> {
            null
        }
    }
}

private fun TargetsUiState.canPresentApplicationSelectionAction(): Boolean {
    return isApplicationMappingAvailable &&
        (
            applicationMappingsAccess == LocalApplicationMappingsAccess.READY ||
                applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED
        )
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
