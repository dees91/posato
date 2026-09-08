package app.posato.feature.session.domain

import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncFormatLimits

internal data class SessionRecord(
    val sessionId: SessionId,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    init {
        require(startEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS)
        require(endEpochMillis in 0..SyncFormatLimits.MAX_PHYSICAL_MILLIS)
        require(endEpochMillis > startEpochMillis)
        require(endEpochMillis - startEpochMillis <= SyncFormatLimits.MAX_SESSION_DURATION_MILLIS)
    }

    override fun toString(): String {
        return "SessionRecord(redacted)"
    }
}

internal enum class SessionEndKind { ENDED_EARLY, EXPIRED }

internal sealed interface LocalSessionStatus {
    data object Inactive : LocalSessionStatus

    data class Active(
        val record: SessionRecord,
        val remainingMillis: Long,
        val frozenStartSet: FrozenStartSet? = null,
    ) : LocalSessionStatus {
        override fun toString(): String {
            return "LocalSessionStatus.Active(redacted)"
        }
    }

    data class Ended(
        val record: SessionRecord,
        val kind: SessionEndKind,
    ) : LocalSessionStatus {
        override fun toString(): String {
            return "LocalSessionStatus.Ended(redacted)"
        }
    }
}

internal sealed interface SessionEvaluation {
    data object NoSession : SessionEvaluation

    data class ShowActive(
        val record: SessionRecord,
        val remainingMillis: Long,
        val frozenStartSet: FrozenStartSet? = null,
    ) : SessionEvaluation {
        override fun toString(): String {
            return "SessionEvaluation.ShowActive(redacted)"
        }
    }

    data class ShowEnded(
        val record: SessionRecord,
        val kind: SessionEndKind,
    ) : SessionEvaluation {
        override fun toString(): String {
            return "SessionEvaluation.ShowEnded(redacted)"
        }
    }

    data class CommitExpiry(
        val record: SessionRecord,
    ) : SessionEvaluation {
        override fun toString(): String {
            return "SessionEvaluation.CommitExpiry(redacted)"
        }
    }
}

internal object SessionState {
    fun evaluate(
        record: SessionRecord?,
        endedEarly: Boolean,
        expiryMarked: Boolean,
        nowEpochMillis: Long,
    ): SessionEvaluation {
        return when {
            record == null -> {
                SessionEvaluation.NoSession
            }

            endedEarly -> {
                SessionEvaluation.ShowEnded(record, SessionEndKind.ENDED_EARLY)
            }

            expiryMarked -> {
                SessionEvaluation.ShowEnded(record, SessionEndKind.EXPIRED)
            }

            nowEpochMillis >= record.endEpochMillis -> {
                SessionEvaluation.CommitExpiry(record)
            }

            else -> {
                SessionEvaluation.ShowActive(record, record.endEpochMillis - nowEpochMillis)
            }
        }
    }
}
