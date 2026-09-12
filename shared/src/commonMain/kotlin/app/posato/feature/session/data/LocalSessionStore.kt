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

internal interface LocalSessionSyncStore : LocalSessionStore {
    suspend fun recordIntents(write: SessionSyncWrite): LocalSessionResult<Unit>

    suspend fun readIntents(): LocalSessionResult<List<SequencedSessionIntent>>

    suspend fun deleteIntent(sequence: Long): LocalSessionResult<Unit>

    suspend fun clearIntents(): LocalSessionResult<Unit>
}
