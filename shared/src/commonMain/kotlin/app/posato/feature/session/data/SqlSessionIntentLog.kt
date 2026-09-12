package app.posato.feature.session.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.domain.SequencedSessionIntent
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.domain.SessionSyncWrite
import app.posato.feature.session.domain.StoredSessionIntent
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlSessionIntentLog(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) {
    suspend fun recordIntents(write: SessionSyncWrite): LocalSessionResult<Unit> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                write.intents.forEach { intent ->
                    insertIntent(write.workspaceId, intent)
                }
                LocalSessionResult.Success(Unit)
            }
        }
    }

    suspend fun readIntents(): LocalSessionResult<List<SequencedSessionIntent>> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val rows = try {
                    database.syncSessionQueries.selectSessionIntents().awaitAsList()
                } catch (_: Exception) {
                    return@localSessionTransact LocalSessionResult.Failure(LocalSessionFailure.STORAGE_FAILURE)
                }
                val intents = rows.map { row ->
                    toSequencedIntent(
                        row.sequence,
                        row.workspace_id,
                        row.kind,
                        row.session_id,
                        row.start_epoch_millis,
                        row.end_epoch_millis,
                    ) ?: return@localSessionTransact LocalSessionResult.Failure(LocalSessionFailure.CORRUPTION)
                }
                LocalSessionResult.Success(intents)
            }
        }
    }

    suspend fun deleteIntent(sequence: Long): LocalSessionResult<Unit> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                database.syncSessionQueries.deleteSessionIntent(sequence)
                LocalSessionResult.Success(Unit)
            }
        }
    }

    suspend fun clearIntents(): LocalSessionResult<Unit> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                database.syncSessionQueries.deleteSessionIntents()
                LocalSessionResult.Success(Unit)
            }
        }
    }

    suspend fun insertIntent(
        workspaceId: ByteArray,
        intent: StoredSessionIntent,
    ) {
        when (intent) {
            is StoredSessionIntent.StartSession -> {
                database.syncSessionQueries.insertSessionIntent(
                    workspaceId = workspaceId,
                    kind = SESSION_START_KIND,
                    sessionId = intent.sessionId.value.copyBytes(),
                    startEpochMillis = intent.startEpochMillis,
                    endEpochMillis = intent.mandatoryEndEpochMillis,
                )
            }

            is StoredSessionIntent.EndSession -> {
                database.syncSessionQueries.insertSessionIntent(
                    workspaceId = workspaceId,
                    kind = SESSION_END_KIND,
                    sessionId = intent.sessionId.value.copyBytes(),
                    startEpochMillis = null,
                    endEpochMillis = null,
                )
            }
        }
    }

    private fun toSequencedIntent(
        sequence: Long,
        workspaceId: ByteArray,
        kind: String,
        sessionIdBytes: ByteArray,
        startEpochMillis: Long?,
        endEpochMillis: Long?,
    ): SequencedSessionIntent? {
        val sessionId = SyncIdentifier.fromUuidV4Bytes(sessionIdBytes)?.let(::SessionId) ?: return null
        val intent = when (kind) {
            SESSION_START_KIND -> {
                val start = startEpochMillis ?: return null
                val end = endEpochMillis ?: return null
                try {
                    SessionRecord(sessionId, start, end)
                } catch (_: IllegalArgumentException) {
                    return null
                }
                StoredSessionIntent.StartSession(sessionId, start, end)
            }

            SESSION_END_KIND -> {
                if (startEpochMillis != null || endEpochMillis != null) {
                    return null
                }
                StoredSessionIntent.EndSession(sessionId)
            }

            else -> {
                return null
            }
        }
        return SequencedSessionIntent(sequence, workspaceId, intent)
    }
}

internal suspend fun <T> PosatoDatabase.localSessionTransact(block: suspend () -> LocalSessionResult<T>): LocalSessionResult<T> {
    return try {
        transactionWithResult {
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

internal class LocalSessionStoreException(
    val reason: LocalSessionFailure,
) : Exception()

internal fun fail(reason: LocalSessionFailure): Nothing {
    throw LocalSessionStoreException(reason)
}

private const val SESSION_START_KIND: String = "session_start"
private const val SESSION_END_KIND: String = "session_end"
