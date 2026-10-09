package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.navigation.PosatoNavStack
import app.posato.core.navigation.rememberLastPresent
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.onboarding.promptInProgress
import app.posato.feature.presence.SessionWindowRequest
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
    zone: ScheduleZone,
    onOpenPausedItems: (PauseSetId?) -> Unit,
    onEditPausedItems: (PauseSetId?, TargetsCategory) -> Unit,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Compact,
    deviceLabel: String = "On this device",
    syncState: SyncBootstrapUiState? = null,
    macSetupState: MacHelperSetupUiState? = null,
    onMacSetupAnnouncement: (String) -> Unit = {},
    windowRequest: SessionWindowRequest? = null,
    onConsumeWindowRequest: () -> Unit = {},
    scheduledPauses: ScheduledPauses? = null,
    notPausedYet: StateFlow<Int>? = null,
    deviceNoun: String = "device",
    onOpenAbout: (() -> Unit)? = null,
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(policyStore, applicationMappings, sessionIds, clock, timeFormat, owner, zone)
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
    val manualNotPaused = notPausedYet?.collectAsState()?.value?.takeIf { state.status is LocalSessionStatus.Active } ?: 0
    val waiting = manualNotPaused + (scheduledView?.takeIf { it.restricts }?.notPausedYet ?: 0)
    CloseSetupDuringScheduledPause(scheduledView?.restricts == true) { viewModel.setSetupVisible(false) }
    SessionScreen(
        state = state,
        scheduled = scheduledView,
        scheduledEnd = scheduledEnd.actions(scheduledView?.restricts == true),
        pauseNotice = waiting.takeIf { it > 0 }?.let { count -> notPausedYetText(count, deviceNoun) },
        onOpenAbout = onOpenAbout,
        onEnterSetup = { viewModel.setSetupVisible(true) },
        onExitSetup = { viewModel.setSetupVisible(false) },
        onChooseDuration = viewModel::chooseDuration,
        onChoosePauseSet = viewModel::choosePauseSet,
        onEnterReview = { viewModel.setReviewVisible(true) },
        onExitReview = { viewModel.setReviewVisible(false) },
        onStartSession = viewModel::startSession,
        onRequestEarlyEnd = { viewModel.setEarlyEndConfirmation(true) },
        onCancelEarlyEnd = { viewModel.setEarlyEndConfirmation(false) },
        onConfirmEarlyEnd = viewModel::confirmEarlyEnd,
        onRetry = viewModel::retry,
        onRetryEnforcement = viewModel::retryEnforcement,
        onOpenPausedItems = { onOpenPausedItems(state.setId) },
        onEditPausedItems = { category -> onEditPausedItems(state.setId, category) },
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
    val now = clock.currentEpochMillis()
    val manualEnd = (state.status as? LocalSessionStatus.Active)?.record?.endEpochMillis ?: 0L
    val until = timeFormat.formatTime(maxOf(scheduled.endEpochMillis, manualEnd), now)
    val setName = { setId: PauseSetId? -> state.pauseSets.firstOrNull { row -> row.id == setId }?.name }
    val manual = state.setName?.takeIf { manualEnd > 0 }?.let { name -> listOf("Set: $name, until ${timeFormat.formatTime(manualEnd, now)}") }
    val parts = manual.orEmpty() + scheduled.parts.map { part ->
        val set = setName(part.setId)?.let { name -> ", Set: $name" }.orEmpty()
        "${part.name} (schedule)$set, until ${timeFormat.formatTime(part.endEpochMillis, now)}"
    }
    return ScheduledPauseView(scheduled.name, until, scheduled.state, parts, scheduled.notPausedYet)
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

internal val PosatoLayout.screenInset: Dp
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
    onChooseDuration: (SessionDurationChoice) -> Unit = {},
    onChoosePauseSet: (PauseSetId) -> Unit = {},
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
    pauseNotice: String? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    val showsMacSetup = macSetup != null && state.showsMacSetup(macSetup)
    var shownItems by rememberSaveable { mutableStateOf<TargetsCategory?>(null) }
    val flow = sessionStack(state, showsMacSetup, scheduled != null && scheduledEnd.confirming, platformUsesCupertinoChrome)
    val stack = if (shownItems != null) flow + SessionRoute.Items else flow
    val showItems = { category: TargetsCategory -> shownItems = category }.takeIf { platformUsesCupertinoChrome }
    val backEnabled = !state.isStarting && !state.isEnding && !(showsMacSetup && macSetup.promptInProgress())
    PosatoNavStack(
        stack,
        onBack = {
            stack.last().back(scheduledEnd.onCancel, onCancelEarlyEnd, onExitReview, onExitSetup, { shownItems = null }) {
                macActions.leave()
                onExitSetup()
            }
        },
        modifier = modifier.fillMaxSize(),
        backEnabled = backEnabled,
    ) { route ->
        if (route == SessionRoute.Items) {
            SessionItemsRoute(state, rememberLastPresent(shownItems), flow.last(), { shownItems = null }, onEditPausedItems)
            return@PosatoNavStack
        }
        val routeBody: @Composable ColumnScope.() -> Unit = {
            SessionOperationNotice(state, onRetry)
            SessionRouteContent(
                route = route,
                state = state,
                layout = layout,
                deviceLabel = deviceLabel,
                onEnterSetup = onEnterSetup,
                onExitSetup = onExitSetup,
                onChooseDuration = onChooseDuration,
                onChoosePauseSet = onChoosePauseSet,
                onEnterReview = onEnterReview,
                onExitReview = onExitReview,
                onStartSession = onStartSession,
                onRequestEarlyEnd = onRequestEarlyEnd,
                onCancelEarlyEnd = onCancelEarlyEnd,
                onConfirmEarlyEnd = onConfirmEarlyEnd,
                onRetry = onRetry,
                onRetryEnforcement = onRetryEnforcement,
                onOpenPausedItems = onOpenPausedItems,
                onEditPausedItems = onEditPausedItems,
                syncState = syncState,
                macSetup = macSetup,
                macActions = macActions,
                macLoginItemEnabled = macLoginItemEnabled,
                scheduled = scheduled,
                scheduledEnd = scheduledEnd,
                pauseNotice = pauseNotice,
                onShowItems = showItems,
            )
        }
        SessionRouteFrame(route, layout, onOpenAbout, onExitSetup, onExitReview, backEnabled, routeBody)
    }
    if (platformUsesCupertinoChrome) {
        SessionEarlyEndAlerts(state, scheduled, scheduledEnd, onConfirmEarlyEnd, onCancelEarlyEnd)
    }
}

@Composable
private fun SessionRouteContent(
    route: SessionRoute,
    state: SessionUiState,
    layout: PosatoLayout,
    deviceLabel: String,
    onEnterSetup: () -> Unit,
    onExitSetup: () -> Unit,
    onChooseDuration: (SessionDurationChoice) -> Unit,
    onChoosePauseSet: (PauseSetId) -> Unit,
    onEnterReview: () -> Unit,
    onExitReview: () -> Unit,
    onStartSession: () -> Unit,
    onRequestEarlyEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
    onConfirmEarlyEnd: () -> Unit,
    onRetry: () -> Unit,
    onRetryEnforcement: () -> Unit,
    onOpenPausedItems: () -> Unit,
    onEditPausedItems: (TargetsCategory) -> Unit,
    syncState: SyncBootstrapUiState?,
    macSetup: MacSetupPresentation?,
    macActions: MacSetupCallbacks,
    macLoginItemEnabled: Boolean?,
    scheduled: ScheduledPauseView?,
    scheduledEnd: ScheduledEndActions,
    pauseNotice: String?,
    onShowItems: ((TargetsCategory) -> Unit)?,
) {
    when (route) {
        SessionRoute.ScheduledEarlyEnd -> {
            rememberLastPresent(scheduled)?.let { ScheduledEarlyEndContent(it, layout, scheduledEnd.onConfirm, scheduledEnd.onCancel) }
        }

        SessionRoute.EarlyEnd -> {
            SessionEarlyEndContent(state, layout, onConfirmEarlyEnd, onCancelEarlyEnd)
        }

        SessionRoute.MacSetup -> {
            if (macSetup != null) {
                SessionMacSetup(state, macSetup, layout, macActions, onExitSetup)
            }
        }

        SessionRoute.Review -> {
            SessionReviewContent(state, layout, deviceLabel, onStartSession, onExitReview, onOpenPausedItems, onEditPausedItems, onRetry, onShowItems)
        }

        SessionRoute.Duration -> {
            SessionDurationContent(state, layout, onChooseDuration, onEnterReview, onExitSetup, onChoosePauseSet)
        }

        SessionRoute.Overview -> {
            if (state.status != null) {
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
                    pauseNotice,
                    onShowItems,
                )
            }
        }

        // The pushed items screen has its own bar and list, so SessionScreen draws it without a route body.
        SessionRoute.Items -> {}
    }
}

/**
 * Session's screens: the overview, a confirmation or setup flow above it, and review above duration. On iOS the
 * selected items are pushed as [Items] over the screen that lists them.
 */
internal enum class SessionRoute {
    Overview,
    Duration,
    Review,
    MacSetup,
    EarlyEnd,
    ScheduledEarlyEnd,
    Items,
}

private fun SessionRoute.back(
    onCancelScheduledEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
    onExitReview: () -> Unit,
    onExitSetup: () -> Unit,
    onCloseItems: () -> Unit,
    onLeaveMacSetup: () -> Unit,
) {
    when (this) {
        SessionRoute.ScheduledEarlyEnd -> {
            onCancelScheduledEnd()
        }

        SessionRoute.EarlyEnd -> {
            onCancelEarlyEnd()
        }

        SessionRoute.MacSetup -> {
            onLeaveMacSetup()
        }

        SessionRoute.Review -> {
            onExitReview()
        }

        SessionRoute.Duration -> {
            onExitSetup()
        }

        SessionRoute.Items -> {
            onCloseItems()
        }

        SessionRoute.Overview -> {}
    }
}

private fun sessionStack(
    state: SessionUiState,
    showsMacSetup: Boolean,
    confirmingScheduledEnd: Boolean,
    confirmationsAsAlerts: Boolean,
): List<SessionRoute> {
    val top = when {
        state.status == null -> null
        confirmingScheduledEnd && !confirmationsAsAlerts -> SessionRoute.ScheduledEarlyEnd
        state.confirmingEarlyEnd && !confirmationsAsAlerts -> SessionRoute.EarlyEnd
        showsMacSetup -> SessionRoute.MacSetup
        state.isReviewing -> SessionRoute.Review
        state.isSettingUp -> SessionRoute.Duration
        else -> null
    }
    return when (top) {
        null -> listOf(SessionRoute.Overview)
        SessionRoute.Review -> listOf(SessionRoute.Overview, SessionRoute.Duration, SessionRoute.Review)
        else -> listOf(SessionRoute.Overview, top)
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
            PosatoActivityIndicator()
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

/**
 * A manual start is not offered during a scheduled pause, so a duration or review screen left open when one
 * starts is closed instead of starting a second pause from a stale form.
 */
@Composable
private fun CloseSetupDuringScheduledPause(
    scheduledRestricts: Boolean,
    onClose: () -> Unit,
) {
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(scheduledRestricts) {
        if (scheduledRestricts) {
            close()
        }
    }
}
