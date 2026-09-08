package app.posato.feature.session.ui

import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionEvaluation
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.domain.SessionSetup
import app.posato.feature.session.domain.SessionSetupResult
import app.posato.feature.session.domain.SessionState
import app.posato.feature.sync.domain.SessionId

internal class FakeLocalSessionStore : LocalSessionStore {
    var record: SessionRecord? = null
    var frozenStartSet: FrozenStartSet? = null
    var endedEarly: Boolean = false
    var expiryMarked: Boolean = false
    var startFailure: LocalSessionFailure? = null
    var endFailure: LocalSessionFailure? = null
    val reads = mutableListOf<Long>()
    var startCalls: Int = 0
    var endEarlyCalls: Int = 0

    override suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
        reads += nowEpochMillis
        return when (val evaluation = evaluate(nowEpochMillis)) {
            is SessionEvaluation.NoSession -> {
                LocalSessionResult.Success(LocalSessionStatus.Inactive)
            }

            is SessionEvaluation.ShowActive -> {
                LocalSessionResult.Success(LocalSessionStatus.Active(evaluation.record, evaluation.remainingMillis, frozenStartSet))
            }

            is SessionEvaluation.ShowEnded -> {
                LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, evaluation.kind))
            }

            is SessionEvaluation.CommitExpiry -> {
                expiryMarked = true
                frozenStartSet = null
                LocalSessionResult.Success(
                    LocalSessionStatus.Ended(evaluation.record, SessionEndKind.EXPIRED),
                )
            }
        }
    }

    override suspend fun start(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long,
        frozenStartSet: FrozenStartSet,
    ): LocalSessionResult<LocalSessionStatus> {
        startCalls += 1
        startFailure?.let { return LocalSessionResult.Failure(it) }
        val valid = SessionSetup.validateEndTime(endEpochMillis, nowEpochMillis) is SessionSetupResult.Valid &&
            startEpochMillis == nowEpochMillis && endEpochMillis - startEpochMillis >= SessionLimits.MIN_DURATION_MILLIS
        return when {
            evaluate(nowEpochMillis) is SessionEvaluation.ShowActive -> {
                LocalSessionResult.Failure(LocalSessionFailure.ALREADY_ACTIVE)
            }

            !valid -> {
                LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
            }

            else -> {
                record = SessionRecord(sessionId, startEpochMillis, endEpochMillis)
                this.frozenStartSet = frozenStartSet
                endedEarly = false
                expiryMarked = false
                LocalSessionResult.Success(LocalSessionStatus.Active(record!!, endEpochMillis - nowEpochMillis, frozenStartSet))
            }
        }
    }

    override suspend fun endEarly(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
        endEarlyCalls += 1
        endFailure?.let { return LocalSessionResult.Failure(it) }
        return when (val evaluation = evaluate(nowEpochMillis)) {
            is SessionEvaluation.ShowActive -> {
                endedEarly = true
                frozenStartSet = null
                LocalSessionResult.Success(
                    LocalSessionStatus.Ended(evaluation.record, SessionEndKind.ENDED_EARLY),
                )
            }

            is SessionEvaluation.ShowEnded -> {
                LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, evaluation.kind))
            }

            is SessionEvaluation.NoSession -> {
                LocalSessionResult.Failure(LocalSessionFailure.SESSION_NOT_ACTIVE)
            }

            is SessionEvaluation.CommitExpiry -> {
                expiryMarked = true
                frozenStartSet = null
                LocalSessionResult.Success(
                    LocalSessionStatus.Ended(evaluation.record, SessionEndKind.EXPIRED),
                )
            }
        }
    }

    private fun evaluate(nowEpochMillis: Long): SessionEvaluation {
        return SessionState.evaluate(record, endedEarly, expiryMarked, nowEpochMillis)
    }
}
