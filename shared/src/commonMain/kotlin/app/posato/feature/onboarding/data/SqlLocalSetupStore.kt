package app.posato.feature.onboarding.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal enum class SetupCompletion {
    UNKNOWN,
    INCOMPLETE,
    COMPLETE
}

internal sealed interface LocalSetupResult<out T> {
    data class Success<T>(
        val value: T
    ) : LocalSetupResult<T>

    data class Failure(
        val reason: LocalSetupFailure
    ) : LocalSetupResult<Nothing>
}

internal enum class LocalSetupFailure {
    CORRUPTION,
    STORAGE_FAILURE
}

internal interface LocalSetupStore {
    suspend fun read(): LocalSetupResult<SetupCompletion>

    suspend fun markComplete(): LocalSetupResult<Unit>
}

internal class SqlLocalSetupStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher
) : LocalSetupStore {
    override suspend fun read(): LocalSetupResult<SetupCompletion> {
        return databaseCall {
            val rows = database.localSetupQueries.selectSetupState().awaitAsList()
            if (rows.size > 1) {
                failLocalSetupStore(LocalSetupFailure.CORRUPTION)
            }
            val singleton = rows.singleOrNull() ?: return@databaseCall SetupCompletion.INCOMPLETE
            if (singleton != 1L) {
                failLocalSetupStore(LocalSetupFailure.CORRUPTION)
            }
            SetupCompletion.COMPLETE
        }
    }

    override suspend fun markComplete(): LocalSetupResult<Unit> {
        return databaseCall {
            database.localSetupQueries.markSetupComplete()
        }
    }

    private suspend fun <T> databaseCall(block: suspend () -> T): LocalSetupResult<T> {
        return withContext(databaseDispatcher) {
            try {
                LocalSetupResult.Success(
                    database.transactionWithResult {
                        block()
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: LocalSetupStoreException) {
                LocalSetupResult.Failure(failure.reason)
            } catch (_: Exception) {
                LocalSetupResult.Failure(LocalSetupFailure.STORAGE_FAILURE)
            }
        }
    }
}

private class LocalSetupStoreException(
    val reason: LocalSetupFailure
) : Exception()

private fun failLocalSetupStore(reason: LocalSetupFailure): Nothing {
    throw LocalSetupStoreException(reason)
}
