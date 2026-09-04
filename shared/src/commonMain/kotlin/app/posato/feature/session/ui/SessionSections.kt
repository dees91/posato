package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.posato.core.designsystem.CardSurface
import app.posato.core.designsystem.LabeledProgress
import app.posato.core.designsystem.NoticeCard
import app.posato.feature.session.domain.LocalSessionStatus.Active
import app.posato.feature.session.domain.LocalSessionStatus.Ended
import app.posato.feature.session.domain.LocalSessionStatus.Inactive
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionEndKind
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_end_session_early
import app.posato.generated.resources.action_open_paused_items
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.action_start_session
import app.posato.generated.resources.no_session_active
import app.posato.generated.resources.no_session_description
import app.posato.generated.resources.operation_error_title
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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionStatusSection(
    state: SessionUiState,
    onEnterSetup: () -> Unit,
    onRequestEarlyEnd: () -> Unit,
    onOpenPausedItems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(Res.string.session_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.session_status_description), style = MaterialTheme.typography.bodyLarge)
            }
            state.operationFailure?.let { failure ->
                item { NoticeCard(failure.operationMessage(), Modifier.fillMaxWidth()) }
            }
            when (val status = state.status) {
                is Active -> item { SessionActiveContent(state, onRequestEarlyEnd, Modifier.fillMaxWidth()) }
                is Ended -> item { SessionEndedContent(state, status, onEnterSetup, Modifier.fillMaxWidth()) }
                is Inactive, null -> item { SessionInactiveContent(state, onEnterSetup, Modifier.fillMaxWidth()) }
            }
            item {
                TextButton(onClick = onOpenPausedItems, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.action_open_paused_items))
                }
            }
        }
    }
}

@Composable
private fun SessionActiveContent(
    state: SessionUiState,
    onRequestEarlyEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status as Active
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.session_active_until, state.formattedActiveEnd.orEmpty()),
            style = MaterialTheme.typography.titleLarge,
        )
        state.remainingMillis?.let { remaining ->
            Text(remainingText(remaining), style = MaterialTheme.typography.bodyLarge)
        }
        SessionItemsSummary(state, Modifier.fillMaxWidth())
        Button(onClick = onRequestEarlyEnd, enabled = state.canRequestEarlyEnd(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_end_session_early))
        }
        if (state.isEnding) {
            LabeledProgress(Res.string.session_ending, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SessionEndedContent(
    state: SessionUiState,
    status: Ended,
    onEnterSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.no_session_active), style = MaterialTheme.typography.titleLarge)
        Text(
            when (status.kind) {
                SessionEndKind.EXPIRED -> stringResource(
                    Res.string.session_ended_expired,
                    state.formattedActiveEnd.orEmpty(),
                )

                SessionEndKind.ENDED_EARLY -> stringResource(Res.string.session_ended_early)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onEnterSetup, enabled = state.canEnterSetup(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_start_session))
        }
    }
}

@Composable
private fun SessionInactiveContent(
    state: SessionUiState,
    onEnterSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.no_session_active), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(Res.string.no_session_description), style = MaterialTheme.typography.bodyLarge)
        SessionItemsSummary(state, Modifier.fillMaxWidth())
        Button(onClick = onEnterSetup, enabled = state.canEnterSetup(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_start_session))
        }
    }
}

@Composable
internal fun SessionSetupSection(
    state: SessionUiState,
    onAdjustDuration: (Int) -> Unit,
    onSubmitDuration: (String) -> Unit,
    onEnterReview: () -> Unit,
    onExitSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(Res.string.session_setup_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(state.setupFailure.setupMessage()), style = MaterialTheme.typography.bodyLarge)
            }
            item {
                key(state.durationMinutes) {
                    val inputState = rememberTextFieldState(initialText = state.durationMinutes.toString())
                    OutlinedTextField(
                        state = inputState,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.session_duration_label)) },
                        isError = state.setupFailure != null,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        onKeyboardAction = { onSubmitDuration(inputState.text.toString()) },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAdjustDuration(-1) }) { Text(stringResource(Res.string.session_duration_decrease)) }
                    TextButton(onClick = { onAdjustDuration(1) }) { Text(stringResource(Res.string.session_duration_increase)) }
                }
            }
            state.formattedPreviewEnd?.let { preview ->
                item { Text(stringResource(Res.string.session_setup_preview, preview), style = MaterialTheme.typography.bodyLarge) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEnterReview) { Text(stringResource(Res.string.session_setup_continue)) }
                    TextButton(onClick = onExitSetup) { Text(stringResource(Res.string.action_cancel)) }
                }
            }
        }
    }
}

@Composable
internal fun SessionReviewSection(
    state: SessionUiState,
    onStartSession: () -> Unit,
    onExitReview: () -> Unit,
    onRetry: () -> Unit,
    onOpenPausedItems: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(Res.string.session_review_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.session_review_description), style = MaterialTheme.typography.bodyLarge)
            }
            if (!state.isReviewReady) {
                item { LabeledProgress(Res.string.session_loading, Modifier.fillMaxWidth()) }
            } else {
                state.formattedReviewEnd?.let { end ->
                    item { Text(stringResource(Res.string.session_review_end, end), style = MaterialTheme.typography.titleLarge) }
                }
                item { SessionItemsSummary(state, Modifier.fillMaxWidth()) }
                state.review.actionRequired?.let { required ->
                    item {
                        NoticeCard(
                            message = required.actionMessage(),
                            modifier = Modifier.fillMaxWidth(),
                            title = Res.string.operation_error_title,
                        ) {
                            if (required == SessionActionRequired.MAPPINGS_LOAD_FAILED) {
                                TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                            } else {
                                TextButton(onClick = onOpenPausedItems) { Text(stringResource(Res.string.action_open_paused_items)) }
                            }
                        }
                    }
                }
                state.operationFailure?.let { failure ->
                    item {
                        NoticeCard(
                            message = failure.operationMessage(),
                            modifier = Modifier.fillMaxWidth(),
                            title = Res.string.operation_error_title,
                        ) {
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartSession, enabled = state.canStart()) {
                            Text(stringResource(Res.string.action_start_session))
                        }
                        TextButton(onClick = onExitReview) { Text(stringResource(Res.string.action_cancel)) }
                    }
                }
                if (state.isStarting) {
                    item { LabeledProgress(Res.string.session_starting, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
internal fun SessionItemsSummary(
    state: SessionUiState,
    modifier: Modifier = Modifier,
) {
    val review = state.review
    CardSurface(modifier = modifier) {
        Text(stringResource(Res.string.session_items_websites, review.domains.size), style = MaterialTheme.typography.bodyLarge)
        review.domains.forEach { domain ->
            Text(domain, style = MaterialTheme.typography.bodyMedium)
        }
        val mappingCount = review.selectedMappingCount
        review.applicationGroupName?.let { group ->
            Text(group, style = MaterialTheme.typography.bodyLarge)
        }
        if (mappingCount != null) {
            Text(
                stringResource(Res.string.session_items_applications, mappingCount),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(stringResource(Res.string.session_items_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
        if (review.domains.isEmpty() && (mappingCount ?: 0) == 0 && mappingCount != null) {
            Text(stringResource(Res.string.session_items_empty), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun remainingText(remainingMillis: Long): String {
    return if (remainingMillis < MILLIS_PER_MINUTE) {
        stringResource(Res.string.session_remaining_soon)
    } else {
        stringResource(Res.string.session_remaining_minutes, (remainingMillis / MILLIS_PER_MINUTE).toInt())
    }
}

private const val MILLIS_PER_MINUTE: Long = 60_000L
