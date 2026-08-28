package app.posato.feature.targets.data

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

internal class DatabaseDispatcherProbe : CoroutineDispatcher() {
    private val delegate = Dispatchers.Default.limitedParallelism(1, "PosatoDatabaseProbe")
    private val recordedOperations = mutableListOf<String>()
    private val contextViolations = mutableListOf<String>()
    private var activeDispatches: Int = 0

    val operations: List<String>
        get() = recordedOperations.toList()

    val violations: List<String>
        get() = contextViolations.toList()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        delegate.dispatch(context) {
            activeDispatches += 1
            try {
                block.run()
            } finally {
                activeDispatches -= 1
            }
        }
    }

    fun record(operation: String) {
        recordedOperations += operation
        if (activeDispatches == 0) {
            contextViolations += operation
        }
    }
}

internal class ProbingSqlDriver(
    private val delegate: SqlDriver,
    private val probe: DatabaseDispatcherProbe,
) : SqlDriver by delegate {
    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        probe.record("query")
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        probe.record("execute")
        return delegate.execute(identifier, sql, parameters, binders)
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        probe.record("transaction")
        return delegate.newTransaction()
    }
}
