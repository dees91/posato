package app.posato.feature.session.ui

import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SequencedSessionIntent
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionEvaluation
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.domain.SessionSetup
import app.posato.feature.session.domain.SessionSetupResult
import app.posato.feature.session.domain.SessionState
import app.posato.feature.session.domain.SessionSyncWrite
import app.posato.feature.session.domain.StoredSessionIntent
import app.posato.feature.sync.domain.SessionId

internal class FakeLocalSessionStore : LocalSessionSyncStore {
    var record: SessionRecord? = null
    var frozenStartSet: FrozenStartSet? = null
    var endedEarly: Boolean = false
    var expiryMarked: Boolean = false
    var origin: SessionOrigin = SessionOrigin.LOCAL
    var readFailure: LocalSessionFailure? = null
    var startFailure: LocalSessionFailure? = null
    var endFailure: LocalSessionFailure? = null
    val reads = mutableListOf<Long>()
    var startCalls: Int = 0
    var endEarlyCalls: Int = 0
    var adoptCalls: Int = 0
    val startWorkspaces = mutableListOf<ByteArray?>()
    val endWorkspaces = mutableListOf<ByteArray?>()
    val intents = mutableListOf<SequencedSessionIntent>()
    private var nextSequence = 1L

    override suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
        reads += nowEpochMillis
        readFailure?.let { return LocalSessionResult.Failure(it) }
        return when (val evaluation = evaluate(nowEpochMillis)) {
            is SessionEvaluation.NoSession -> {
                LocalSessionResult.Success(LocalSessionStatus.Inactive)
            }

            is SessionEvaluation.ShowActive -> {
                LocalSessionResult.Success(LocalSessionStatus.Active(evaluation.record, evaluation.remainingMillis, frozenStartSet, origin))
            }

            is SessionEvaluation.ShowEnded -> {
                LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, evaluation.kind, origin))
            }

            is SessionEvaluation.CommitExpiry -> {
                expiryMarked = true
                frozenStartSet = null
                LocalSessionResult.Success(
                    LocalSessionStatus.Ended(evaluation.record, SessionEndKind.EXPIRED, origin),
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
        workspaceId: ByteArray?,
    ): LocalSessionResult<LocalSessionStatus> {
        startCalls += 1
        startWorkspaces += workspaceId?.copyOf()
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
                origin = SessionOrigin.LOCAL
                if (workspaceId != null) {
                    intents += SequencedSessionIntent(
                        nextSequence++,
                        workspaceId.copyOf(),
                        StoredSessionIntent.StartSession(sessionId, startEpochMillis, endEpochMillis),
                    )
                }
                LocalSessionResult.Success(LocalSessionStatus.Active(record!!, endEpochMillis - nowEpochMillis, frozenStartSet, origin))
            }
        }
    }

    override suspend fun endEarly(
        nowEpochMillis: Long,
        workspaceId: ByteArray?,
    ): LocalSessionResult<LocalSessionStatus> {
        endEarlyCalls += 1
        endWorkspaces += workspaceId?.copyOf()
        endFailure?.let { return LocalSessionResult.Failure(it) }
        return when (val evaluation = evaluate(nowEpochMillis)) {
            is SessionEvaluation.ShowActive -> {
                endedEarly = true
                frozenStartSet = null
                // A deliberate early end converges whether the row started here
                // or was adopted (SYNC-012 AC-05); mirrors SqlLocalSessionStore.
                if (workspaceId != null) {
                    intents += SequencedSessionIntent(
                        nextSequence++,
                        workspaceId.copyOf(),
                        StoredSessionIntent.EndSession(evaluation.record.sessionId),
                    )
                }
                LocalSessionResult.Success(
                    LocalSessionStatus.Ended(evaluation.record, SessionEndKind.ENDED_EARLY, origin),
                )
            }

            is SessionEvaluation.ShowEnded -> {
                LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, evaluation.kind, origin))
            }

            is SessionEvaluation.NoSession -> {
                LocalSessionResult.Failure(LocalSessionFailure.SESSION_NOT_ACTIVE)
            }

            is SessionEvaluation.CommitExpiry -> {
                expiryMarked = true
                frozenStartSet = null
                LocalSessionResult.Success(
                    LocalSessionStatus.Ended(evaluation.record, SessionEndKind.EXPIRED, origin),
                )
            }
        }
    }

    var markExpiredFailure: LocalSessionFailure? = null
    var markExpiredCalls: Int = 0

    override suspend fun markExpired(sessionId: SessionId): LocalSessionResult<LocalSessionStatus> {
        markExpiredCalls += 1
        val injected = markExpiredFailure
        val current = record
        return when {
            injected != null -> {
                LocalSessionResult.Failure(injected)
            }

            current == null || current.sessionId != sessionId -> {
                LocalSessionResult.Failure(LocalSessionFailure.SESSION_NOT_ACTIVE)
            }

            endedEarly -> {
                LocalSessionResult.Success(LocalSessionStatus.Ended(current, SessionEndKind.ENDED_EARLY, origin))
            }

            else -> {
                expiryMarked = true
                frozenStartSet = null
                LocalSessionResult.Success(LocalSessionStatus.Ended(current, SessionEndKind.EXPIRED, origin))
            }
        }
    }

    override suspend fun adopt(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long,
        frozenStartSet: FrozenStartSet,
    ): LocalSessionResult<LocalSessionStatus> {
        adoptCalls += 1
        val adopted = try {
            SessionRecord(sessionId, startEpochMillis, endEpochMillis)
        } catch (_: IllegalArgumentException) {
            return LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
        }
        if (startEpochMillis > nowEpochMillis || nowEpochMillis >= endEpochMillis) {
            return LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
        }
        record = adopted
        this.frozenStartSet = frozenStartSet
        endedEarly = false
        expiryMarked = false
        origin = SessionOrigin.ADOPTED
        return LocalSessionResult.Success(
            LocalSessionStatus.Active(adopted, endEpochMillis - nowEpochMillis, frozenStartSet, origin),
        )
    }

    override suspend fun recordIntents(write: SessionSyncWrite): LocalSessionResult<Unit> {
        write.intents.forEach { intent ->
            intents += SequencedSessionIntent(nextSequence++, write.workspaceId.copyOf(), intent)
        }
        return LocalSessionResult.Success(Unit)
    }

    override suspend fun readIntents(): LocalSessionResult<List<SequencedSessionIntent>> {
        return LocalSessionResult.Success(intents.toList())
    }

    override suspend fun deleteIntent(sequence: Long): LocalSessionResult<Unit> {
        intents.removeAll { row -> row.sequence == sequence }
        return LocalSessionResult.Success(Unit)
    }

    override suspend fun clearIntents(): LocalSessionResult<Unit> {
        intents.clear()
        return LocalSessionResult.Success(Unit)
    }

    fun intentKinds(): List<StoredSessionIntent> {
        return intents.map { row -> row.intent }
    }

    private fun evaluate(nowEpochMillis: Long): SessionEvaluation {
        return SessionState.evaluate(record, endedEarly, expiryMarked, nowEpochMillis)
    }
}
