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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
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
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    scheduledPauses: ScheduledPauses? = null,
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(policyStore, applicationMappings, sessionIds, clock, timeFormat, owner)
    },
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.onScreenEntered() }
    val loginItem = macSetupState?.loginItem
    val loginItemEnabled = loginItem?.enabled?.collectAsState()?.value
    LaunchedEffect(loginItem) { loginItem?.refresh() }
    LaunchedEffect(macSetupState) { macSetupState?.readQuietly() }
    val scope = rememberCoroutineScope()
    val scheduledEnd = remember(viewModel, scheduledPauses) {
        ScheduledEndState(viewModel::setEarlyEndConfirmation, viewModel::confirmEarlyEnd, { scheduledPauses?.endEarly() }, scope)
    }
    val consumeWindowRequest by rememberUpdatedState(onConsumeWindowRequest)
    LaunchedEffect(windowRequest) {
        if (windowRequest == null) return@LaunchedEffect
        viewModel.uiState.first { it.status != null }
        when (windowRequest) {
            SessionWindowRequest.START_SESSION -> {
                viewModel.setSetupVisible(true)
            }

            SessionWindowRequest.END_SESSION_EARLY -> {
                viewModel.setEarlyEndConfirmation(true)
                scheduledEnd.confirming = scheduledPauses?.pause?.value?.restricts == true
            }

            SessionWindowRequest.SESSION -> {}
        }
        consumeWindowRequest()
    }
    val scheduledView = scheduledPauseView(scheduledPauses, state, timeFormat, clock)
    SessionScreen(
        state = state,
        scheduled = scheduledView,
        scheduledEnd = scheduledEnd.actions(scheduledView?.restricts == true),
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
        macActions = macSetupState?.callbacks(state.blocksHelperRemoval(), onMacSetupAnnouncement) ?: MacSetupCallbacks(),
        macLoginItemEnabled = loginItemEnabled,
    )
}

@Composable
private fun scheduledPauseView(
    scheduledPauses: ScheduledPauses?,
    state: SessionUiState,
    timeFormat: SessionTimeFormat,
    clock: SessionClock,
): ScheduledPauseView? {
    val scheduled = scheduledPauses?.pause?.collectAsState()?.value ?: return null
    val manualEnd = (state.status as? LocalSessionStatus.Active)?.record?.endEpochMillis ?: 0L
    val until = timeFormat.formatTime(maxOf(scheduled.endEpochMillis, manualEnd), clock.currentEpochMillis())
    return ScheduledPauseView(scheduled.name, until, scheduled.state)
}

/** Asks before ending a scheduled pause; the menu's End early opens the same question. */
@Stable
private class ScheduledEndState(
    private val confirmManualEnd: (Boolean) -> Unit,
    private val endManual: () -> Unit,
    private val endScheduled: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    var confirming by mutableStateOf(false)

    fun actions(restricts: Boolean): ScheduledEndActions {
        return ScheduledEndActions(
            confirming = confirming && restricts,
            onRequest = {
                confirmManualEnd(true)
                confirming = true
            },
            onConfirm = {
                confirming = false
                // End early ends the manual session too, then every occurrence running here.
                endManual()
                scope.launch { endScheduled() }
            },
            onCancel = {
                confirming = false
                confirmManualEnd(false)
            },
        )
    }
}

/** End early for a scheduled pause: it asks first, then ends the manual session and every occurrence running here. */
internal class ScheduledEndActions(
    val confirming: Boolean = false,
    val onRequest: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

private val PosatoLayout.screenInset: Dp
    get() {
        return if (this == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
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
    macActions: MacSetupCallbacks = MacSetupCallbacks(),
    macLoginItemEnabled: Boolean? = null,
    scheduled: ScheduledPauseView? = null,
    scheduledEnd: ScheduledEndActions = ScheduledEndActions(),
) {
    key(state.isSettingUp, state.isReviewing, state.confirmingEarlyEnd, scheduledEnd.confirming) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(layout.screenInset),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            SessionOperationNotice(state, onRetry)
            when {
                state.status == null -> {}

                scheduled != null && scheduledEnd.confirming -> {
                    ScheduledEarlyEndContent(scheduled, layout, scheduledEnd.onConfirm, scheduledEnd.onCancel)
                }

                state.confirmingEarlyEnd -> {
                    SessionEarlyEndContent(state, layout, onConfirmEarlyEnd, onCancelEarlyEnd)
                }

                macSetup != null && state.showsMacSetup(macSetup) -> {
                    SessionMacSetup(state, macSetup, layout, macActions, onExitSetup)
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
                        macActions,
                        macLoginItemEnabled,
                        scheduled,
                        scheduledEnd.onRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionEarlyEndContent(
    state: SessionUiState,
    layout: PosatoLayout,
    onConfirmEarlyEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
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
}

@Composable
private fun SessionOperationNotice(
    state: SessionUiState,
    onRetry: () -> Unit
) {
    if (state.status == null && state.operationFailure == null) {
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
            CircularProgressIndicator()
            Text("Loading session")
        }
    } else {
        state.operationFailure?.let {
            PosatoNotice(tone = PosatoTone.Critical, actionContent = { PosatoButton(onRetry) { Text("Retry") } }) {
                Text(stringResource(it.operationMessage()))
            }
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
