package app.posato.feature.session.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlSessionExpiryStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LocalSessionExpiryStore {
    override suspend fun retainExpiryMarker(sessionId: SessionId): LocalSessionResult<Unit> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                database.localSessionQueries.insertExpiryMarker(sessionId.value.copyBytes())
                LocalSessionResult.Success(Unit)
            }
        }
    }

    override suspend fun retainedExpiryMarkers(): LocalSessionResult<Set<SessionId>> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val rows = try {
                    database.localSessionQueries.selectExpiryMarkers().awaitAsList()
                } catch (_: Exception) {
                    return@localSessionTransact LocalSessionResult.Failure(LocalSessionFailure.STORAGE_FAILURE)
                }
                val markers = rows.map { bytes ->
                    SyncIdentifier.fromUuidV4Bytes(bytes)?.let(::SessionId)
                        ?: return@localSessionTransact LocalSessionResult.Failure(LocalSessionFailure.CORRUPTION)
                }.toSet()
                LocalSessionResult.Success(markers)
            }
        }
    }

    override suspend fun deleteExpiryMarker(sessionId: SessionId): LocalSessionResult<Unit> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                database.localSessionQueries.deleteExpiryMarker(sessionId.value.copyBytes())
                LocalSessionResult.Success(Unit)
            }
        }
    }

    override suspend fun dropRetainedMarkersExceptCurrent(): LocalSessionResult<Unit> {
        return withContext(databaseDispatcher) {
            database.localSessionTransact {
                val stored = database.readStoredLocalSession()
                if (stored == null) {
                    database.localSessionQueries.deleteAllExpiryMarkers()
                } else {
                    database.localSessionQueries.deleteStaleExpiryMarkers(stored.record.sessionId.value.copyBytes())
                }
                LocalSessionResult.Success(Unit)
            }
        }
    }
}
