package app.posato.persistence

import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal enum class DatabaseContextOperation {
    DRIVER_OPEN,
    STORE_OPENED_FOR_HANDOFF,
    TRANSACTION_COMPLETED,
}

internal class DatabaseDispatcher(
    private val dispatcher: CoroutineDispatcher,
    private val operationObserver: ((DatabaseContextOperation) -> Unit)? = null,
) {
    suspend fun <T> execute(block: suspend () -> T): T {
        return withContext(dispatcher) {
            block()
        }
    }

    suspend fun <T> executePreservingCompletedResult(block: suspend () -> T): T {
        var completedResult: CompletedResult<T>? = null
        return try {
            withContext(dispatcher) {
                block().also { result -> completedResult = CompletedResult(result) }
            }
        } catch (expectedCancellation: CancellationException) {
            val result = completedResult ?: throw expectedCancellation
            result.value
        }
    }

    suspend fun <T> executeNonCancellable(block: suspend () -> T): T {
        return withContext(dispatcher + NonCancellable) {
            block()
        }
    }

    fun record(operation: DatabaseContextOperation) {
        operationObserver?.invoke(operation)
    }
}

internal class DispatchingSqlDriver(
    private val delegate: SqlDriver,
    private val databaseDispatcher: DatabaseDispatcher,
) : SqlDriver by delegate,
    SuspendingTransacter.TransactionDispatcher {
    override suspend fun <R> dispatch(transaction: suspend () -> R): R {
        return databaseDispatcher.executePreservingCompletedResult {
            try {
                transaction()
            } finally {
                databaseDispatcher.record(DatabaseContextOperation.TRANSACTION_COMPLETED)
            }
        }
    }
}

private class CompletedResult<T>(
    val value: T,
)
