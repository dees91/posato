package app.posato.feature.session.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
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
import app.posato.feature.sync.domain.SyncIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlLocalSessionStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LocalSessionSyncStore {
    private val intents = SqlSessionIntentLog(database, databaseDispatcher)

    override suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val evaluated = evaluateStored(nowEpochMillis)
                when (val evaluation = evaluated.evaluation) {
                    is SessionEvaluation.NoSession -> {
                        LocalSessionResult.Success(LocalSessionStatus.Inactive)
                    }

                    is SessionEvaluation.ShowActive -> {
                        LocalSessionResult.Success(
                            LocalSessionStatus.Active(
                                evaluation.record,
                                evaluation.remainingMillis,
                                evaluation.frozenStartSet,
                                evaluated.originOrThrow(),
                            ),
                        )
                    }

                    is SessionEvaluation.ShowEnded -> {
                        LocalSessionResult.Success(
                            LocalSessionStatus.Ended(evaluation.record, evaluation.kind, evaluated.originOrThrow()),
                        )
                    }

                    is SessionEvaluation.CommitExpiry -> {
                        commitExpiry(evaluation.record, evaluated.originOrThrow())
                    }
                }
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
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val evaluated = evaluateStored(nowEpochMillis)
                when (val evaluation = evaluated.evaluation) {
                    is SessionEvaluation.ShowActive -> {
                        LocalSessionResult.Failure(LocalSessionFailure.ALREADY_ACTIVE)
                    }

                    else -> {
                        if (evaluation is SessionEvaluation.CommitExpiry) {
                            commitExpiry(evaluation.record, evaluated.originOrThrow())
                        }
                        startWhenInactive(sessionId, startEpochMillis, endEpochMillis, nowEpochMillis, frozenStartSet, workspaceId)
                    }
                }
            }
        }
    }

    override suspend fun endEarly(
        nowEpochMillis: Long,
        workspaceId: ByteArray?,
    ): LocalSessionResult<LocalSessionStatus> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val evaluated = evaluateStored(nowEpochMillis)
                when (val evaluation = evaluated.evaluation) {
                    is SessionEvaluation.ShowActive -> {
                        database.localSessionQueries.markEndedEarly()
                        database.localSessionQueries.clearFrozenStartSet()
                        // A deliberate early end converges whether the row started
                        // here or was adopted: AC-05 ends from the receiving
                        // peer. No-ended-backfill scoping lives in the
                        // reconciler seed gate (the workspace must already
                        // hold the un-ended start), not in the origin.
                        if (workspaceId != null) {
                            intents.insertIntent(workspaceId, StoredSessionIntent.EndSession(evaluation.record.sessionId))
                        }
                        LocalSessionResult.Success(
                            LocalSessionStatus.Ended(evaluation.record, SessionEndKind.ENDED_EARLY, evaluated.originOrThrow()),
                        )
                    }

                    is SessionEvaluation.ShowEnded -> {
                        LocalSessionResult.Success(
                            LocalSessionStatus.Ended(evaluation.record, evaluation.kind, evaluated.originOrThrow()),
                        )
                    }

                    is SessionEvaluation.NoSession -> {
                        LocalSessionResult.Failure(LocalSessionFailure.SESSION_NOT_ACTIVE)
                    }

                    is SessionEvaluation.CommitExpiry -> {
                        commitExpiry(evaluation.record, evaluated.originOrThrow())
                    }
                }
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
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val record = try {
                    SessionRecord(sessionId, startEpochMillis, endEpochMillis)
                } catch (_: IllegalArgumentException) {
                    return@localSessionTransact LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
                }
                if (startEpochMillis > nowEpochMillis || nowEpochMillis >= endEpochMillis) {
                    return@localSessionTransact LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
                }
                // An adopted session replaces the local row by identity; like a local
                // start it retains only the new session's marker. SYNC-012 reconciles
                // from the current row.
                database.localSessionQueries.deleteStaleExpiryMarkers(sessionId.value.copyBytes())
                database.localSessionQueries.replaceSession(
                    session_id = sessionId.value.copyBytes(),
                    start_epoch_millis = startEpochMillis,
                    end_epoch_millis = endEpochMillis,
                    frozen_domains = frozenStartSet.toStorageValue(),
                    frozen_application_count = frozenStartSet.applicationCount?.toLong(),
                    origin = SessionOrigin.ADOPTED.storageValue,
                )
                LocalSessionResult.Success(
                    LocalSessionStatus.Active(
                        record,
                        endEpochMillis - nowEpochMillis,
                        frozenStartSet,
                        SessionOrigin.ADOPTED,
                    ),
                )
            }
        }
    }

    override suspend fun recordIntents(write: SessionSyncWrite): LocalSessionResult<Unit> {
        return intents.recordIntents(write)
    }

    override suspend fun readIntents(): LocalSessionResult<List<SequencedSessionIntent>> {
        return intents.readIntents()
    }

    override suspend fun deleteIntent(sequence: Long): LocalSessionResult<Unit> {
        return intents.deleteIntent(sequence)
    }

    override suspend fun clearIntents(): LocalSessionResult<Unit> {
        return intents.clearIntents()
    }

    private suspend fun evaluateStored(nowEpochMillis: Long): EvaluatedStored {
        val stored = database.readStoredLocalSession()
        if (stored == null) {
            return EvaluatedStored(SessionEvaluation.NoSession, null)
        }
        val frozenStartSet = try {
            FrozenStartSet.parseStored(stored.frozenDomains, stored.frozenApplicationCount)
        } catch (_: IllegalArgumentException) {
            fail(LocalSessionFailure.CORRUPTION)
        }

        val evaluation = when (val evaluation = SessionState.evaluate(stored.record, stored.endedEarly, stored.expiryMarked, nowEpochMillis)) {
            is SessionEvaluation.ShowActive -> {
                evaluation.copy(frozenStartSet = frozenStartSet)
            }

            else -> {
                evaluation
            }
        }
        return EvaluatedStored(evaluation, stored.origin)
    }

    private suspend fun startWhenInactive(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long,
        frozenStartSet: FrozenStartSet,
        workspaceId: ByteArray?,
    ): LocalSessionResult<LocalSessionStatus> {
        return when (SessionSetup.validateEndTime(endEpochMillis, nowEpochMillis)) {
            is SessionSetupResult.Invalid -> {
                LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
            }

            is SessionSetupResult.Valid -> {
                if (startEpochMillis != nowEpochMillis || endEpochMillis - startEpochMillis < SessionLimits.MIN_DURATION_MILLIS) {
                    LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
                } else {
                    // Retains only the new session's marker; SYNC-012 reconciles from the current row.
                    database.localSessionQueries.deleteStaleExpiryMarkers(sessionId.value.copyBytes())
                    database.localSessionQueries.replaceSession(
                        session_id = sessionId.value.copyBytes(),
                        start_epoch_millis = startEpochMillis,
                        end_epoch_millis = endEpochMillis,
                        frozen_domains = frozenStartSet.toStorageValue(),
                        frozen_application_count = frozenStartSet.applicationCount?.toLong(),
                        origin = SessionOrigin.LOCAL.storageValue,
                    )
                    if (workspaceId != null) {
                        intents.insertIntent(
                            workspaceId,
                            StoredSessionIntent.StartSession(sessionId, startEpochMillis, endEpochMillis),
                        )
                    }
                    LocalSessionResult.Success(
                        LocalSessionStatus.Active(
                            SessionRecord(sessionId, startEpochMillis, endEpochMillis),
                            endEpochMillis - nowEpochMillis,
                            frozenStartSet,
                            SessionOrigin.LOCAL,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun commitExpiry(
        record: SessionRecord,
        origin: SessionOrigin,
    ): LocalSessionResult<LocalSessionStatus> {
        database.localSessionQueries.insertExpiryMarker(record.sessionId.value.copyBytes())
        database.localSessionQueries.clearFrozenStartSet()

        return LocalSessionResult.Success(LocalSessionStatus.Ended(record, SessionEndKind.EXPIRED, origin))
    }
}

private suspend fun PosatoDatabase.readStoredLocalSession(): StoredLocalSession? {
    val rows = localSessionQueries.selectSession().awaitAsList()
    if (rows.isEmpty()) {
        return null
    }
    if (rows.size != 1) {
        fail(LocalSessionFailure.CORRUPTION)
    }
    val row = rows.single()
    val sessionId = SyncIdentifier.fromUuidV4Bytes(row.session_id)?.let(::SessionId) ?: fail(LocalSessionFailure.CORRUPTION)
    val record = try {
        SessionRecord(sessionId, row.start_epoch_millis, row.end_epoch_millis)
    } catch (_: IllegalArgumentException) {
        fail(LocalSessionFailure.CORRUPTION)
    }
    val origin = parseStoredOrigin(row.origin) ?: fail(LocalSessionFailure.CORRUPTION)
    val markers = localSessionQueries.selectExpiryMarker(row.session_id).awaitAsList()
    if (markers.size > 1) {
        fail(LocalSessionFailure.CORRUPTION)
    }

    return StoredLocalSession(record, row.ended_early == 1L, markers.isNotEmpty(), row.frozen_domains, row.frozen_application_count, origin)
}

private class StoredLocalSession(
    val record: SessionRecord,
    val endedEarly: Boolean,
    val expiryMarked: Boolean,
    val frozenDomains: String?,
    val frozenApplicationCount: Long?,
    val origin: SessionOrigin,
)

private class EvaluatedStored(
    val evaluation: SessionEvaluation,
    val origin: SessionOrigin?,
) {
    fun originOrThrow(): SessionOrigin {
        return origin ?: fail(LocalSessionFailure.CORRUPTION)
    }
}

private val SessionOrigin.storageValue: String
    get() = when (this) {
        SessionOrigin.LOCAL -> "local"
        SessionOrigin.ADOPTED -> "adopted"
    }

internal fun parseStoredOrigin(value: String): SessionOrigin? {
    return when (value) {
        SessionOrigin.LOCAL.storageValue -> SessionOrigin.LOCAL
        SessionOrigin.ADOPTED.storageValue -> SessionOrigin.ADOPTED
        else -> null
    }
}
