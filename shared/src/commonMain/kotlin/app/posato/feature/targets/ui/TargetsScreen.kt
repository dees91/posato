package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarContentTop
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoNavigationBar
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.choiceSet
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
    setId: PauseSetId = PauseSetId.FIRST,
    header: TargetsHeader = TargetsHeader(),
    viewModel: TargetsViewModel = viewModel(key = setId.choiceSet().hex) { TargetsViewModel(store, applicationMappings, setId) },
) {
    val state by viewModel.uiState.collectAsState()
    TargetsScreen(
        state = state,
        browser = browser,
        deviceLabel = deviceLabel,
        header = header,
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
    header: TargetsHeader = TargetsHeader(),
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
    val callbacks = TargetsCallbacks(
        onSubmitWebsites = onSubmitWebsites,
        onSubmitDomain = onSubmitDomain,
        onEditDomain = onEditDomain,
        onCancelDomainEdit = onCancelDomainEdit,
        onRemoveDomain = onRemoveDomain,
        onRetry = onRetry,
        onRetryApplicationMappings = onRetryApplicationMappings,
        onChooseApplications = onChooseApplications,
        onClearApplicationMappings = onClearApplicationMappings,
        onRemoveApplicationMapping = onRemoveApplicationMapping,
        onActivateApplications = onActivateApplications,
    )
    val onDone = { focus.clearFocus() }
    if (platformUsesCupertinoChrome && header.onBack != null) {
        val editorShown =
            state.hasLoaded && state.editingDomain != null && browser.category == TargetsCategory.WEBSITES && browser.showingWebsiteEditor
        PosatoNavStack(
            if (editorShown) listOf(TargetsRoute.Browser, TargetsRoute.WebsiteEditor) else listOf(TargetsRoute.Browser),
            onBack = onCancelDomainEdit,
            modifier = modifier.fillMaxSize(),
            backEnabled = !state.isSaving,
        ) { route ->
            when (route) {
                TargetsRoute.Browser -> TargetsFrame(header, onDone) {
                    TargetsBody(state, browser, deviceLabel, header, callbacks, inlineEditor = false)
                }

                TargetsRoute.WebsiteEditor -> WebsiteEditorScreen(header.title, state, browser, callbacks)
            }
        }
    } else {
        TargetsFrame(header, onDone, modifier) { TargetsBody(state, browser, deviceLabel, header, callbacks, inlineEditor = true) }
    }
}

/** What a set screen does with the person's input, passed down as one value. */
internal class TargetsCallbacks(
    val onSubmitWebsites: (String, Long) -> Unit,
    val onSubmitDomain: (String) -> Unit,
    val onEditDomain: (String) -> Unit,
    val onCancelDomainEdit: () -> Unit,
    val onRemoveDomain: (String) -> Unit,
    val onRetry: () -> Unit,
    val onRetryApplicationMappings: () -> Unit,
    val onChooseApplications: () -> Unit,
    val onClearApplicationMappings: () -> Unit,
    val onRemoveApplicationMapping: (LocalApplicationMappingId) -> Unit,
    val onActivateApplications: () -> Unit,
)

/** The set's notices and its Websites and Apps browser; the website editor slides in over it unless iOS pushes it. */
@Composable
private fun ColumnScope.TargetsBody(
    state: TargetsUiState,
    browser: TargetsBrowserState,
    deviceLabel: String,
    header: TargetsHeader,
    callbacks: TargetsCallbacks,
    inlineEditor: Boolean,
) {
    if (state.setMissing) {
        PosatoNotice { Text("This set was deleted on another device.") }
        return
    }
    TargetsNotices(state, header, callbacks.onRetry)
    if (!state.hasLoaded) return
    val editing = state.editingDomain != null && browser.category == TargetsCategory.WEBSITES
    val editorShown = inlineEditor && editing && browser.showingWebsiteEditor
    PosatoNavStack(
        if (editorShown) listOf(TargetsRoute.Browser, TargetsRoute.WebsiteEditor) else listOf(TargetsRoute.Browser),
        onBack = callbacks.onCancelDomainEdit,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        backEnabled = !state.isSaving,
    ) { route ->
        when (route) {
            TargetsRoute.WebsiteEditor -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
                rememberLastPresent(state.editingDomain)?.let { domain ->
                    WebsiteEditor(
                        state,
                        browser.editorDraft(state.domainEditorSession, domain),
                        callbacks.onSubmitDomain,
                        callbacks.onCancelDomainEdit,
                    )
                }
            }

            TargetsRoute.Browser -> TargetsBrowser(
                state = state,
                browser = browser,
                deviceLabel = deviceLabel,
                suspendedWebsiteEdit = editing && !browser.showingWebsiteEditor,
                onSubmitWebsites = callbacks.onSubmitWebsites,
                onEditDomain = callbacks.onEditDomain,
                onRemoveDomain = callbacks.onRemoveDomain,
                onRetryApplicationMappings = callbacks.onRetryApplicationMappings,
                onChooseApplications = callbacks.onChooseApplications,
                onClearApplicationMappings = callbacks.onClearApplicationMappings,
                onRemoveApplicationMapping = callbacks.onRemoveApplicationMapping,
                onActivateApplications = callbacks.onActivateApplications,
            )
        }
    }
}

@Composable
private fun TargetsNotices(
    state: TargetsUiState,
    header: TargetsHeader,
    onRetry: () -> Unit,
) {
    if (header.inUse) {
        PosatoCaption("Added items pause now. Removed items stay paused until the pause using this set ends.")
    }
    if (state.isLoading && !state.hasLoaded) {
        CircularProgressIndicator()
        return
    }
    state.operationFailure?.let { failure ->
        PosatoNotice(tone = PosatoTone.Critical, actionContent = { PosatoButton(onRetry) { Text("Reload") } }) {
            Text(stringResource(failure.operationMessage()))
        }
    }
}

/** What the set screen shows above its tabs: the set's name, a way back to the list, and its actions. */
internal class TargetsHeader(
    val title: String = "Paused items",
    val onBack: (() -> Unit)? = null,
    val inUse: Boolean = false,
    val menuContent: (@Composable () -> Unit)? = null,
)

/** Paused items' screens: the browser, with a saved website's editor above it. */
private enum class TargetsRoute {
    Browser,
    WebsiteEditor,
}

@Composable
private fun TargetsBrowser(
    state: TargetsUiState,
    browser: TargetsBrowserState,
    deviceLabel: String,
    suspendedWebsiteEdit: Boolean,
    onSubmitWebsites: (String, Long) -> Unit,
    onEditDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onRetryApplicationMappings: () -> Unit,
    onChooseApplications: () -> Unit,
    onClearApplicationMappings: () -> Unit,
    onRemoveApplicationMapping: (LocalApplicationMappingId) -> Unit,
    onActivateApplications: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val searching = browser.category == TargetsCategory.WEBSITES && browser.searching
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = searching,
        onBackCompleted = {
            focus.clearFocus()
            browser.searching = false
            browser.search.edit { replace(0, length, "") }
        },
    )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
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
