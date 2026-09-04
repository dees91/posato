package app.posato.feature.session.data

import app.posato.feature.session.domain.LocalSessionStatus
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
    ): LocalSessionResult<LocalSessionStatus>

    suspend fun endEarly(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus>
}
