package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionSyncWrite
import app.posato.feature.session.domain.StoredSessionIntent
import app.posato.feature.sync.domain.SessionCandidate
import app.posato.feature.sync.domain.SessionConclusionKind
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.domain.SynchronizedSessionStart

internal sealed interface SessionReconcileResult {
    data class Completed(
        val status: LocalSessionStatus,
    ) : SessionReconcileResult {
        override fun toString(): String {
            return "SessionReconcileResult.Completed(redacted)"
        }
    }

    data class Halted(
        val status: SyncStatus,
    ) : SessionReconcileResult
}

internal class SessionReconciler(
    private val sessions: LocalSessionSyncStore,
) {
    suspend fun reconcile(
        writer: SyncWriter,
        workspace: EstablishedWorkspace,
        nowEpochMillis: Long,
        authoring: SessionSyncAuthoring,
        captureFrozen: suspend () -> FrozenStartSet,
    ): SessionReconcileResult {
        val status = when (val read = sessions.read(nowEpochMillis)) {
            is LocalSessionResult.Failure -> return SessionReconcileResult.Halted(read.reason.toSyncStatus())
            is LocalSessionResult.Success -> read.value
        }
        val workspaceId = workspace.context.workspaceId.value.copyBytes()
        val projection = writer.projection()
        if (!seedIntents(projection, workspaceId, status)) {
            return SessionReconcileResult.Halted(SyncStatus.ACTION_REQUIRED)
        }
        // Pipeline order: authoring publishes local starts first so the
        // replica learns them; retained terminal facts transfer next;
        // only then is the candidate evaluated fresh, so a banked session
        // can never come back as Current from a stale evaluation.
        if (!authoring.drain(writer, workspaceId)) {
            return SessionReconcileResult.Halted(SyncStatus.ACTION_REQUIRED)
        }
        drainRetainedMarkers(writer, status)
        val pendingRead = sessions.readIntents()
        if (pendingRead is LocalSessionResult.Failure) {
            return SessionReconcileResult.Halted(pendingRead.reason.toSyncStatus())
        }
        check(pendingRead is LocalSessionResult.Success)
        val pendingIds = pendingRead.value
            .filter { row -> row.workspaceId.contentEquals(workspaceId) }
            .map { row ->
                when (val intent = row.intent) {
                    is StoredSessionIntent.StartSession -> intent.sessionId
                    is StoredSessionIntent.EndSession -> intent.sessionId
                }
            }.toSet()
        val freshProjection = writer.projection()
        val candidate = writer.sessionCandidate(nowEpochMillis)
        // Newly observed expiry banks uniformly before branching, for every
        // local variant alike. A failed bank halts the pass without adopting:
        // the fact is retried next exchange instead of being lost or revived.
        val concluded = candidate as? SessionCandidate.Concluded
        if (concluded != null && concluded.kind == SessionConclusionKind.EXPIRED) {
            if (!writer.markTerminalExpiry(concluded.sessionId)) {
                return SessionReconcileResult.Halted(SyncStatus.ACTION_REQUIRED)
            }
        }
        return when (val retainedRead = sessions.retainedExpiryMarkers()) {
            is LocalSessionResult.Failure -> SessionReconcileResult.Halted(retainedRead.reason.toSyncStatus())

            is LocalSessionResult.Success -> applyProjection(
                writer,
                freshProjection,
                candidate,
                pendingIds,
                retainedRead.value,
                status,
                nowEpochMillis,
                captureFrozen,
            )
        }
    }

    private suspend fun drainRetainedMarkers(
        writer: SyncWriter,
        status: LocalSessionStatus,
    ) {
        // Best-effort transfer of every retained terminal fact, independent
        // of the current row. An ambiguous bank result never deletes: the
        // marker is retained and re-evaluated next exchange, while unrelated
        // work continues. Only a completed transfer with no local consumer
        // left retires the marker.
        val markers = when (val read = sessions.retainedExpiryMarkers()) {
            is LocalSessionResult.Failure -> return
            is LocalSessionResult.Success -> read.value
        }
        val occupant = (status as? LocalSessionStatus.Active)?.record?.sessionId
            ?: (status as? LocalSessionStatus.Ended)?.record?.sessionId
        // Best effort like every other reconciler store call: an ambiguous
        // bank keeps its marker for the next exchange, and a failed delete
        // retries through the idempotent re-transfer.
        markers.forEach { marker ->
            if (writer.markTerminalExpiry(marker) && marker != occupant) {
                sessions.deleteExpiryMarker(marker)
            }
        }
    }

    private suspend fun seedIntents(
        projection: SyncProjection,
        workspaceId: ByteArray,
        status: LocalSessionStatus,
    ): Boolean {
        val queued = when (val read = sessions.readIntents()) {
            is LocalSessionResult.Failure -> return false
            is LocalSessionResult.Success -> read.value
        }.filter { row -> row.workspaceId.contentEquals(workspaceId) }
        val queuedStarts = queued.mapNotNull { row ->
            (row.intent as? StoredSessionIntent.StartSession)?.sessionId
        }.toSet()
        val queuedEnds = queued.mapNotNull { row ->
            (row.intent as? StoredSessionIntent.EndSession)?.sessionId
        }.toSet()
        val seed = seedForStatus(status, projection, queuedStarts, queuedEnds) ?: return true
        return when (sessions.recordIntents(SessionSyncWrite(workspaceId, listOf(seed)))) {
            is LocalSessionResult.Success -> true
            is LocalSessionResult.Failure -> false
        }
    }

    private fun seedForStatus(
        status: LocalSessionStatus,
        projection: SyncProjection,
        queuedStarts: Set<SessionId>,
        queuedEnds: Set<SessionId>,
    ): StoredSessionIntent? {
        return when (status) {
            // D1: linking publishes only a still-active local session, once, with its
            // original identifier and end. The projection gate keeps it to once per
            // workspace: after authoring, the projection holds the identifier.
            is LocalSessionStatus.Active -> {
                if (status.origin != SessionOrigin.LOCAL) {
                    null
                } else if (projection.knows(status.record.sessionId) || status.record.sessionId in queuedStarts) {
                    null
                } else {
                    StoredSessionIntent.StartSession(status.record.sessionId, status.record.startEpochMillis, status.record.endEpochMillis)
                }
            }

            // A locally ended session converges its end only when the workspace
            // already holds its un-ended start, whether the row started here
            // or was adopted (AC-05 ends from the receiving peer). Ended
            // sessions are never backfilled.
            is LocalSessionStatus.Ended -> {
                if (status.kind != SessionEndKind.ENDED_EARLY) {
                    null
                } else if (!projection.hasUnended(status.record.sessionId) || status.record.sessionId in queuedEnds) {
                    null
                } else {
                    StoredSessionIntent.EndSession(status.record.sessionId)
                }
            }

            is LocalSessionStatus.Inactive -> {
                null
            }
        }
    }

    private suspend fun applyProjection(
        writer: SyncWriter,
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
                return bankIfExpired(writer, projection, status)
            }
            if (status.record.sessionId in pendingIds || (candidateId != null && candidateId in pendingIds)) {
                return bankIfExpired(writer, projection, status)
            }
        }
        return when (status) {
            is LocalSessionStatus.Active -> {
                applyActive(writer, projection, candidate, retained, status, nowEpochMillis, captureFrozen)
            }

            is LocalSessionStatus.Ended -> {
                adoptSuperseding(writer, projection, candidate, pendingIds, retained, status, nowEpochMillis, captureFrozen)
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
        writer: SyncWriter,
        projection: SyncProjection,
        candidate: SessionCandidate,
        pendingIds: Set<SessionId>,
        retained: Set<SessionId>,
        status: LocalSessionStatus.Ended,
        nowEpochMillis: Long,
        captureFrozen: suspend () -> FrozenStartSet,
    ): SessionReconcileResult {
        val banked = bankIfExpired(writer, projection, status)
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
        writer: SyncWriter,
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
                    bankIfExpired(writer, projection, status)
                } else {
                    adoptCandidate(candidate.start, retained, nowEpochMillis, captureFrozen)
                }
            }

            is SessionCandidate.Future -> {
                bankIfExpired(writer, projection, status)
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
                    bankIfExpired(writer, projection, status)
                }
            }

            is SessionCandidate.None -> {
                bankIfExpired(writer, projection, status)
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
        writer: SyncWriter,
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
            if (!writer.markTerminalExpiry(status.record.sessionId)) {
                return SessionReconcileResult.Halted(SyncStatus.ACTION_REQUIRED)
            }
        }
        return SessionReconcileResult.Completed(status)
    }
}

private fun SyncProjection.knows(sessionId: SessionId): Boolean {
    return eligibleSessionStarts.any { start -> start.sessionId == sessionId } ||
        sessionId in conflictedSessionIds
}

private fun SyncProjection.hasUnended(sessionId: SessionId): Boolean {
    return eligibleSessionStarts.any { start -> start.sessionId == sessionId && !start.isEnded }
}

internal fun LocalSessionFailure.toSyncStatus(): SyncStatus {
    return when (this) {
        LocalSessionFailure.CORRUPTION -> SyncStatus.ACTION_REQUIRED
        else -> SyncStatus.RETRYABLE
    }
}
