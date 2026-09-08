package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SessionEnforcementCoordinator(
    private val enforcement: EnforcementPort,
    private val scope: CoroutineScope,
    private val loadTargets: suspend () -> SessionTargetsState,
    private val refreshSession: () -> Unit,
) {
    private val mutableView = MutableStateFlow(EnforcementViewState())
    private var reconcileJob: Job? = null
    private var unknownStreak = 0
    private var expiryClearDoneFor: String? = null

    val view: StateFlow<EnforcementViewState> = mutableView.asStateFlow()

    suspend fun settle(status: LocalSessionStatus) {
        val active = (status as? LocalSessionStatus.Active)?.record
        if (active != null) {
            val view = mutableView.value
            if (view.state is EnforcementState.Inactive && !view.busy) {
                reconcileActiveSession(active)
            }
            return
        }
        reconcileJob?.cancel()
        reconcileJob = null
        val ended = status as? LocalSessionStatus.Ended
        if (ended != null && ended.kind == SessionEndKind.EXPIRED) {
            clearAfterObservedExpiry(ended.record)
        }
    }

    fun pollNow(record: SessionRecord) {
        if (mutableView.value.busy || mutableView.value.state !is EnforcementState.Active) {
            return
        }
        scope.launch {
            onTickSecond(record)
        }
    }

    suspend fun applyAfterStart(
        record: SessionRecord,
        targets: SessionTargetsState,
    ) {
        val entering = mutableView.value.state
        mutableView.update { view -> view.copy(busy = true) }
        try {
            if (entering is EnforcementState.ActionRequired && entering.kind == EnforcementActionKind.CLEAR_FAILED) {
                enforcement.clear()
            }
            val frozen = targets.toEnforcedSet()
            val report = enforcement.apply(record.toEnforcementRequest(frozen, targets))
            mutableView.update { view -> view.copy(state = report.toActiveState(), enforced = frozen) }
            if (report.outcome == EnforcementOutcome.APPLIED) {
                unknownStreak = 0
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view ->
                view.copy(
                    state = EnforcementState.ActionRequired(
                        EnforcementActionKind.APPLY_FAILED,
                        enforcement.reapplyRequiresPrompt,
                    ),
                    enforced = targets.toEnforcedSet(),
                )
            }
        } finally {
            mutableView.update { view -> view.copy(busy = false) }
        }
    }

    suspend fun clearAfterEnd() {
        val entering = mutableView.value.state
        if (entering !is EnforcementState.Active && entering !is EnforcementState.ActionRequired) {
            return
        }
        mutableView.update { view -> view.copy(busy = true) }
        try {
            when (enforcement.clear()) {
                EnforcementOutcome.CLEARED -> {
                    mutableView.update { EnforcementViewState() }
                }

                EnforcementOutcome.UNAVAILABLE,
                EnforcementOutcome.AUTHORIZATION_REQUIRED -> {
                    if (entering.isApplyFailure()) {
                        mutableView.update { EnforcementViewState() }
                    } else {
                        mutableView.update { view ->
                            view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                        }
                    }
                }

                else -> {
                    mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
                }
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view -> view.copy(state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        } finally {
            mutableView.update { view -> view.copy(busy = false) }
        }
    }

    fun retry(status: LocalSessionStatus?) {
        if (mutableView.value.busy) {
            return
        }
        mutableView.update { view -> view.copy(busy = true) }
        scope.launch {
            try {
                val record = (status as? LocalSessionStatus.Active)?.record
                if (record != null) {
                    reapplyCurrent(record)
                } else {
                    clearAfterEnd()
                }
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            } finally {
                mutableView.update { view -> view.copy(busy = false) }
            }
        }
    }

    private suspend fun onTickSecond(record: SessionRecord) {
        if (mutableView.value.busy || mutableView.value.state !is EnforcementState.Active) {
            return
        }
        val outcome: EnforcementOutcome
        try {
            outcome = enforcement.status()
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            return
        }
        if (mutableView.value.busy || mutableView.value.state !is EnforcementState.Active) {
            return
        }
        when (outcome) {
            EnforcementOutcome.APPLIED -> {
                unknownStreak = 0
            }

            EnforcementOutcome.CLEARED -> {
                unknownStreak = 0
                handlePollLoss(record)
            }

            else -> {
                unknownStreak += 1
                if (unknownStreak >= CONSECUTIVE_UNKNOWN_LIMIT) {
                    unknownStreak = 0
                    handlePollLoss(record)
                }
            }
        }
    }

    private suspend fun handlePollLoss(record: SessionRecord) {
        try {
            if (enforcement.reapplyRequiresPrompt) {
                mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
            } else {
                reapplyCurrent(record)
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            mutableView.update { view -> view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt)) }
        }
    }

    private fun reconcileActiveSession(record: SessionRecord) {
        if (reconcileJob?.isActive == true) {
            return
        }
        reconcileJob = scope.launch {
            try {
                reconcile(record)
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (_: Exception) {
                mutableView.update { view ->
                    if (view.state is EnforcementState.Inactive) {
                        view.copy(state = EnforcementActionKind.APPLY_FAILED.toAction(enforcement.reapplyRequiresPrompt))
                    } else {
                        view
                    }
                }
            }
        }
    }

    private suspend fun reconcile(record: SessionRecord) {
        val sessionId = record.sessionId.reconciliationId()
        if (enforcement.pollSuspendedExpiry(sessionId)) {
            when (enforcement.clear()) {
                EnforcementOutcome.CLEARED -> {
                    refreshSession()
                }

                else -> {
                    mutableView.update { view ->
                        view.copy(
                            state = EnforcementActionKind.CLEAR_FAILED.toAction(enforcement.reapplyRequiresPrompt),
                            enforced = loadTargets().toEnforcedSet(),
                        )
                    }
                    refreshSession()
                }
            }
            return
        }
        if (enforcement.status() == EnforcementOutcome.APPLIED) {
            mutableView.update { view ->
                if (view.state is EnforcementState.Inactive) {
                    view.copy(state = EnforcementState.Active(false), enforced = loadTargets().toEnforcedSet())
                } else {
                    view
                }
            }
            return
        }
        if (enforcement.reapplyRequiresPrompt) {
            mutableView.update { view ->
                view.copy(
                    state = EnforcementState.ActionRequired(
                        EnforcementActionKind.RESUME_REQUIRED,
                        repeatsSystemPrompt = true,
                    ),
                    enforced = loadTargets().toEnforcedSet(),
                )
            }
            return
        }
        reapplyCurrent(record)
    }

    private suspend fun reapplyCurrent(record: SessionRecord) {
        val targets = loadTargets()
        val frozen = targets.toEnforcedSet()
        enforcement.clear()
        val report = enforcement.apply(record.toEnforcementRequest(frozen, targets))
        mutableView.update { view -> view.copy(state = report.toActiveState(), enforced = frozen) }
        if (report.outcome == EnforcementOutcome.APPLIED) {
            unknownStreak = 0
        }
    }

    private suspend fun clearAfterObservedExpiry(record: SessionRecord) {
        val sessionId = record.sessionId.reconciliationId()
        if (expiryClearDoneFor == sessionId) {
            return
        }
        expiryClearDoneFor = sessionId
        clearAfterEnd()
    }
}

internal fun SessionTargetsState.toEnforcedSet(): EnforcedSet {
    val policy = this.policy ?: return EnforcedSet()
    val mappings = (mappings as? LocalApplicationMappingsLoadResult.Success)?.snapshot?.mappings
    return EnforcedSet(
        domains = policy.domains.map { domain -> domain.canonicalValue }.toPersistentList(),
        applicationCount = mappings?.size,
    )
}

internal fun SessionRecord.toEnforcementRequest(
    frozen: EnforcedSet,
    targets: SessionTargetsState,
): EnforcementRequest {
    val mappingIds = if (frozen.applicationCount != null) {
        val mappings = targets.mappings as? LocalApplicationMappingsLoadResult.Success
        mappings?.snapshot?.mappings?.map { mapping -> mapping.id.canonicalValue }.orEmpty()
    } else {
        emptyList()
    }
    return EnforcementRequest(
        domains = frozen.domains,
        mappingIds = mappingIds,
        sessionId = sessionId.reconciliationId(),
        sessionStartEpochMillis = startEpochMillis,
        sessionEndEpochMillis = endEpochMillis,
    )
}

internal fun EnforcementApplyReport.toActiveState(): EnforcementState {
    return when (outcome) {
        EnforcementOutcome.APPLIED -> EnforcementState.Active(belowPlatformMinimum)
        EnforcementOutcome.NOTHING_TO_ENFORCE -> EnforcementState.Active(false)
        else -> EnforcementState.ActionRequired(EnforcementActionKind.APPLY_FAILED, repeatsSystemPrompt)
    }
}

internal fun EnforcementActionKind.toAction(repeatsSystemPrompt: Boolean): EnforcementState.ActionRequired {
    return EnforcementState.ActionRequired(this, repeatsSystemPrompt)
}

internal fun EnforcementState.isApplyFailure(): Boolean {
    return this is EnforcementState.ActionRequired && kind == EnforcementActionKind.APPLY_FAILED
}

private const val CONSECUTIVE_UNKNOWN_LIMIT: Int = 3
