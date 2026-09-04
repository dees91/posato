package app.posato.feature.session.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionEvaluation
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.domain.SessionSetup
import app.posato.feature.session.domain.SessionSetupResult
import app.posato.feature.session.domain.SessionState
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlLocalSessionStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LocalSessionStore {
    override suspend fun read(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
        return withContext(databaseDispatcher) {
            transact {
                when (val evaluation = evaluateStored(nowEpochMillis)) {
                    is SessionEvaluation.NoSession -> {
                        LocalSessionResult.Success(LocalSessionStatus.Inactive)
                    }

                    is SessionEvaluation.ShowActive -> {
                        LocalSessionResult.Success(LocalSessionStatus.Active(evaluation.record, evaluation.remainingMillis))
                    }

                    is SessionEvaluation.ShowEnded -> {
                        LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, evaluation.kind))
                    }

                    is SessionEvaluation.CommitExpiry -> {
                        commitExpiry(evaluation.record)
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
    ): LocalSessionResult<LocalSessionStatus> {
        return withContext(databaseDispatcher) {
            transact {
                when (evaluateStored(nowEpochMillis)) {
                    is SessionEvaluation.ShowActive -> {
                        LocalSessionResult.Failure(LocalSessionFailure.ALREADY_ACTIVE)
                    }

                    else -> {
                        startWhenInactive(sessionId, startEpochMillis, endEpochMillis, nowEpochMillis)
                    }
                }
            }
        }
    }

    override suspend fun endEarly(nowEpochMillis: Long): LocalSessionResult<LocalSessionStatus> {
        return withContext(databaseDispatcher) {
            transact {
                when (val evaluation = evaluateStored(nowEpochMillis)) {
                    is SessionEvaluation.ShowActive -> {
                        database.localSessionQueries.markEndedEarly()
                        LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, SessionEndKind.ENDED_EARLY))
                    }

                    is SessionEvaluation.ShowEnded -> {
                        LocalSessionResult.Success(LocalSessionStatus.Ended(evaluation.record, evaluation.kind))
                    }

                    is SessionEvaluation.NoSession -> {
                        LocalSessionResult.Failure(LocalSessionFailure.SESSION_NOT_ACTIVE)
                    }

                    is SessionEvaluation.CommitExpiry -> {
                        commitExpiry(evaluation.record)
                    }
                }
            }
        }
    }

    private suspend fun transact(block: suspend () -> LocalSessionResult<LocalSessionStatus>): LocalSessionResult<LocalSessionStatus> {
        return try {
            database.transactionWithResult {
                block()
            }
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (failure: LocalSessionStoreException) {
            LocalSessionResult.Failure(failure.reason)
        } catch (_: Exception) {
            LocalSessionResult.Failure(LocalSessionFailure.STORAGE_FAILURE)
        }
    }

    private suspend fun evaluateStored(nowEpochMillis: Long): SessionEvaluation {
        val stored = readStoredOrThrow()

        return SessionState.evaluate(stored?.record, stored?.endedEarly == true, stored?.expiryMarked == true, nowEpochMillis)
    }

    private suspend fun startWhenInactive(
        sessionId: SessionId,
        startEpochMillis: Long,
        endEpochMillis: Long,
        nowEpochMillis: Long,
    ): LocalSessionResult<LocalSessionStatus> {
        return when (SessionSetup.validateEndTime(endEpochMillis, nowEpochMillis)) {
            is SessionSetupResult.Invalid -> {
                LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
            }

            is SessionSetupResult.Valid -> {
                if (startEpochMillis != nowEpochMillis || endEpochMillis - startEpochMillis < SessionLimits.MIN_DURATION_MILLIS) {
                    LocalSessionResult.Failure(LocalSessionFailure.INVALID_SESSION)
                } else {
                    database.localSessionQueries.deleteStaleExpiryMarkers(sessionId.value.copyBytes())
                    database.localSessionQueries.replaceSession(
                        session_id = sessionId.value.copyBytes(),
                        start_epoch_millis = startEpochMillis,
                        end_epoch_millis = endEpochMillis,
                    )
                    LocalSessionResult.Success(
                        LocalSessionStatus.Active(
                            SessionRecord(sessionId, startEpochMillis, endEpochMillis),
                            endEpochMillis - nowEpochMillis,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun commitExpiry(record: SessionRecord): LocalSessionResult<LocalSessionStatus> {
        database.localSessionQueries.insertExpiryMarker(record.sessionId.value.copyBytes())

        return LocalSessionResult.Success(LocalSessionStatus.Ended(record, SessionEndKind.EXPIRED))
    }

    private suspend fun readStoredOrThrow(): StoredLocalSession? {
        val rows = database.localSessionQueries.selectSession().awaitAsList()
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
        val markers = database.localSessionQueries.selectExpiryMarker(row.session_id).awaitAsList()
        if (markers.size > 1) {
            fail(LocalSessionFailure.CORRUPTION)
        }

        return StoredLocalSession(record, row.ended_early == 1L, markers.isNotEmpty())
    }
}

private class StoredLocalSession(
    val record: SessionRecord,
    val endedEarly: Boolean,
    val expiryMarked: Boolean,
)

private class LocalSessionStoreException(
    val reason: LocalSessionFailure,
) : Exception()

private fun fail(reason: LocalSessionFailure): Nothing {
    throw LocalSessionStoreException(reason)
}
