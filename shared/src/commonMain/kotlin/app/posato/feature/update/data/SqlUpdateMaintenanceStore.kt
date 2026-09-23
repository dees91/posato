package app.posato.feature.update.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.data.hasActiveLocalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal sealed interface MaintenanceGate {
    data object Open : MaintenanceGate

    data class Closed(
        val fromBuild: String,
        val targetBuild: String,
        val closedEpochMillis: Long,
    ) : MaintenanceGate
}

internal enum class MaintenanceCloseOutcome {
    CLOSED,
    SESSION_ACTIVE,
}

internal enum class MaintenanceStoreFailure {
    CORRUPTION,
    STORAGE_FAILURE,
}

internal sealed interface MaintenanceStoreResult<out T> {
    data class Success<T>(
        val value: T,
    ) : MaintenanceStoreResult<T>

    data class Failure(
        val reason: MaintenanceStoreFailure,
    ) : MaintenanceStoreResult<Nothing>
}

internal interface UpdateMaintenanceStore {
    suspend fun read(): MaintenanceStoreResult<MaintenanceGate>

    suspend fun close(
        fromBuild: String,
        targetBuild: String,
        nowEpochMillis: Long,
    ): MaintenanceStoreResult<MaintenanceCloseOutcome>

    suspend fun reopen(): MaintenanceStoreResult<Unit>
}

internal class SqlUpdateMaintenanceStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : UpdateMaintenanceStore {
    override suspend fun read(): MaintenanceStoreResult<MaintenanceGate> {
        return databaseCall {
            readGate()
        }
    }

    override suspend fun close(
        fromBuild: String,
        targetBuild: String,
        nowEpochMillis: Long,
    ): MaintenanceStoreResult<MaintenanceCloseOutcome> {
        return databaseCall {
            if (database.hasActiveLocalSession(nowEpochMillis)) {
                MaintenanceCloseOutcome.SESSION_ACTIVE
            } else {
                database.updateMaintenanceQueries.closeGate(fromBuild, targetBuild, nowEpochMillis)
                MaintenanceCloseOutcome.CLOSED
            }
        }
    }

    override suspend fun reopen(): MaintenanceStoreResult<Unit> {
        return databaseCall {
            database.updateMaintenanceQueries.reopenGate()
        }
    }

    private suspend fun readGate(): MaintenanceGate {
        val rows = database.updateMaintenanceQueries.selectGate().awaitAsList()
        if (rows.size > 1) {
            throw MaintenanceStoreException(MaintenanceStoreFailure.CORRUPTION)
        }
        val row = rows.singleOrNull() ?: return MaintenanceGate.Open
        return MaintenanceGate.Closed(row.from_build, row.target_build, row.closed_epoch_millis)
    }

    private suspend fun <T> databaseCall(block: suspend () -> T): MaintenanceStoreResult<T> {
        return withContext(databaseDispatcher) {
            try {
                MaintenanceStoreResult.Success(
                    database.transactionWithResult {
                        block()
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: MaintenanceStoreException) {
                MaintenanceStoreResult.Failure(failure.reason)
            } catch (_: Exception) {
                MaintenanceStoreResult.Failure(MaintenanceStoreFailure.STORAGE_FAILURE)
            }
        }
    }
}

private class MaintenanceStoreException(
    val reason: MaintenanceStoreFailure,
) : Exception()
