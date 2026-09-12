package app.posato.feature.session.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.domain.LocalSessionStatus.Active
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionReviewDerivation
import app.posato.feature.session.domain.SessionSetup
import app.posato.feature.session.domain.SessionSetupFailure
import app.posato.feature.session.domain.SessionSetupResult
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SessionViewModel(
    private val policyStore: LocalTargetPolicyStore,
    private val applicationMappings: LocalApplicationMappings,
    private val sessionIds: SessionIdGenerator,
    private val clock: SessionClock,
    private val timeFormat: SessionTimeFormat,
    private val owner: SessionTransitionOwner,
) : ViewModel() {
    private val targetsRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val sessionLoad = MutableStateFlow(SessionLoadState())
    private val setupDraft = MutableStateFlow(SessionSetupDraft())
    private val targetsState = MutableStateFlow(SessionTargetsState())
    private val command = MutableStateFlow<SessionCommand?>(null)
    private val confirmingEarlyEnd = MutableStateFlow(false)
    private val ownerStatuses: Flow<Unit> = owner.status.transform { status ->
        sessionLoad.update { SessionLoadState(status = status) }
        emit(Unit)
    }
    private val ticker = observeSessionTicks(clock)
    private val ownerUpdates: Flow<Unit> = merge(
        ownerStatuses,
        ticker.transform { now ->
            // Display ticks carry no work of their own: the owner evaluates time
            // and polls enforcement, while the tick value drives the countdown.
            owner.onTick(now)
        },
    )
    private val targetsReadLifecycle: Flow<Unit> = merge(
        targetsRefreshRequests.onStart { emit(Unit) },
        policyStore.policyChanges,
    ).transform {
        emit(Unit)
        targetsState.update { loadSessionTargets(policyStore, applicationMappings) }
    }

    val uiState: StateFlow<SessionUiState> = combine(
        combine(sessionLoad, setupDraft, targetsState) { load, draft, targets ->
            Triple(load, draft, targets)
        },
        combine(command, confirmingEarlyEnd, ticker) { activeCommand, confirming, nowMillis ->
            Triple(activeCommand, confirming, nowMillis)
        },
        ownerUpdates,
        targetsReadLifecycle,
        owner.view,
    ) { left, right, _, _, enforcementView ->
        createSessionUiState(
            left.first,
            left.second,
            left.third,
            right.first,
            right.second,
            right.third,
            timeFormat,
            enforcementView,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    fun setSetupVisible(visible: Boolean) {
        if (visible) {
            val current = createSessionUiState(
                sessionLoad.value,
                setupDraft.value,
                targetsState.value,
                command.value,
                confirmingEarlyEnd.value,
                clock.currentEpochMillis(),
                timeFormat,
                owner.view.value,
            )
            if (!current.canEnterSetup()) {
                return
            }
            setupDraft.update { draft -> draft.copy(isSettingUp = true, failure = null) }
        } else {
            setupDraft.update { SessionSetupDraft() }
        }
    }

    fun adjustDuration(deltaMinutes: Int) {
        setupDraft.update { draft ->
            val adjusted = (draft.durationMinutes + deltaMinutes).coerceIn(SessionLimits.MIN_DURATION_MINUTES, SessionLimits.MAX_DURATION_MINUTES)
            draft.copy(durationMinutes = adjusted, failure = null)
        }
    }

    fun setDurationMinutes(minutes: Int) {
        if (minutes !in SessionLimits.MIN_DURATION_MINUTES..SessionLimits.MAX_DURATION_MINUTES) {
            return
        }
        setupDraft.update { draft -> draft.copy(durationMinutes = minutes, failure = null) }
    }

    fun onScreenEntered() {
        targetsRefreshRequests.tryEmit(Unit)
        viewModelScope.launch {
            when (val result = owner.refresh()) {
                is LocalSessionResult.Success -> {
                    sessionLoad.update { SessionLoadState(status = result.value) }
                }

                is LocalSessionResult.Failure -> {
                    sessionLoad.update { SessionLoadState(failure = result.reason.toLoadFailure()) }
                }
            }
        }
    }

    fun submitDurationMinutes(input: String) {
        val minutes = input.trim().toIntOrNull()
        setupDraft.update { draft ->
            when {
                minutes == null || minutes < SessionLimits.MIN_DURATION_MINUTES -> {
                    draft.copy(failure = SessionSetupFailure.TOO_SHORT)
                }

                minutes > SessionLimits.MAX_DURATION_MINUTES -> {
                    draft.copy(failure = SessionSetupFailure.TOO_LONG)
                }

                else -> {
                    draft.copy(durationMinutes = minutes, failure = null)
                }
            }
        }
    }

    fun setReviewVisible(visible: Boolean) {
        if (visible) {
            val draft = setupDraft.value
            if (!draft.isSettingUp || draft.isReviewing) {
                return
            }
            when (val validation = SessionSetup.validateDuration(draft.durationMinutes, clock.currentEpochMillis())) {
                is SessionSetupResult.Invalid -> {
                    setupDraft.update { state -> state.copy(failure = validation.reason) }
                }

                is SessionSetupResult.Valid -> {
                    setupDraft.update { state -> state.copy(isReviewing = true, failure = null, resolvedReviewEnd = validation.endEpochMillis) }
                    targetsRefreshRequests.tryEmit(Unit)
                }
            }
        } else {
            setupDraft.update { draft -> draft.copy(isReviewing = false, resolvedReviewEnd = null) }
        }
    }

    fun startSession() {
        val draft = setupDraft.value
        if (!draft.isSettingUp || !draft.isReviewing || command.value != null) {
            return
        }
        val now = clock.currentEpochMillis()
        val end = when (val validation = SessionSetup.validateDuration(draft.durationMinutes, now)) {
            is SessionSetupResult.Invalid -> {
                setupDraft.update { state -> state.copy(isReviewing = false, resolvedReviewEnd = null, failure = validation.reason) }
                return
            }

            is SessionSetupResult.Valid -> {
                validation.endEpochMillis
            }
        }
        command.update { SessionCommand.STARTING }
        viewModelScope.launch {
            try {
                val targets = loadSessionTargets(policyStore, applicationMappings)
                targetsState.update { targets }
                if (isStartBlocked(targets)) {
                    return@launch
                }
                when (val result = owner.startSession(sessionIds.create(), now, end, targets.toFrozenStartSet())) {
                    is LocalSessionResult.Success -> {
                        sessionLoad.update { SessionLoadState(status = result.value) }
                        setupDraft.update { SessionSetupDraft() }
                    }

                    is LocalSessionResult.Failure -> {
                        if (result.reason == LocalSessionFailure.ALREADY_ACTIVE) {
                            sessionLoad.update { SessionLoadState() }
                            viewModelScope.refreshSession(owner, sessionLoad)
                        } else {
                            sessionLoad.update { state -> state.copy(failure = SessionOperationFailure.START_FAILED) }
                        }
                    }
                }
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                sessionLoad.update { state -> state.copy(failure = SessionOperationFailure.START_FAILED) }
            } finally {
                command.update { state -> if (state == SessionCommand.STARTING) null else state }
            }
        }
    }

    fun setEarlyEndConfirmation(visible: Boolean) {
        if (visible) {
            val current = createSessionUiState(
                sessionLoad.value,
                setupDraft.value,
                targetsState.value,
                command.value,
                confirmingEarlyEnd.value,
                clock.currentEpochMillis(),
                timeFormat,
                owner.view.value,
            )
            if (current.canRequestEarlyEnd()) {
                confirmingEarlyEnd.update { true }
            }
        } else {
            confirmingEarlyEnd.update { false }
        }
    }

    fun confirmEarlyEnd() {
        val status = sessionLoad.value.status
        if (status !is Active || command.value != null) {
            return
        }
        // The confirmation ends the identifier shown to the person, not whichever
        // session later occupies the row: the owner refuses a stale identifier.
        val confirmed = status.record.sessionId
        confirmingEarlyEnd.update { false }
        command.update { SessionCommand.ENDING }
        viewModelScope.launch {
            try {
                when (val result = owner.endEarly(confirmed)) {
                    is LocalSessionResult.Success -> {
                        sessionLoad.update { SessionLoadState(status = result.value) }
                    }

                    is LocalSessionResult.Failure -> {
                        sessionLoad.update { state -> state.copy(failure = SessionOperationFailure.END_FAILED) }
                    }
                }
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                sessionLoad.update { load -> load.copy(failure = SessionOperationFailure.END_FAILED) }
            } finally {
                command.update { active -> if (active == SessionCommand.ENDING) null else active }
            }
        }
    }

    fun retry() {
        sessionLoad.update { SessionLoadState() }
        viewModelScope.refreshSession(owner, sessionLoad)
        targetsRefreshRequests.tryEmit(Unit)
    }

    fun retryEnforcement() {
        owner.retry()
    }
}

private fun CoroutineScope.refreshSession(
    owner: SessionTransitionOwner,
    sessionLoad: MutableStateFlow<SessionLoadState>,
) {
    launch {
        when (val result = owner.refresh()) {
            is LocalSessionResult.Success -> {
                sessionLoad.update { SessionLoadState(status = result.value) }
            }

            is LocalSessionResult.Failure -> {
                sessionLoad.update { SessionLoadState(failure = result.reason.toLoadFailure()) }
            }
        }
    }
}

private fun LocalSessionFailure.toLoadFailure(): SessionOperationFailure {
    return if (this == LocalSessionFailure.CORRUPTION) SessionOperationFailure.CORRUPTED_SESSION else SessionOperationFailure.LOAD_FAILED
}

internal fun observeSessionTicks(clock: SessionClock): Flow<Long> = flow {
    while (true) {
        emit(clock.currentEpochMillis())
        delay(TICK_MILLIS)
    }
}

private const val TICK_MILLIS: Long = 1_000L

private fun isStartBlocked(targets: SessionTargetsState): Boolean {
    val policy = targets.policy
    val mappings = targets.mappings
    if (policy == null || mappings == null) {
        return true
    }
    return SessionReviewDerivation.derive(policy, mappings).actionRequired?.blocksStart == true
}
