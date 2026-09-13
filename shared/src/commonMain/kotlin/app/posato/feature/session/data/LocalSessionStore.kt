package app.posato.feature.session.data

import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SequencedSessionIntent
import app.posato.feature.session.domain.SessionSyncWrite
import app.posato.feature.sync.domain.SessionId

internal enum class LocalSessionFailure {
    INVALID_SESSION,
    ALREADY_ACTIVE,
    SESSION_NOT_ACTIVE,
    CORRUPTION,
    STORAGE_FAILURE,
}

internal sealed interface LocalSessionResult<out T> {
    data class Success<T>(
        val value: T,
    ) : LocalSessionResult<T> {
        override fun toString(): String {
            return "LocalSessionResult.Success(redacted)"
        }
    }

    data class Failure(
        val reason: LocalSessionFailure,
    ) : LocalSessionResult<Nothing>
}

internal interface LocalSessionStore {
    suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus>

    suspend fun start(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long,
        frozenStartSet: FrozenStartSet,
        workspaceId: ByteArray? = null,
    ): LocalSessionResult<LocalSessionStatus>

    suspend fun endEarly(
        nowEpochMillis: Long,
        workspaceId: ByteArray? = null,
    ): LocalSessionResult<LocalSessionStatus>

    suspend fun adopt(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long,
        frozenStartSet: FrozenStartSet,
    ): LocalSessionResult<LocalSessionStatus>

    /**
     * Records the terminal expiry of the current row for [sessionId] without
     * consulting the wall clock: the native extension already proved the end.
     * A row with another identity, or no row at all, reports
     * SESSION_NOT_ACTIVE instead of inventing a terminal fact.
     */
    suspend fun markExpired(sessionId: SessionId): LocalSessionResult<LocalSessionStatus>
}

/**
 * Identity-bound terminal facts that outlive the current row. A separate
 * collaborator implements this surface (see [SqlSessionExpiryStore]) so the
 * row store stays focused; the sync store below reunites both for callers
 * that need the whole local session contract through one dependency.
 */
internal interface LocalSessionExpiryStore {
    /**
     * Persists an identity-bound terminal fact independently of the current
     * row, for an expiry observed elsewhere (a superseded native signal or a
     * converged candidate) that must survive row replacement. Idempotent:
     * recording the same identity twice succeeds once.
     */
    suspend fun retainExpiryMarker(sessionId: SessionId): LocalSessionResult<Unit>

    /**
     * Lists every retained terminal fact, including identities that no longer
     * occupy the local row, so the reconciler can transfer each to the owning
     * replica independently of the current session.
     */
    suspend fun retainedExpiryMarkers(): LocalSessionResult<Set<SessionId>>

    /**
     * Drops one retained terminal fact after its replica transfer completes
     * and no local consumer needs it. Never called on an ambiguous bank
     * result: an undetermined marker is retained, not deleted.
     */
    suspend fun deleteExpiryMarker(sessionId: SessionId): LocalSessionResult<Unit>

    /**
     * Drops every retained terminal fact except the current row occupant's.
     * Called exactly once when workspace ownership ends (removal/re-link):
     * transfer obligations die with the discarded replica while the
     * occupant's marker stays for local rollback terminality.
     */
    suspend fun dropRetainedMarkersExceptCurrent(): LocalSessionResult<Unit>
}

internal interface LocalSessionSyncStore :
    LocalSessionStore,
    LocalSessionExpiryStore {
    suspend fun recordIntents(write: SessionSyncWrite): LocalSessionResult<Unit>

    suspend fun readIntents(): LocalSessionResult<List<SequencedSessionIntent>>

    suspend fun deleteIntent(sequence: Long): LocalSessionResult<Unit>

    suspend fun clearIntents(): LocalSessionResult<Unit>
}
