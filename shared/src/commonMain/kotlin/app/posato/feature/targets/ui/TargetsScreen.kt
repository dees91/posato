package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.loading_domains
import app.posato.generated.resources.operation_error_title
import app.posato.generated.resources.paused_items_description
import app.posato.generated.resources.paused_items_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TargetsScreen(
    store: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
    modifier: Modifier = Modifier,
    viewModel: TargetsViewModel = viewModel { TargetsViewModel(store, applicationMappings) },
) {
    val state by viewModel.uiState.collectAsState()

    TargetsScreen(
        state = state,
        onSubmitDomain = viewModel::submitDomain,
        onEditDomain = viewModel::beginEditingDomain,
        onCancelDomainEdit = viewModel::cancelEditingDomain,
        onRemoveDomain = viewModel::removeDomain,
        onSubmitApplicationPolicy = viewModel::submitApplicationPolicy,
        onEditApplicationPolicy = viewModel::beginEditingApplicationPolicy,
        onCancelApplicationPolicyEdit = viewModel::cancelEditingApplicationPolicy,
        onRemoveApplicationPolicy = viewModel::removeApplicationPolicy,
        onRetry = viewModel::retry,
        onRetryApplicationMappings = viewModel::retryApplicationMappings,
        onChooseApplications = viewModel::chooseApplications,
        onRemoveApplicationMapping = viewModel::removeApplicationMapping,
        modifier = modifier,
    )
}

@Composable
internal fun TargetsScreen(
    state: TargetsUiState,
    onSubmitDomain: (String) -> Unit,
    onEditDomain: (String) -> Unit,
    onCancelDomainEdit: () -> Unit,
    onRemoveDomain: (String) -> Unit,
    onSubmitApplicationPolicy: (String) -> Unit,
    onEditApplicationPolicy: () -> Unit,
    onCancelApplicationPolicyEdit: () -> Unit,
    onRemoveApplicationPolicy: () -> Unit,
    onRetry: () -> Unit,
    onRetryApplicationMappings: () -> Unit,
    onChooseApplications: () -> Unit,
    onRemoveApplicationMapping: (LocalApplicationMappingId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.isLoading && !state.hasLoaded -> TargetsLoading()

            !state.hasLoaded -> TargetsUnavailable(state.operationFailure, onRetry)

            else -> TargetsContent(
                state = state,
                onSubmitDomain = onSubmitDomain,
                onEditDomain = onEditDomain,
                onCancelDomainEdit = onCancelDomainEdit,
                onRemoveDomain = onRemoveDomain,
                onSubmitApplicationPolicy = onSubmitApplicationPolicy,
                onEditApplicationPolicy = onEditApplicationPolicy,
                onCancelApplicationPolicyEdit = onCancelApplicationPolicyEdit,
                onRemoveApplicationPolicy = onRemoveApplicationPolicy,
                onRetry = onRetry,
                onRetryApplicationMappings = onRetryApplicationMappings,
                onChooseApplications = onChooseApplications,
                onRemoveApplicationMapping = onRemoveApplicationMapping,
            )
        }
    }
}

@Composable
private fun TargetsLoading(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.loading_domains)
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TargetsUnavailable(
    failure: TargetsOperationFailure?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.operation_error_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(failure.operationMessage()), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
        }
    }
}

@Composable
private fun TargetsContent(
    state: TargetsUiState,
    onSubmitDomain: (String) -> Unit,
    onEditDomain: (String) -> Unit,
    onCancelDomainEdit: () -> Unit,
    onRemoveDomain: (String) -> Unit,
    onSubmitApplicationPolicy: (String) -> Unit,
    onEditApplicationPolicy: () -> Unit,
    onCancelApplicationPolicyEdit: () -> Unit,
    onRemoveApplicationPolicy: () -> Unit,
    onRetry: () -> Unit,
    onRetryApplicationMappings: () -> Unit,
    onChooseApplications: () -> Unit,
    onRemoveApplicationMapping: (LocalApplicationMappingId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.canMutatePolicy()
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(Res.string.paused_items_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.paused_items_description), style = MaterialTheme.typography.bodyLarge)
            }
            state.operationFailure?.let { failure -> item { OperationFailureNotice(failure, onRetry, Modifier.fillMaxWidth()) } }
            item { ApplicationSectionHeader() }
            item {
                ApplicationPolicySection(
                    state,
                    enabled,
                    onSubmitApplicationPolicy,
                    onEditApplicationPolicy,
                    onCancelApplicationPolicyEdit,
                    onRemoveApplicationPolicy,
                    Modifier.fillMaxWidth(),
                )
            }
            item {
                ApplicationMappingsSection(
                    state = state,
                    onRetry = onRetryApplicationMappings,
                    onChoose = onChooseApplications,
                    onRemove = onRemoveApplicationMapping,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { WebsitesSectionHeader() }
            item { DomainEditor(state, enabled, onSubmitDomain, onCancelDomainEdit, Modifier.fillMaxWidth()) }
            if (state.domains.isEmpty()) {
                item { EmptyDomains(Modifier.fillMaxWidth()) }
            } else {
                items(state.domains, key = { domain -> domain }) { domain ->
                    TargetRow(
                        value = domain,
                        enabled = enabled,
                        supportingText = null,
                        onEdit = { onEditDomain(domain) },
                        onRemove = { onRemoveDomain(domain) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Composable
private fun TargetsPhonePreview(
    @PreviewParameter(TargetsScreenPreviewDataProvider::class) previewState: TargetsScreenPreviewDataProvider.TargetsPreviewState,
) {
    PosatoTheme { TargetsScreen(previewState.state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Desktop", widthDp = 900, heightDp = 720)
@Composable
private fun TargetsDesktopPreview(
    @PreviewParameter(TargetsScreenPreviewDataProvider::class) previewState: TargetsScreenPreviewDataProvider.TargetsPreviewState,
) {
    PosatoTheme { TargetsScreen(previewState.state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
