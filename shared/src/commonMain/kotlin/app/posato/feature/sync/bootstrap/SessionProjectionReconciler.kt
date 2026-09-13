package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.sync.domain.SessionCandidate
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.domain.SynchronizedSessionStart

internal class SessionProjectionReconciler(
    private val sessions: LocalSessionSyncStore,
) {
    suspend fun apply(
        bankExpiry: suspend (SessionId) -> Boolean,
        projection: SyncProjection,
        candidate: SessionCandidate,
        pendingIds: Set<SessionId>,
        retained: Set<SessionId>,
        status: LocalSessionStatus,
        nowEpochMillis: Long,
        captureFrozen: suspend () -> FrozenStartSet,
    ): SessionReconcileResult {
        // Pending local commands are not overwritten before authoring: an intent
        // recorded mid-pass converges on the next pass instead of being transiently
        // reverted or revived here.
        val candidateId = when (candidate) {
            is SessionCandidate.Current -> candidate.start.sessionId
            is SessionCandidate.Concluded -> candidate.sessionId
            is SessionCandidate.Future, SessionCandidate.None -> null
        }
        if (status is LocalSessionStatus.Active) {
            if (status.record.sessionId in projection.conflictedSessionIds) {
                return bankIfExpired(bankExpiry, projection, status)
            }
            if (status.record.sessionId in pendingIds || (candidateId != null && candidateId in pendingIds)) {
                return bankIfExpired(bankExpiry, projection, status)
            }
        }
        return when (status) {
            is LocalSessionStatus.Active -> {
                applyActive(bankExpiry, projection, candidate, retained, status, nowEpochMillis, captureFrozen)
            }

            is LocalSessionStatus.Ended -> {
                adoptSuperseding(bankExpiry, projection, candidate, pendingIds, retained, status, nowEpochMillis, captureFrozen)
            }

            is LocalSessionStatus.Inactive -> {
                // Concluded expiries bank uniformly before branching; an
                // inactive row never falls back to an ended, expired, or
                // future session: only a currently eligible start activates
                // the device.
                val inactiveCurrent = candidate as? SessionCandidate.Current
                if (inactiveCurrent == null || inactiveCurrent.start.sessionId in pendingIds) {
                    // No eligible start, or a local command for this identity
                    // is mid-flight and converges on the next pass instead of
                    // being transiently adopted here.
                    SessionReconcileResult.Completed(status)
                } else {
                    adoptCandidate(inactiveCurrent.start, retained, nowEpochMillis, captureFrozen)
                }
            }
        }
    }

    private suspend fun adoptSuperseding(
        bankExpiry: suspend (SessionId) -> Boolean,
        projection: SyncProjection,
        candidate: SessionCandidate,
        pendingIds: Set<SessionId>,
        retained: Set<SessionId>,
        status: LocalSessionStatus.Ended,
        nowEpochMillis: Long,
        captureFrozen: suspend () -> FrozenStartSet,
    ): SessionReconcileResult {
        val banked = bankIfExpired(bankExpiry, projection, status)
        val current = candidate as? SessionCandidate.Current
        // A newer session supersedes the ended row; an older or concluded
        // candidate never falls back to it.
        if (current != null &&
            banked is SessionReconcileResult.Completed &&
            supersedes(status, current.start, pendingIds, projection)
        ) {
            return adoptCandidate(current.start, retained, nowEpochMillis, captureFrozen)
        }
        return banked
    }

    private fun supersedes(
        status: LocalSessionStatus.Ended,
        start: SynchronizedSessionStart,
        pendingIds: Set<SessionId>,
        projection: SyncProjection,
    ): Boolean {
        return start.sessionId != status.record.sessionId &&
            start.sessionId !in pendingIds &&
            start.sessionId !in projection.conflictedSessionIds
    }

    private suspend fun adoptCandidate(
        start: SynchronizedSessionStart,
        retained: Set<SessionId>,
        nowEpochMillis: Long,
        captureFrozen: suspend () -> FrozenStartSet,
    ): SessionReconcileResult {
        if (start.sessionId in retained) {
            // A locally retained terminal fact bars adoption of that identity
            // until the fact itself is reconciled with the replica, no matter
            // what the candidate evaluation claims. Retrying next exchange
            // converges instead of reviving the expired session.
            return SessionReconcileResult.Halted(SyncStatus.ACTION_REQUIRED)
        }
        // The receiving device captures its own local frozen summary once, at
        // adoption; repeated delivery keeps the stored set instead.
        return when (
            val adopted = sessions.adopt(
                start.sessionId,
                start.startEpochMillis,
                start.mandatoryEndEpochMillis,
                nowEpochMillis,
                captureFrozen(),
            )
        ) {
            is LocalSessionResult.Failure -> SessionReconcileResult.Halted(adopted.reason.toSyncStatus())
            is LocalSessionResult.Success -> SessionReconcileResult.Completed(adopted.value)
        }
    }

    private suspend fun applyActive(
        bankExpiry: suspend (SessionId) -> Boolean,
        projection: SyncProjection,
        candidate: SessionCandidate,
        retained: Set<SessionId>,
        status: LocalSessionStatus.Active,
        nowEpochMillis: Long,
        captureFrozen: suspend () -> FrozenStartSet,
    ): SessionReconcileResult {
        return when (candidate) {
            is SessionCandidate.Current -> {
                if (candidate.start.sessionId == status.record.sessionId) {
                    bankIfExpired(bankExpiry, projection, status)
                } else {
                    adoptCandidate(candidate.start, retained, nowEpochMillis, captureFrozen)
                }
            }

            is SessionCandidate.Future -> {
                bankIfExpired(bankExpiry, projection, status)
            }

            is SessionCandidate.Concluded -> {
                if (candidate.sessionId == status.record.sessionId) {
                    concludeSession(nowEpochMillis)
                } else if (projection.knows(status.record.sessionId)) {
                    // The workspace knows this session and still does not activate
                    // it: an older candidate never falls back, so the local row ends
                    // instead of enforcing beyond the converged intent.
                    concludeSession(nowEpochMillis)
                } else {
                    // Unknown to this workspace: a local session was already offered
                    // through the seed above, while an adopted session expires locally
                    // without ever replaying another workspace's work.
                    bankIfExpired(bankExpiry, projection, status)
                }
            }

            is SessionCandidate.None -> {
                bankIfExpired(bankExpiry, projection, status)
            }
        }
    }

    private suspend fun concludeSession(nowEpochMillis: Long,): SessionReconcileResult {
        // The matching session-end is already accepted, so no intent is recorded.
        // An expired row reaches here only defensively: the read above commits a due
        // expiry marker first, and a banked fact survives wall-clock rollback.
        return when (val ended = sessions.endEarly(nowEpochMillis, null)) {
            is LocalSessionResult.Failure -> SessionReconcileResult.Halted(ended.reason.toSyncStatus())
            is LocalSessionResult.Success -> SessionReconcileResult.Completed(ended.value)
        }
    }

    private suspend fun bankIfExpired(
        bankExpiry: suspend (SessionId) -> Boolean,
        projection: SyncProjection,
        status: LocalSessionStatus,
    ): SessionReconcileResult {
        if (status is LocalSessionStatus.Ended &&
            status.kind == SessionEndKind.EXPIRED &&
            projection.hasUnended(status.record.sessionId)
        ) {
            // The expiry marker is already durable in the local row; banking shares
            // nothing and only retires the local terminal fact into the replica so a
            // restart, rollback, or replacement cannot revive the session.
            if (!bankExpiry(status.record.sessionId)) {
                return SessionReconcileResult.Halted(SyncStatus.ACTION_REQUIRED)
            }
        }
        return SessionReconcileResult.Completed(status)
    }
}
