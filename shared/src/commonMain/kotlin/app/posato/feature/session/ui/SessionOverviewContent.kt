package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoEndTime
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoHero
import app.posato.core.designsystem.PosatoIntervalArtwork
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionEndKind
import app.posato.generated.resources.Res
import app.posato.generated.resources.session_ended_early
import app.posato.generated.resources.session_ended_expired
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionOverviewContent(
    state: SessionUiState,
    layout: PosatoLayout,
    deviceLabel: String,
    onSetup: () -> Unit,
    onEnd: () -> Unit,
    onItems: () -> Unit,
    onRetryEnforcement: () -> Unit = {},
) {
    val active = state.status is LocalSessionStatus.Active
    val hasItems = state.displayDomains().isNotEmpty() ||
        (state.review.applicationGroupName != null && (state.displayApplicationCount() ?: 0) > 0)
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHero(layout = layout, artworkContent = { PosatoIntervalArtwork() }, headingContent = {
            PosatoHeading(
                title = if (active) "A little room.\nJust for you." else "Room for what matters.",
                eyebrow = if (active) "SESSION ACTIVE" else "NO SESSION ACTIVE",
                description = when {
                    active -> "Your session timer is running on this device."
                    !hasItems -> "Start with a website or app you’d like a little space from."
                    else -> "A quiet pause is ready when you are. Choose a little space from the things that pull you away."
                },
                layout = layout,
            )
        })
        EnforcementNotice(state, onRetryEnforcement)
        if (active) {
            PosatoEndTime("Until ${state.formattedActiveEnd.orEmpty()}", supportingText = state.remainingMillis?.let { remainingText(it) })
            PosatoButton(onEnd, style = PosatoButtonStyle.Quiet, enabled = state.canRequestEarlyEnd()) { Text("End session early") }
        } else {
            SessionEndedCaption(state)
            PosatoButton(if (hasItems) onSetup else onItems, enabled = state.canEnterSetup()) {
                Text(if (hasItems) "Start a session" else "Choose paused items")
            }
        }
        SessionSelectionSummary(state, deviceLabel)
        if (state.showsFrozenSet()) {
            PosatoCaption("Changes in Paused items apply to the next pause.")
        }
        PosatoCaption("Saved on this device. Restrictions apply only while a session is active.")
    }
}

@Composable
private fun EnforcementNotice(
    state: SessionUiState,
    onRetryEnforcement: () -> Unit,
) {
    when (val enforcement = state.enforcement) {
        is EnforcementState.Active -> {
            if (enforcement.belowPlatformMinimum) {
                PosatoCaption("Short pause — iPhone restricts it only while the app stays open.")
            } else {
                PosatoCaption("Restrictions active.")
            }
        }

        is EnforcementState.ActionRequired -> {
            PosatoNotice(
                tone = PosatoTone.Critical,
                actionContent = {
                    PosatoButton(onRetryEnforcement, enabled = !state.enforcementBusy) {
                        Text(if (enforcement.kind == EnforcementActionKind.RESUME_REQUIRED) "Resume restrictions" else "Retry")
                    }
                },
            ) {
                Text(enforcement.attentionMessage())
            }
        }

        is EnforcementState.Inactive -> {}
    }
}

private fun EnforcementState.ActionRequired.attentionMessage(): String {
    return when (kind) {
        EnforcementActionKind.APPLY_FAILED -> {
            if (repeatsSystemPrompt) {
                "Restrictions need attention — approving again applies them."
            } else {
                "Restrictions need attention."
            }
        }

        EnforcementActionKind.CLEAR_FAILED -> {
            "Restrictions may still apply — retry clearing them."
        }

        EnforcementActionKind.RESUME_REQUIRED -> {
            "Restrictions stopped when the app closed."
        }
    }
}

@Composable
private fun SessionEndedCaption(state: SessionUiState) {
    val ended = state.status as? LocalSessionStatus.Ended ?: return
    PosatoCaption(
        when (ended.kind) {
            SessionEndKind.ENDED_EARLY -> stringResource(Res.string.session_ended_early)
            SessionEndKind.EXPIRED -> stringResource(Res.string.session_ended_expired, state.formattedActiveEnd.orEmpty())
        },
    )
}

@Composable
internal fun SessionReviewContent(
    state: SessionUiState,
    layout: PosatoLayout,
    deviceLabel: String,
    onStart: () -> Unit,
    onChangeDuration: () -> Unit,
    onItems: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            title = if (state.review.actionRequired?.blocksStart == true) "A small step first." else "Your pause, your choice.",
            eyebrow = "ONE LAST LOOK",
            description = "Review your saved choices and the ending time for this session.",
            layout = layout,
        )
        if (!state.isReviewReady) {
            CircularProgressIndicator()
        } else {
            PosatoEndTime(
                "Until ${state.formattedReviewEnd.orEmpty()}",
                Modifier.fillMaxWidth(),
                "${state.durationMinutes} minutes · you stay in control",
            )
            state.review.actionRequired?.let { required ->
                PosatoNotice(tone = PosatoTone.Caution, actionContent = {
                    PosatoButton(if (required == SessionActionRequired.MAPPINGS_LOAD_FAILED) onRetry else onItems) {
                        Text(if (required == SessionActionRequired.MAPPINGS_LOAD_FAILED) "Retry" else "Review paused items")
                    }
                }) { Text(stringResource(required.actionMessage())) }
            }
            PosatoActionRow {
                PosatoButton(onStart, enabled = state.canStart()) { Text(if (state.isStarting) "Starting…" else "Start this pause") }
                PosatoButton(onChangeDuration, style = PosatoButtonStyle.Quiet, enabled = !state.isStarting) { Text("Change duration") }
            }
            SessionSelectionSummary(state, deviceLabel)
            PosatoCaption("This starts the session and applies the chosen restrictions on this device.")
        }
    }
}
