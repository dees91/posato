package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.presence.SessionWindowRequest
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsCategory
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionScreen(
    policyStore: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
    sessionIds: SessionIdGenerator,
    clock: SessionClock,
    timeFormat: SessionTimeFormat,
    owner: SessionTransitionOwner,
    onOpenPausedItems: () -> Unit,
    onEditPausedItems: (TargetsCategory) -> Unit,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Compact,
    deviceLabel: String = "On this device",
    syncState: SyncBootstrapUiState? = null,
    macSetupState: MacHelperSetupUiState? = null,
    onMacSetupAnnouncement: (String) -> Unit = {},
    windowRequest: SessionWindowRequest? = null,
    onConsumeWindowRequest: () -> Unit = {},
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(policyStore, applicationMappings, sessionIds, clock, timeFormat, owner)
    },
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.onScreenEntered() }
    val loginItem = macSetupState?.loginItem
    val loginItemEnabled = loginItem?.enabled?.collectAsState()?.value
    LaunchedEffect(loginItem) { loginItem?.refresh() }
    val consumeWindowRequest by rememberUpdatedState(onConsumeWindowRequest)
    LaunchedEffect(windowRequest) {
        if (windowRequest == null) return@LaunchedEffect
        viewModel.uiState.first { it.status != null }
        when (windowRequest) {
            SessionWindowRequest.START_SESSION -> viewModel.setSetupVisible(true)
            SessionWindowRequest.END_SESSION_EARLY -> viewModel.setEarlyEndConfirmation(true)
            SessionWindowRequest.SESSION -> Unit
        }
        consumeWindowRequest()
    }
    SessionScreen(
        state = state,
        onEnterSetup = { viewModel.setSetupVisible(true) },
        onExitSetup = { viewModel.setSetupVisible(false) },
        onSetDuration = viewModel::setDurationMinutes,
        onEnterReview = { viewModel.setReviewVisible(true) },
        onExitReview = { viewModel.setReviewVisible(false) },
        onStartSession = viewModel::startSession,
        onRequestEarlyEnd = { viewModel.setEarlyEndConfirmation(true) },
        onCancelEarlyEnd = { viewModel.setEarlyEndConfirmation(false) },
        onConfirmEarlyEnd = viewModel::confirmEarlyEnd,
        onRetry = viewModel::retry,
        onRetryEnforcement = viewModel::retryEnforcement,
        onOpenPausedItems = onOpenPausedItems,
        onEditPausedItems = onEditPausedItems,
        modifier = modifier,
        layout = layout,
        deviceLabel = deviceLabel,
        syncState = syncState,
        macSetup = macSetupState?.presentation(),
        onMacSetupCheck = { macSetupState?.check() },
        onMacSetupEnable = { macSetupState?.enable() },
        onMacSetupOpenSettings = { macSetupState?.openSettings() },
        onMacSetupAnnouncement = onMacSetupAnnouncement,
        onMacSetupRemove = { macSetupState?.remove(sessionBlocked = state.blocksHelperRemoval()) },
        macLoginItemEnabled = loginItemEnabled,
        onMacLoginItemChange = { loginItem?.setEnabled(it) },
        onMacStandingGrantChange = { macSetupState?.setStandingGrant(it, sessionBlocked = state.blocksHelperRemoval()) },
    )
}

@Composable
internal fun SessionScreen(
    state: SessionUiState,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Compact,
    deviceLabel: String = "On this device",
    onEnterSetup: () -> Unit = {},
    onExitSetup: () -> Unit = {},
    onSetDuration: (Int) -> Unit = {},
    onEnterReview: () -> Unit = {},
    onExitReview: () -> Unit = {},
    onStartSession: () -> Unit = {},
    onRequestEarlyEnd: () -> Unit = {},
    onCancelEarlyEnd: () -> Unit = {},
    onConfirmEarlyEnd: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRetryEnforcement: () -> Unit = {},
    onOpenPausedItems: () -> Unit = {},
    onEditPausedItems: (TargetsCategory) -> Unit = {},
    syncState: SyncBootstrapUiState? = null,
    macSetup: MacSetupPresentation? = null,
    onMacSetupCheck: () -> Unit = {},
    onMacSetupEnable: () -> Unit = {},
    onMacSetupOpenSettings: () -> Unit = {},
    onMacSetupAnnouncement: (String) -> Unit = {},
    onMacSetupRemove: () -> Unit = {},
    macLoginItemEnabled: Boolean? = null,
    onMacLoginItemChange: (Boolean) -> Unit = {},
    onMacStandingGrantChange: (Boolean) -> Unit = {},
) {
    key(state.isSettingUp, state.isReviewing, state.confirmingEarlyEnd) {
        val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            SessionOperationFailureNotice(state.operationFailure, onRetry)
            when {
                state.status == null && state.operationFailure == null -> {
                    CircularProgressIndicator()
                    Text("Loading session")
                }

                state.status == null -> {}

                state.confirmingEarlyEnd -> {
                    PosatoHeading(
                        "Ready to return?",
                        eyebrow = "END THIS PAUSE",
                        description = if (state.nothingIsRestricted()) {
                            "Nothing is restricted in this pause. You can end it now."
                        } else {
                            "You can end this session early. Your saved choices will stay ready for another time."
                        },
                        layout = layout,
                    )
                    PosatoButton(onConfirmEarlyEnd, enabled = !state.isEnding) { Text("End session") }
                    PosatoButton(onCancelEarlyEnd, style = PosatoButtonStyle.Quiet, enabled = !state.isEnding) { Text("Keep this pause") }
                }

                state.isReviewing -> {
                    SessionReviewContent(state, layout, deviceLabel, onStartSession, onExitReview, onOpenPausedItems, onEditPausedItems, onRetry)
                }

                state.isSettingUp -> {
                    SessionDurationContent(state, layout, onSetDuration, onEnterReview, onExitSetup)
                }

                else -> {
                    SessionOverviewContent(
                        state,
                        layout,
                        deviceLabel,
                        onEnterSetup,
                        onRequestEarlyEnd,
                        onOpenPausedItems,
                        onEditPausedItems,
                        onRetryEnforcement,
                        syncState,
                        macSetup,
                        onMacSetupCheck,
                        onMacSetupEnable,
                        onMacSetupOpenSettings,
                        onMacSetupAnnouncement,
                        onMacSetupRemove,
                        macLoginItemEnabled,
                        onMacLoginItemChange,
                        onMacStandingGrantChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionOperationFailureNotice(
    failure: SessionOperationFailure?,
    onRetry: () -> Unit
) {
    failure?.let {
        PosatoNotice(tone = PosatoTone.Critical, actionContent = { PosatoButton(onRetry) { Text("Retry") } }) {
            Text(stringResource(it.operationMessage()))
        }
    }
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Composable
private fun SessionPhonePreview(
    @PreviewParameter(SessionScreenPreviewDataProvider::class) previewState: SessionScreenPreviewDataProvider.SessionPreviewState,
) {
    PosatoTheme { SessionScreen(previewState.state, macSetup = previewState.macSetup) }
}

@Preview(name = "Desktop", widthDp = 1060, heightDp = 780)
@Composable
private fun SessionDesktopPreview(
    @PreviewParameter(SessionScreenPreviewDataProvider::class) previewState: SessionScreenPreviewDataProvider.SessionPreviewState,
) {
    PosatoTheme { SessionScreen(previewState.state, layout = PosatoLayout.Expanded, macSetup = previewState.macSetup) }
}
