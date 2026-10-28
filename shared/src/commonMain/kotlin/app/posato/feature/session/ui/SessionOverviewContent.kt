package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoActivityIndicator
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
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.designsystem.stringResourceForDevice
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
import app.posato.generated.resources.action_add_websites_or_apps
import app.posato.generated.resources.action_change_duration
import app.posato.generated.resources.action_choose_paused_items
import app.posato.generated.resources.action_end_session_early
import app.posato.generated.resources.action_finish_setup
import app.posato.generated.resources.action_resume_restrictions
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.action_start_a_session
import app.posato.generated.resources.action_start_this_pause
import app.posato.generated.resources.mac_setup_unavailable
import app.posato.generated.resources.schedule_set_label
import app.posato.generated.resources.session_action_no_items_computer
import app.posato.generated.resources.session_action_no_items_ipad
import app.posato.generated.resources.session_action_no_items_iphone
import app.posato.generated.resources.session_action_no_items_mac
import app.posato.generated.resources.session_action_no_items_phone
import app.posato.generated.resources.session_applying
import app.posato.generated.resources.session_ended_early
import app.posato.generated.resources.session_ended_expired
import app.posato.generated.resources.session_frozen_counts
import app.posato.generated.resources.session_restrictions_active
import app.posato.generated.resources.session_restrictions_attention
import app.posato.generated.resources.session_restrictions_attention_again
import app.posato.generated.resources.session_restrictions_clear
import app.posato.generated.resources.session_restrictions_not_active_mac
import app.posato.generated.resources.session_review_eyebrow
import app.posato.generated.resources.session_review_heading
import app.posato.generated.resources.session_review_heading_blocked
import app.posato.generated.resources.session_review_lead
import app.posato.generated.resources.session_saved_on_device
import app.posato.generated.resources.session_setup_incomplete
import app.posato.generated.resources.session_short_pause_computer
import app.posato.generated.resources.session_short_pause_ipad
import app.posato.generated.resources.session_short_pause_iphone
import app.posato.generated.resources.session_short_pause_mac
import app.posato.generated.resources.session_short_pause_phone
import app.posato.generated.resources.session_showing_set
import app.posato.generated.resources.session_start_applies
import app.posato.generated.resources.session_starting_ellipsis
import app.posato.generated.resources.session_stay_in_control
import app.posato.generated.resources.session_until
import app.posato.generated.resources.session_until_end_of_day
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
    pauseNotice: String? = null,
    onShowItems: ((TargetsCategory) -> Unit)? = null,
) {
    val scheduledRestricts = scheduled?.restricts == true
    val active = state.status is LocalSessionStatus.Active || scheduledRestricts
    val needsMacSetup = macSetup?.needsSetup() == true
    val hasItems = state.hasPausableItems()
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
            PosatoEndTime(
                stringResource(Res.string.session_until, state.formattedActiveEnd.orEmpty()),
                supportingText = state.remainingMillis?.let {
                    remainingText(it)
                },
            )
            state.setName?.let { name -> PosatoCaption(stringResource(Res.string.schedule_set_label, name)) }
            PosatoButton(onEnd, style = PosatoButtonStyle.Quiet, enabled = state.canRequestEarlyEnd(), controlId = "action_end_session_early") {
                Text(stringResource(Res.string.action_end_session_early))
            }
        } else {
            SessionEndedCaption(state)
            scheduled?.let { ScheduledPauseAttention(it, macSetup, macActions, onSetup) }
            SessionStartAction(state, needsMacSetup, hasItems, onSetup, onItems)
            macSetup?.takeIf { it.offerVisible }?.let { SessionSetupOffer(it, macActions, onSetup) }
        }
        NotPausedYetNotice(pauseNotice.takeIf { active })
        // The summary describes this device's default set or a session's set; a running schedule's parts name their own sets.
        if (!scheduledRestricts || state.status is LocalSessionStatus.Active) {
            FrozenSetCaption(state)
            SessionSelectionSummary(state, deviceLabel, onEditItems, onShowItems)
        }
        PosatoCaption(stringResource(Res.string.session_saved_on_device))
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
    PosatoButton(
        if (needsMacSetup || hasItems) onSetup else onItems,
        enabled = state.canEnterSetup(),
        controlId = "action_start_a_session",
    ) {
        Text(
            when {
                needsMacSetup -> stringResource(Res.string.action_finish_setup)
                hasItems -> stringResource(Res.string.action_start_a_session)
                else -> stringResource(Res.string.action_choose_paused_items)
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
            Text(stringResource(Res.string.session_setup_incomplete))
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
    val shortPause = stringResourceForDevice(
        Res.string.session_short_pause_mac,
        Res.string.session_short_pause_iphone,
        Res.string.session_short_pause_ipad,
        Res.string.session_short_pause_computer,
        Res.string.session_short_pause_phone,
    )
    val restrictionsActive = stringResource(Res.string.session_restrictions_active)
    if (state.enforcementRecovering && state.status is LocalSessionStatus.Active && state.enforcement is EnforcementState.ActionRequired) {
        PosatoCaption(stringResource(Res.string.session_applying))
        return
    }
    when (val enforcement = state.enforcement) {
        is EnforcementState.Active -> {
            PosatoCaption(if (enforcement.belowPlatformMinimum) shortPause else restrictionsActive)
        }

        is EnforcementState.ActionRequired -> {
            PosatoNotice(
                tone = PosatoTone.Critical,
                actionContent = {
                    PosatoButton(onRetryEnforcement, enabled = !state.enforcementBusy, controlId = "action_retry") {
                        Text(
                            if (enforcement.kind ==
                                EnforcementActionKind.RESUME_REQUIRED
                            ) {
                                stringResource(Res.string.action_resume_restrictions)
                            } else {
                                stringResource(Res.string.action_retry)
                            },
                        )
                    }
                },
            ) {
                Text(enforcement.attentionMessage())
            }
        }

        is EnforcementState.Inactive -> {}
    }
}

@Composable
private fun EnforcementState.ActionRequired.attentionMessage(): String {
    return when (kind) {
        EnforcementActionKind.APPLY_FAILED -> {
            if (repeatsSystemPrompt) {
                stringResource(Res.string.session_restrictions_attention_again)
            } else {
                stringResource(Res.string.session_restrictions_attention)
            }
        }

        EnforcementActionKind.CLEAR_FAILED -> {
            stringResource(Res.string.session_restrictions_clear)
        }

        EnforcementActionKind.RESUME_REQUIRED -> {
            stringResource(Res.string.session_restrictions_not_active_mac)
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
            stringResource(Res.string.session_frozen_counts)
        } else {
            stringResource(Res.string.session_showing_set)
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
    onShowItems: ((TargetsCategory) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            title = if (state.review.actionRequired?.blocksStart ==
                true
            ) {
                stringResource(Res.string.session_review_heading_blocked)
            } else {
                stringResource(Res.string.session_review_heading)
            },
            eyebrow = stringResource(Res.string.session_review_eyebrow).takeUnless { platformUsesCupertinoChrome },
            description = stringResource(Res.string.session_review_lead),
            layout = layout,
        )
        val untilEndOfDay = stringResource(Res.string.session_until_end_of_day)
        val length = sessionLengthText(state.durationMinutes)
        val stayInControl = stringResource(Res.string.session_stay_in_control, if (state.untilEndOfDay) untilEndOfDay else length)
        if (!state.isReviewReady) {
            PosatoActivityIndicator()
        } else {
            PosatoEndTime(
                stringResource(Res.string.session_until, state.formattedReviewEnd.orEmpty()),
                Modifier.fillMaxWidth(),
                stayInControl,
            )
            state.setName?.let { name -> Text(stringResource(Res.string.schedule_set_label, name), style = MaterialTheme.typography.titleMedium) }
            state.review.actionRequired?.let { required -> SessionReadinessNotice(required, onRetry) }
            PosatoActionRow {
                if (state.review.actionRequired == SessionActionRequired.NO_EFFECTIVE_ITEMS) {
                    PosatoButton(onItems, controlId = "action_add_websites_or_apps") { Text(stringResource(Res.string.action_add_websites_or_apps)) }
                } else {
                    PosatoButton(onStart, enabled = state.canStart(), controlId = "action_start_this_pause") {
                        Text(
                            if (state.isStarting) {
                                stringResource(
                                    Res.string.session_starting_ellipsis,
                                )
                            } else {
                                stringResource(Res.string.action_start_this_pause)
                            },
                        )
                    }
                }
                if (!platformUsesCupertinoChrome) {
                    PosatoButton(
                        onChangeDuration,
                        style = PosatoButtonStyle.Quiet,
                        enabled = !state.isStarting,
                        controlId = "action_change_duration",
                    ) { Text(stringResource(Res.string.action_change_duration)) }
                }
            }
            SessionSelectionSummary(state, deviceLabel, onEditItems, onShowItems)
            PosatoCaption(stringResource(Res.string.session_start_applies))
        }
    }
}

@Composable
private fun SessionReadinessNotice(
    required: SessionActionRequired,
    onRetry: () -> Unit,
) {
    val noItems = stringResourceForDevice(
        Res.string.session_action_no_items_mac,
        Res.string.session_action_no_items_iphone,
        Res.string.session_action_no_items_ipad,
        Res.string.session_action_no_items_computer,
        Res.string.session_action_no_items_phone,
    )
    val reported = stringResource(required.actionMessage())
    PosatoNotice(tone = PosatoTone.Caution, actionContent = {
        when (required) {
            SessionActionRequired.MAPPINGS_LOAD_FAILED -> {
                PosatoButton(onRetry, controlId = "action_retry") { Text(stringResource(Res.string.action_retry)) }
            }

            SessionActionRequired.NO_EFFECTIVE_ITEMS -> {}
        }
    }) { Text(if (required == SessionActionRequired.NO_EFFECTIVE_ITEMS) noItems else reported) }
}
