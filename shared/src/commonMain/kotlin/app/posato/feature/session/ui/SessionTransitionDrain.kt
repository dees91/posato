package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.ExpiryDisplacementOutcome
import app.posato.feature.enforcement.sessionIdFromReconciliationId
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Explicit drain termination condition: the desired state counts as converged
 * when the port outcome is confirmed (the enforced identity matches an active
 * row), when the view already carries this session's action outcome, or when
 * a row with nothing left to enforce shows the inactive view. A live active
 * row with no enforcement and no rendered outcome still needs its reconcile
 * pass, so a bare inactive view converges only non-active rows. An ended row
 * converges on a rendered clear outcome only: a stale apply failure from the
 * start episode must still run its end cleanup instead of settling as clean.
 */
internal fun isTransitionConverged(
    status: LocalSessionStatus,
    enforcedIdentity: SessionTag?,
    viewState: EnforcementState,
    actionTag: SessionTag?,
    confirmedClear: Boolean,
): Boolean {
    val tag = tagOf(status)
    if (status is LocalSessionStatus.Active && enforcedIdentity == tag) {
        return true
    }
    if (viewState is EnforcementState.ActionRequired) {
        if (status is LocalSessionStatus.Active) {
            return actionTag == tag
        }
        return viewState.kind == EnforcementActionKind.CLEAR_FAILED && actionTag == tag
    }
    return status !is LocalSessionStatus.Active && enforcedIdentity == null && (status is LocalSessionStatus.Inactive || confirmedClear)
}

/**
 * Pre-apply liveness boundary shared by the command and re-apply paths. The
 * owner keeps single serialized responsibility for transitions; this helper
 * only needs its mutex, store, and clock plus a converge callback so the
 * class itself stays small. A spent or replaced session is recorded terminal
 * without ever applying; its deadline never moves. markExpired is
 * identity-bound and idempotent, so a replaced row or an already-banked
 * expiry is safe here. A failed read proves nothing: the apply is skipped
 * without inventing a terminal fact.
 */
internal suspend fun ensureApplicableBeforeApply(
    stateMutex: Mutex,
    store: LocalSessionSyncStore,
    clock: SessionClock,
    tag: SessionTag,
    record: SessionRecord,
    converge: suspend () -> Unit,
): Boolean {
    val applicable = stateMutex.withLock {
        when (val read = store.read(clock.currentEpochMillis())) {
            is LocalSessionResult.Failure -> {
                null
            }

            is LocalSessionResult.Success -> {
                val active = read.value as? LocalSessionStatus.Active
                active != null && SessionTag(active.record) == tag && clock.currentEpochMillis() < active.record.endEpochMillis
            }
        }
    }
    if (applicable == true) {
        return true
    }
    if (applicable == false) {
        // A Success read proved the session spent or replaced: record the
        // terminal fact without ever applying; the deadline never moves.
        // markExpired is identity-bound and idempotent, so a replaced row
        // or an already-banked expiry is safe here.
        stateMutex.withLock { store.markExpired(record.sessionId) }
    }
    // A failed read proved nothing: skip the apply without inventing a
    // terminal fact, and converge whatever is current instead.
    converge()
    return false
}

internal suspend fun acknowledgeExpired(
    enforcement: EnforcementPort,
    sessionId: String,
): Boolean {
    // Best effort: a failed acknowledgement only repeats an idempotent
    // bank on the next observation; the row is already terminal, and a
    // later observations retry acknowledgement.
    return try {
        enforcement.acknowledgeSuspendedExpiry(sessionId)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (_: Exception) {
        false
    }
}

internal suspend fun persistDisplacedExpiry(
    enforcement: EnforcementPort,
    store: LocalSessionSyncStore,
    currentSessionId: String,
): Boolean {
    return try {
        val displaced = enforcement.displacedSuspendedExpiry(currentSessionId)
        when (displaced.outcome) {
            ExpiryDisplacementOutcome.ABSENT -> {
                true
            }

            ExpiryDisplacementOutcome.FAILED -> {
                false
            }

            ExpiryDisplacementOutcome.PRESENT -> {
                val displacedId = displaced.sessionId?.let(::sessionIdFromReconciliationId) ?: return false
                if (store.retainExpiryMarker(displacedId) is LocalSessionResult.Failure) {
                    false
                } else {
                    acknowledgeExpired(enforcement, checkNotNull(displaced.sessionId))
                    true
                }
            }
        }
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (_: Exception) {
        false
    }
}
