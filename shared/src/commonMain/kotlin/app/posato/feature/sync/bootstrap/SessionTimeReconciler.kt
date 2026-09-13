package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.sync.domain.SessionCandidate
import app.posato.feature.sync.domain.SessionConclusionKind
import app.posato.feature.sync.domain.SessionReplicaSnapshot
import app.posato.feature.sync.domain.SyncReducer

internal suspend fun reconcileSessionTime(
    sessions: LocalSessionSyncStore,
    snapshot: SessionReplicaSnapshot,
    now: Long,
    captureFrozen: suspend () -> FrozenStartSet,
): SessionReconcileResult {
    val initial = when (val read = sessions.read(now)) {
        is LocalSessionResult.Failure -> return SessionReconcileResult.Halted(read.reason.toSyncStatus())
        is LocalSessionResult.Success -> read.value
    }
    val intents = when (val read = sessions.readIntents()) {
        is LocalSessionResult.Failure -> return SessionReconcileResult.Halted(read.reason.toSyncStatus())
        is LocalSessionResult.Success -> read.value
    }
    val hasPending = intents.any { it.workspaceId.contentEquals(snapshot.context.workspaceId.value.copyBytes()) }
    val unseeded = initial is LocalSessionStatus.Active && initial.origin == SessionOrigin.LOCAL &&
        !snapshot.projection.knows(initial.record.sessionId)
    if (hasPending || unseeded) return SessionReconcileResult.Completed(initial)
    return bankAndReconcileSessionTime(sessions, snapshot, now, captureFrozen)
}

private suspend fun bankAndReconcileSessionTime(
    sessions: LocalSessionSyncStore,
    snapshot: SessionReplicaSnapshot,
    now: Long,
    captureFrozen: suspend () -> FrozenStartSet,
): SessionReconcileResult {
    var markers = when (val read = sessions.retainedExpiryMarkers()) {
        is LocalSessionResult.Failure -> return SessionReconcileResult.Halted(read.reason.toSyncStatus())
        is LocalSessionResult.Success -> read.value
    }
    val candidate = SyncReducer.describeSession(snapshot.projection, now, snapshot.terminalExpiryFacts + markers)
    if (candidate is SessionCandidate.Concluded && candidate.kind == SessionConclusionKind.EXPIRED) {
        when (val banked = sessions.retainExpiryMarker(candidate.sessionId)) {
            is LocalSessionResult.Failure -> return SessionReconcileResult.Halted(banked.reason.toSyncStatus())
            is LocalSessionResult.Success -> markers = markers + candidate.sessionId
        }
    }
    val current = when (val read = sessions.read(now)) {
        is LocalSessionResult.Failure -> return SessionReconcileResult.Halted(read.reason.toSyncStatus())
        is LocalSessionResult.Success -> read.value
    }
    return SessionProjectionReconciler(sessions).apply(
        bankExpiry = { sessions.retainExpiryMarker(it) is LocalSessionResult.Success },
        projection = snapshot.projection,
        candidate = SyncReducer.describeSession(snapshot.projection, now, snapshot.terminalExpiryFacts + markers),
        pendingIds = emptySet(),
        retained = markers,
        status = current,
        nowEpochMillis = now,
        captureFrozen = captureFrozen,
    )
}
