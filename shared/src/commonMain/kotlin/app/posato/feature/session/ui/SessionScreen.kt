package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.core.designsystem.CardSurface
import app.posato.core.designsystem.LabeledProgress
import app.posato.core.designsystem.NoticeCard
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.LocalSessionStatus.Active
import app.posato.feature.session.domain.LocalSessionStatus.Ended
import app.posato.feature.session.domain.LocalSessionStatus.Inactive
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_confirm_end_session
import app.posato.generated.resources.action_end_session_early
import app.posato.generated.resources.action_open_paused_items
import app.posato.generated.resources.action_open_session
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.action_start_session
import app.posato.generated.resources.end_session_confirm_body
import app.posato.generated.resources.end_session_confirm_title
import app.posato.generated.resources.no_session_active
import app.posato.generated.resources.no_session_description
import app.posato.generated.resources.operation_error_title
import app.posato.generated.resources.paused_items_title
import app.posato.generated.resources.session_active_until
import app.posato.generated.resources.session_duration_decrease
import app.posato.generated.resources.session_duration_increase
import app.posato.generated.resources.session_duration_label
import app.posato.generated.resources.session_ended_early
import app.posato.generated.resources.session_ended_expired
import app.posato.generated.resources.session_ending
import app.posato.generated.resources.session_items_applications
import app.posato.generated.resources.session_items_empty
import app.posato.generated.resources.session_items_unavailable
import app.posato.generated.resources.session_items_websites
import app.posato.generated.resources.session_loading
import app.posato.generated.resources.session_remaining_minutes
import app.posato.generated.resources.session_remaining_soon
import app.posato.generated.resources.session_review_description
import app.posato.generated.resources.session_review_end
import app.posato.generated.resources.session_review_title
import app.posato.generated.resources.session_setup_continue
import app.posato.generated.resources.session_setup_preview
import app.posato.generated.resources.session_setup_title
import app.posato.generated.resources.session_starting
import app.posato.generated.resources.session_status_description
import app.posato.generated.resources.session_title
import org.jetbrains.compose.resources.StringResource
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
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(sessionStore, policyStore, applicationMappings, sessionIds, clock, timeFormat)
    },
) {
    val state by viewModel.uiState.collectAsState()

    SessionScreen(
        state = state,
        onEnterSetup = { viewModel.setSetupVisible(true) },
        onExitSetup = { viewModel.setSetupVisible(false) },
        onAdjustDuration = viewModel::adjustDuration,
        onSubmitDuration = viewModel::submitDurationMinutes,
        onEnterReview = { viewModel.setReviewVisible(true) },
        onExitReview = { viewModel.setReviewVisible(false) },
        onStartSession = viewModel::startSession,
        onRequestEarlyEnd = { viewModel.setEarlyEndConfirmation(true) },
        onCancelEarlyEnd = { viewModel.setEarlyEndConfirmation(false) },
        onConfirmEarlyEnd = viewModel::confirmEarlyEnd,
        onRetry = viewModel::retry,
        onOpenPausedItems = onOpenPausedItems,
        modifier = modifier,
    )
}

@Composable
internal fun SessionScreen(
    state: SessionUiState,
    onEnterSetup: () -> Unit,
    onExitSetup: () -> Unit,
    onAdjustDuration: (Int) -> Unit,
    onSubmitDuration: (String) -> Unit,
    onEnterReview: () -> Unit,
    onExitReview: () -> Unit,
    onStartSession: () -> Unit,
    onRequestEarlyEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
    onConfirmEarlyEnd: () -> Unit,
    onRetry: () -> Unit,
    onOpenPausedItems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.status == null && state.operationFailure == null -> SessionLoading()
            state.operationFailure != null && state.status == null -> SessionLoadFailure(state.operationFailure, onRetry)
            state.isSettingUp && !state.isReviewing -> SessionSetupSection(state, onAdjustDuration, onSubmitDuration, onEnterReview, onExitSetup)
            state.isReviewing -> SessionReviewSection(state, onStartSession, onExitReview, onRetry, onOpenPausedItems)
            else -> SessionStatusSection(state, onEnterSetup, onRequestEarlyEnd, onOpenPausedItems)
        }
        if (state.confirmingEarlyEnd) {
            EarlyEndConfirmDialog(onCancelEarlyEnd, onConfirmEarlyEnd)
        }
    }
}

@Composable
internal fun SessionDestinationSwitch(
    onOpenSession: () -> Unit,
    onOpenPausedItems: () -> Unit,
    showingSession: Boolean,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        SegmentedButton(
            selected = showingSession,
            onClick = onOpenSession,
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(Res.string.action_open_session)) }
        SegmentedButton(
            selected = !showingSession,
            onClick = onOpenPausedItems,
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(Res.string.paused_items_title)) }
    }
}

@Composable
private fun SessionLoading(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.session_loading)
    Box(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SessionLoadFailure(
    failure: SessionOperationFailure?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
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
private fun EarlyEndConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.end_session_confirm_title)) },
        text = { Text(stringResource(Res.string.end_session_confirm_body)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(Res.string.action_confirm_end_session)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Composable
private fun SessionPhonePreview(
    @PreviewParameter(SessionScreenPreviewDataProvider::class) previewState: SessionScreenPreviewDataProvider.SessionPreviewState,
) {
    PosatoTheme {
        SessionScreen(previewState.state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Desktop", widthDp = 900, heightDp = 720)
@Composable
private fun SessionDesktopPreview(
    @PreviewParameter(SessionScreenPreviewDataProvider::class) previewState: SessionScreenPreviewDataProvider.SessionPreviewState,
) {
    PosatoTheme {
        SessionScreen(previewState.state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
