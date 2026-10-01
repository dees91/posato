package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformDevice
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacHelperReadinessNotice
import app.posato.feature.onboarding.MacSetupOffer
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.onboarding.MacSetupSection
import app.posato.feature.onboarding.needsSetup
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.sync.ui.SyncBootstrapSection
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.ui.TargetsCategory
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_unavailable
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
    onEditItems: (TargetsCategory) -> Unit,
    onRetryEnforcement: () -> Unit = {},
    syncState: SyncBootstrapUiState? = null,
    macSetup: MacSetupPresentation? = null,
    macActions: MacSetupCallbacks = MacSetupCallbacks(),
    macLoginItemEnabled: Boolean? = null,
    scheduled: ScheduledPauseView? = null,
    onEndSchedule: () -> Unit = {},
) {
    val scheduledRestricts = scheduled?.restricts == true
    val active = state.status is LocalSessionStatus.Active || scheduledRestricts
    val needsMacSetup = macSetup?.needsSetup() == true
    val hasItems = state.displayDomains().isNotEmpty() ||
        (state.review.applicationGroupName != null && (state.displayApplicationCount() ?: 0) > 0)
    var macSetupExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        SessionHero(layout, overviewDescription(active, scheduledRestricts, needsMacSetup, hasItems), active, scheduledRestricts)
        // While a schedule restricts this Mac, a received session needs no Resume here.
        if (!scheduledRestricts) {
            EnforcementNotice(state, onRetryEnforcement)
        }
        MacSetupNotice(macSetup, macSetupExpanded, state.enforcement, active)
        if (scheduled != null && scheduledRestricts) {
            ScheduledPauseRunning(scheduled, onEndSchedule)
        } else if (active) {
            PosatoEndTime("Until ${state.formattedActiveEnd.orEmpty()}", supportingText = state.remainingMillis?.let { remainingText(it) })
            state.setName?.let { name -> PosatoCaption("Set: $name, until ${state.formattedActiveEnd.orEmpty()}") }
            PosatoButton(onEnd, style = PosatoButtonStyle.Quiet, enabled = state.canRequestEarlyEnd()) { Text("End session early") }
        } else {
            SessionEndedCaption(state)
            scheduled?.let { ScheduledPauseAttention(it, macSetup, macActions, onSetup) }
            SessionStartAction(state, needsMacSetup, hasItems, onSetup, onItems)
            macSetup?.takeIf { it.offerVisible }?.let { SessionSetupOffer(it, macActions, onSetup) }
        }
        FrozenSetCaption(state)
        SessionSelectionSummary(state, deviceLabel, onEditItems)
        PosatoCaption("Saved on this device. Restrictions apply only during a pause you start or a schedule you set.")
        SyncSection(syncState)
        macSetup?.let { presentation ->
            MacSetupSection(
                presentation,
                expanded = macSetupExpanded,
                onToggle = { macSetupExpanded = !macSetupExpanded },
                onCheck = macActions.check,
                onEnable = macActions.enable,
                onOpenSettings = macActions.openSettings,
                onAnnouncement = macActions.announce,
                sessionBlocksRemoval = state.blocksHelperRemoval(),
                onRemove = macActions.remove,
                loginItemEnabled = macLoginItemEnabled,
                onLoginItemChange = macActions.loginItemChange,
                onStandingGrantChange = macActions.standingGrantChange,
                onSetUp = macActions.setUp,
            )
        }
    }
}

/** The compact invitation opens the shared setup screen and starts the run there. */
@Composable
private fun SessionSetupOffer(
    macSetup: MacSetupPresentation,
    macActions: MacSetupCallbacks,
    onOpenSetup: () -> Unit,
) {
    PosatoPanel {
        MacSetupOffer(macSetup, onSetUp = {
            macActions.open()
            macActions.setUp()
            onOpenSetup()
        }, onDismiss = macActions.dismissOffer)
    }
}

@Composable
private fun SessionStartAction(
    state: SessionUiState,
    needsMacSetup: Boolean,
    hasItems: Boolean,
    onSetup: () -> Unit,
    onItems: () -> Unit,
) {
    PosatoButton(if (needsMacSetup || hasItems) onSetup else onItems, enabled = state.canEnterSetup()) {
        Text(
            when {
                needsMacSetup -> "Finish setup"
                hasItems -> "Start a session"
                else -> "Choose paused items"
            },
        )
    }
}

@Composable
private fun MacSetupNotice(
    macSetup: MacSetupPresentation?,
    expanded: Boolean,
    enforcement: EnforcementState,
    active: Boolean,
) {
    if (macSetup == null) return
    if (!active && macSetup.needsSetup() && enforcement is EnforcementState.Inactive) {
        PosatoNotice(tone = PosatoTone.Caution) {
            Text("Setup incomplete. Posato is not blocking websites or apps on this Mac. Finish setup to start a pause.")
        }
        return
    }
    val readiness = macSetup.readiness ?: return
    if (expanded || macSetup.activity != null || readiness == MacHelperReadiness.READY) {
        return
    }
    if (enforcement is EnforcementState.Active) {
        return
    }
    MacHelperReadinessNotice(readiness, Res.string.mac_setup_unavailable, announceChanges = false)
}

@Composable
private fun SyncSection(syncState: SyncBootstrapUiState?) {
    syncState?.let { SyncBootstrapSection(it) }
}

@Composable
private fun EnforcementNotice(
    state: SessionUiState,
    onRetryEnforcement: () -> Unit,
) {
    when (val enforcement = state.enforcement) {
        is EnforcementState.Active -> {
            if (enforcement.belowPlatformMinimum) {
                PosatoCaption("Short pause — ${platformDevice().noun} restricts it only while the app stays open.")
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
            "Restrictions not active on this Mac."
        }
    }
}

@Composable
private fun FrozenSetCaption(state: SessionUiState) {
    if (!state.showsFrozenSet()) {
        return
    }
    PosatoCaption(
        if (state.showsPersistedStartSet()) {
            "These counts stay as they were at session start. The actions below edit current Paused items, which restrictions follow."
        } else {
            "Showing your current Paused items."
        },
    )
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
    onEditItems: (TargetsCategory) -> Unit,
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
            state.setName?.let { name -> Text("Set: $name", style = MaterialTheme.typography.titleMedium) }
            state.review.actionRequired?.let { required -> SessionReadinessNotice(required, onRetry, onEditItems) }
            PosatoActionRow {
                if (state.review.actionRequired == SessionActionRequired.NO_EFFECTIVE_ITEMS) {
                    PosatoButton(onItems) { Text("Add websites or apps") }
                } else {
                    PosatoButton(onStart, enabled = state.canStart()) { Text(if (state.isStarting) "Starting…" else "Start this pause") }
                }
                PosatoButton(onChangeDuration, style = PosatoButtonStyle.Quiet, enabled = !state.isStarting) { Text("Change duration") }
            }
            SessionSelectionSummary(state, deviceLabel, onEditItems)
            PosatoCaption("This starts the session and applies the chosen restrictions on this device.")
        }
    }
}

@Composable
private fun SessionReadinessNotice(
    required: SessionActionRequired,
    onRetry: () -> Unit,
    onEditItems: (TargetsCategory) -> Unit,
) {
    val noun = remember { platformDevice().noun }
    PosatoNotice(tone = PosatoTone.Caution, actionContent = {
        when (required) {
            SessionActionRequired.MAPPINGS_LOAD_FAILED -> {
                PosatoButton(onRetry) { Text("Retry") }
            }

            SessionActionRequired.MAPPINGS_NOT_CHOSEN, SessionActionRequired.ACCESS_REQUIRED -> {
                PosatoButton(
                    { onEditItems(TargetsCategory.APPLICATIONS) },
                    style = PosatoButtonStyle.Quiet,
                ) { Text("Choose apps") }
            }

            SessionActionRequired.NO_EFFECTIVE_ITEMS -> {}
        }
    }) { Text(stringResource(required.actionMessage(), noun)) }
}
