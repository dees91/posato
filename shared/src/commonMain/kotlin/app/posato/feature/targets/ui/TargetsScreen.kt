package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TargetsScreen(
    store: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
    modifier: Modifier = Modifier,
    browser: TargetsBrowserState = remember { TargetsBrowserState() },
    deviceLabel: String = "On this device only",
    viewModel: TargetsViewModel = viewModel { TargetsViewModel(store, applicationMappings) },
) {
    val state by viewModel.uiState.collectAsState()
    TargetsScreen(
        state = state,
        browser = browser,
        deviceLabel = deviceLabel,
        onSubmitWebsites = { input, id -> viewModel.submitWebsites(input, id, preserveEditingDomain = !browser.showingWebsiteEditor) },
        onSubmitDomain = viewModel::submitDomain,
        onEditDomain = { domain ->
            browser.showingWebsiteEditor = true
            viewModel.beginEditingDomain(domain)
        },
        onCancelDomainEdit = viewModel::cancelEditingDomain,
        onRemoveDomain = viewModel::removeDomain,
        onRetry = viewModel::retry,
        onRetryApplicationMappings = viewModel::retryApplicationMappings,
        onChooseApplications = viewModel::chooseApplications,
        onClearApplicationMappings = viewModel::clearApplicationMappings,
        onRemoveApplicationMapping = viewModel::removeApplicationMapping,
        onActivateApplications = viewModel::activateApplicationPolicy,
        modifier = modifier,
    )
}

@Composable
internal fun TargetsScreen(
    state: TargetsUiState,
    modifier: Modifier = Modifier,
    browser: TargetsBrowserState = remember { TargetsBrowserState() },
    deviceLabel: String = "On this device only",
    onSubmitWebsites: (String, Long) -> Unit = { _, _ -> },
    onSubmitDomain: (String) -> Unit = {},
    onEditDomain: (String) -> Unit = {},
    onCancelDomainEdit: () -> Unit = {},
    onRemoveDomain: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onRetryApplicationMappings: () -> Unit = {},
    onChooseApplications: () -> Unit = {},
    onClearApplicationMappings: () -> Unit = {},
    onRemoveApplicationMapping: (LocalApplicationMappingId) -> Unit = {},
    onActivateApplications: () -> Unit = {},
) {
    LaunchedEffect(state.websiteBatchReceipt) { browser.accept(state.websiteBatchReceipt) }
    val focus = LocalFocusManager.current
    LaunchedEffect(browser) {
        snapshotFlow { browser.search.text.toString().trim().lowercase() }.distinctUntilChanged().drop(1).collect {
            browser.websitesScroll.scrollToItem(0)
        }
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(
            titleContent = { Text("Paused items", style = MaterialTheme.typography.headlineSmall) },
            actionContent = {
                PosatoButton(onClick = { focus.clearFocus() }, style = PosatoButtonStyle.Quiet) { Text("Done") }
            },
        )
        if (state.isLoading && !state.hasLoaded) {
            CircularProgressIndicator()
            return@Column
        }
        state.operationFailure?.let { failure ->
            PosatoNotice(tone = PosatoTone.Critical, actionContent = { PosatoButton(onRetry) { Text("Reload") } }) {
                Text(stringResource(failure.operationMessage()))
            }
        }
        if (!state.hasLoaded) return@Column
        if (state.editingDomain != null && browser.category == TargetsCategory.WEBSITES && browser.showingWebsiteEditor) {
            WebsiteEditor(state, browser.editorDraft(state.domainEditorSession, state.editingDomain), onSubmitDomain, onCancelDomainEdit)
            return@Column
        }
        val suspendedWebsiteEdit = state.editingDomain != null && browser.category == TargetsCategory.WEBSITES
        TargetsCategoryTabs(browser.category, state.domains.size, state.applicationMappings.size) {
            focus.clearFocus()
            browser.category = it
        }
        if (suspendedWebsiteEdit) SuspendedWebsiteEditNotice { browser.showingWebsiteEditor = true }
        when (browser.category) {
            TargetsCategory.WEBSITES -> WebsiteBrowser(
                state,
                browser,
                onSubmitWebsites,
                onEditDomain,
                onRemoveDomain,
                rowActionsEnabled = !suspendedWebsiteEdit,
                modifier = Modifier.weight(1f),
            )

            TargetsCategory.APPLICATIONS -> ApplicationBrowser(
                state,
                browser.applicationsScroll,
                deviceLabel,
                onChooseApplications,
                onClearApplicationMappings,
                onRemoveApplicationMapping,
                onRetryApplicationMappings,
                onActivateApplications,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SuspendedWebsiteEditNotice(onResume: () -> Unit) {
    PosatoNotice(actionContent = {
        PosatoButton(onResume, style = PosatoButtonStyle.Quiet) { Text("Resume website edit") }
    }) {
        Text("Your unfinished website edit is still here. Resume it to finish or cancel before changing another saved website.")
    }
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Composable
private fun TargetsPhonePreview(
    @PreviewParameter(TargetsScreenPreviewDataProvider::class) previewState: TargetsScreenPreviewDataProvider.TargetsPreviewState,
) {
    PosatoTheme { TargetsScreen(previewState.state) }
}

@Preview(name = "Desktop", widthDp = 1060, heightDp = 780)
@Composable
private fun TargetsDesktopPreview(
    @PreviewParameter(TargetsScreenPreviewDataProvider::class) previewState: TargetsScreenPreviewDataProvider.TargetsPreviewState,
) {
    PosatoTheme { TargetsScreen(previewState.state) }
}
