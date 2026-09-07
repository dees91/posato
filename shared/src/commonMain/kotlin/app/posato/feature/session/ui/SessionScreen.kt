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
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionScreen(
    sessionStore: LocalSessionStore,
    policyStore: LocalTargetPolicyStore,
    applicationMappings: LocalApplicationMappings,
    sessionIds: SessionIdGenerator,
    clock: SessionClock,
    timeFormat: SessionTimeFormat,
    onOpenPausedItems: () -> Unit,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Compact,
    deviceLabel: String = "On this device",
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(sessionStore, policyStore, applicationMappings, sessionIds, clock, timeFormat)
    },
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refreshTargets() }
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
        onOpenPausedItems = onOpenPausedItems,
        modifier = modifier,
        layout = layout,
        deviceLabel = deviceLabel,
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
    onOpenPausedItems: () -> Unit = {},
) {
    key(state.isSettingUp, state.isReviewing, state.confirmingEarlyEnd) {
        val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            state.operationFailure?.let { failure ->
                PosatoNotice(tone = PosatoTone.Critical, actionContent = { PosatoButton(onRetry) { Text("Retry") } }) {
                    Text(stringResource(failure.operationMessage()))
                }
            }
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
                        description = "You can end this session early. Your saved choices will stay ready for another time.",
                        layout = layout,
                    )
                    PosatoButton(onConfirmEarlyEnd, enabled = !state.isEnding) { Text("End session") }
                    PosatoButton(onCancelEarlyEnd, style = PosatoButtonStyle.Quiet, enabled = !state.isEnding) { Text("Keep this pause") }
                }

                state.isReviewing -> {
                    SessionReviewContent(state, layout, deviceLabel, onStartSession, onExitReview, onOpenPausedItems, onRetry)
                }

                state.isSettingUp -> {
                    SessionDurationContent(state, layout, onSetDuration, onEnterReview, onExitSetup)
                }

                else -> {
                    SessionOverviewContent(state, layout, deviceLabel, onEnterSetup, onRequestEarlyEnd, onOpenPausedItems)
                }
            }
        }
    }
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Composable
private fun SessionPhonePreview(
    @PreviewParameter(SessionScreenPreviewDataProvider::class) previewState: SessionScreenPreviewDataProvider.SessionPreviewState,
) {
    PosatoTheme { SessionScreen(previewState.state) }
}

@Preview(name = "Desktop", widthDp = 1060, heightDp = 780)
@Composable
private fun SessionDesktopPreview(
    @PreviewParameter(SessionScreenPreviewDataProvider::class) previewState: SessionScreenPreviewDataProvider.SessionPreviewState,
) {
    PosatoTheme { SessionScreen(previewState.state, layout = PosatoLayout.Expanded) }
}
