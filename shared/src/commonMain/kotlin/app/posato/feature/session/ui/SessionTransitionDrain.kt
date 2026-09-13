package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.sessionIdFromReconciliationId
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FrozenStartSet
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
    return status !is LocalSessionStatus.Active && enforcedIdentity == null
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
    val now = clock.currentEpochMillis()
    val applicable = stateMutex.withLock {
        when (val read = store.read(now)) {
            is LocalSessionResult.Failure -> {
                null
            }

            is LocalSessionResult.Success -> {
                val active = read.value as? LocalSessionStatus.Active
                active != null && SessionTag(active.record) == tag && now < active.record.endEpochMillis
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

/**
 * Post-transition drain shared by every path that touches the port. Reads
 * the current row through the owner's mutex and either reconciles a newer
 * desired state directly or settles the full fresh state. A tag alone cannot
 * surface a same-identity Active to Ended change, so settling the full state
 * converges a skipped end transition instead of leaving the view Active on
 * an Ended row. Settling an already-converged state is a no-op through the
 * predicate, so this cannot self-drive.
 */
internal suspend fun convergeAfterPort(
    stateMutex: Mutex,
    store: LocalSessionSyncStore,
    clock: SessionClock,
    cleared: SessionTag?,
    settle: suspend (LocalSessionStatus) -> Unit,
    reconcile: suspend (SessionTag, SessionRecord, FrozenStartSet?) -> Unit,
) {
    val fresh = stateMutex.withLock {
        when (val read = store.read(clock.currentEpochMillis())) {
            is LocalSessionResult.Failure -> null
            is LocalSessionResult.Success -> read.value
        }
    } ?: return
    val active = fresh as? LocalSessionStatus.Active
    if (active != null && SessionTag(active.record) != cleared) {
        // The clear removed a newer enforcement than intended: reconcile
        // the current desired state directly, so the latest transition
        // drains before this in-flight transition completes.
        reconcile(SessionTag(active.record), active.record, active.frozenStartSet)
    } else if (tagOf(fresh) != cleared || fresh !is LocalSessionStatus.Active) {
        settle(fresh)
    }
}

internal suspend fun acknowledgeExpired(
    enforcement: EnforcementPort,
    sessionId: String,
): Boolean {
    // Best effort: a failed acknowledgement only repeats an idempotent
    // bank on the next observation; the row is already terminal, and a
    // future schedule clears a leftover native record.
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
) {
    // Mandatory displacement point before every schedule: a natively
    // recorded expiry for a superseded identity must land in the durable
    // marker table before the new schedule (and only then its
    // acknowledgement) can make the signal disappear. The native side
    // never deletes foreign records itself, so this ordering plus the
    // record-then-ack in the superseded branch closes the loss window.
    val displaced = try {
        enforcement.displacedSuspendedExpiry(currentSessionId)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (_: Exception) {
        null
    } ?: return
    val displacedId = sessionIdFromReconciliationId(displaced) ?: return
    if (store.retainExpiryMarker(displacedId) is LocalSessionResult.Success) {
        acknowledgeExpired(enforcement, displaced)
    }
}
