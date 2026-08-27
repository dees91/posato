package app.posato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.loading_domains
import app.posato.generated.resources.operation_error_title
import app.posato.persistence.LocalExactDomainPolicyStore
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExactDomainsScreen(
    store: LocalExactDomainPolicyStore,
    modifier: Modifier = Modifier,
    viewModel: ExactDomainsViewModel = viewModel { ExactDomainsViewModel(store) },
) {
    val state by viewModel.uiState.collectAsState()

    ExactDomainsScreen(
        state = state,
        onSubmit = viewModel::submit,
        onEdit = viewModel::beginEditing,
        onCancelEdit = viewModel::cancelEditing,
        onRemove = viewModel::remove,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun ExactDomainsScreen(
    state: ExactDomainsUiState,
    onSubmit: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when {
            state.isLoading && !state.hasLoaded -> {
                ExactDomainsLoading(modifier = Modifier.fillMaxSize())
            }

            !state.hasLoaded -> {
                ExactDomainsUnavailable(
                    failure = state.operationFailure,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                ExactDomainsContent(
                    state = state,
                    onSubmit = onSubmit,
                    onEdit = onEdit,
                    onCancelEdit = onCancelEdit,
                    onRemove = onRemove,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ExactDomainsLoading(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.loading_domains)

    Box(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ExactDomainsUnavailable(
    failure: ExactDomainsOperationFailure?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.operation_error_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(failure.operationMessage()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}

@Composable
private fun ExactDomainsContent(
    state: ExactDomainsUiState,
    onSubmit: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ExactDomainsHeader()
            }
            state.operationFailure?.let { failure ->
                item {
                    OperationFailureNotice(
                        failure = failure,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                ExactDomainEditor(
                    state = state,
                    onSubmit = onSubmit,
                    onCancelEdit = onCancelEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.domains.isEmpty()) {
                item {
                    EmptyDomains(modifier = Modifier.fillMaxWidth())
                }
            } else {
                items(
                    items = state.domains,
                    key = { domain -> domain },
                ) { domain ->
                    ExactDomainRow(
                        canonicalDomain = domain,
                        isEnabled = !state.isSaving,
                        onEdit = { onEdit(domain) },
                        onRemove = { onRemove(domain) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
