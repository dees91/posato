package app.posato.persistence

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.posato.persistence.db.PosatoDatabase
import app.posato.policy.ExactDomainPolicy
import app.posato.policy.ExactDomainPolicyLimits
import app.posato.policy.ExactDomainPolicyValidationResult
import kotlinx.coroutines.CancellationException

internal fun interface LocalPolicyDriverFactory {
    fun open(): OpenedLocalPolicyDriver
}

internal class OpenedLocalPolicyDriver(
    val driver: SqlDriver,
    val existedBeforeOpen: Boolean,
)

internal class SqlLocalExactDomainPolicyStore private constructor(
    private val driver: SqlDriver,
    private val database: PosatoDatabase,
    private val databaseDispatcher: DatabaseDispatcher,
) : LocalExactDomainPolicyStore {
    private var closed: Boolean = false

    override suspend fun read(): LocalPolicyResult<LocalExactDomainPolicyState> {
        return databaseDispatcher.execute {
            if (closed) {
                return@execute LocalPolicyResult.Failure(LocalPolicyFailure.CLOSED)
            }
            try {
                LocalPolicyResult.Success(readStateOrThrow())
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (failure: LocalPolicyStoreException) {
                LocalPolicyResult.Failure(failure.reason)
            } catch (expectedReadFailure: Exception) {
                LocalPolicyResult.Failure(LocalPolicyFailure.CORRUPTION)
            }
        }
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: ExactDomainPolicy,
    ): LocalPolicyResult<LocalExactDomainPolicyState> {
        return databaseDispatcher.execute {
            when {
                closed -> LocalPolicyResult.Failure(LocalPolicyFailure.CLOSED)
                expectedRevision < 0 -> LocalPolicyResult.Failure(LocalPolicyFailure.INVALID_REVISION)
                expectedRevision == Long.MAX_VALUE -> LocalPolicyResult.Failure(LocalPolicyFailure.REVISION_EXHAUSTED)
                else -> replaceOpenStore(expectedRevision, policy)
            }
        }
    }

    private suspend fun replaceOpenStore(
        expectedRevision: Long,
        policy: ExactDomainPolicy,
    ): LocalPolicyResult<LocalExactDomainPolicyState> {
        return try {
            val state =
                database.transactionWithResult {
                    val changed =
                        database.localExactDomainPolicyQueries.advanceRevision(
                            next_revision = expectedRevision + 1,
                            expected_revision = expectedRevision,
                        )
                    if (changed != 1L) {
                        throw LocalPolicyStoreException(LocalPolicyFailure.REVISION_CONFLICT)
                    }
                    database.localExactDomainPolicyQueries.deleteDomains()
                    policy.domains.forEach { domain ->
                        database.localExactDomainPolicyQueries.insertDomain(domain.canonicalValue)
                    }
                    readStateOrThrow()
                }
            LocalPolicyResult.Success(state)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (failure: LocalPolicyStoreException) {
            LocalPolicyResult.Failure(failure.reason)
        } catch (expectedWriteFailure: Exception) {
            LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
        }
    }

    override suspend fun close() {
        databaseDispatcher.executeNonCancellable {
            if (!closed) {
                closed = true
                driver.close()
            }
        }
    }

    private suspend fun readStateOrThrow(): LocalExactDomainPolicyState {
        val revision = database.localExactDomainPolicyQueries.selectRevision().awaitAsOne()
        if (revision < 0) {
            fail(LocalPolicyFailure.CORRUPTION)
        }
        val canonicalDomains =
            database.localExactDomainPolicyQueries
                .selectDomains(ExactDomainPolicyLimits.MAX_DOMAIN_COUNT.toLong() + 1)
                .awaitAsList()
        if (canonicalDomains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
            fail(LocalPolicyFailure.CORRUPTION)
        }
        val policy =
            when (val validation = ExactDomainPolicy.fromCanonicalValues(canonicalDomains)) {
                is ExactDomainPolicyValidationResult.Success -> {
                    validation.policy
                }

                is ExactDomainPolicyValidationResult.Failure -> {
                    fail(LocalPolicyFailure.CORRUPTION)
                }
            }
        return LocalExactDomainPolicyState(revision, policy)
    }

    companion object {
        private const val CURRENT_SCHEMA_VERSION: Long = 1

        suspend fun open(
            factory: LocalPolicyDriverFactory,
            databaseDispatcher: DatabaseDispatcher,
        ): LocalPolicyResult<LocalExactDomainPolicyStore> {
            val owner = OpenedDriverOwner()
            return try {
                val result =
                    databaseDispatcher.execute {
                        openOnDatabaseContext(factory, databaseDispatcher, owner)
                    }
                owner.release()
                result
            } catch (expectedCancellation: CancellationException) {
                closeOpenedAfterCancellation(owner.opened, expectedCancellation, databaseDispatcher)
                throw expectedCancellation
            }
        }

        private suspend fun openOnDatabaseContext(
            factory: LocalPolicyDriverFactory,
            databaseDispatcher: DatabaseDispatcher,
            owner: OpenedDriverOwner,
        ): LocalPolicyResult<LocalExactDomainPolicyStore> {
            return try {
                val store = createValidatedStore(factory, databaseDispatcher, owner)
                when (val state = store.read()) {
                    is LocalPolicyResult.Success -> {
                        databaseDispatcher.record(DatabaseContextOperation.STORE_OPENED_FOR_HANDOFF)
                        LocalPolicyResult.Success(store)
                    }

                    is LocalPolicyResult.Failure -> {
                        closeOwnedStoreAfterFailure(owner, state.reason, store)
                    }
                }
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (failure: LocalPolicyStoreException) {
                closeOwnedDriverAfterFailure(owner, failure.reason, databaseDispatcher)
            } catch (expectedOpenFailure: Exception) {
                closeOwnedDriverAfterFailure(owner, LocalPolicyFailure.STORAGE_FAILURE, databaseDispatcher)
            }
        }

        private suspend fun createValidatedStore(
            factory: LocalPolicyDriverFactory,
            databaseDispatcher: DatabaseDispatcher,
            owner: OpenedDriverOwner,
        ): SqlLocalExactDomainPolicyStore {
            databaseDispatcher.record(DatabaseContextOperation.DRIVER_OPEN)
            val opened = owner.acquire(factory.open(), databaseDispatcher)
            if (!opened.existedBeforeOpen) {
                PosatoDatabase.Schema.create(opened.driver).await()
            }
            validateOpenedStorage(opened)
            val database = PosatoDatabase(opened.driver)
            val schemaVersion = readSchemaVersion(database, opened)
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                fail(LocalPolicyFailure.UNSUPPORTED_SCHEMA)
            }
            return SqlLocalExactDomainPolicyStore(
                driver = opened.driver,
                database = database,
                databaseDispatcher = databaseDispatcher,
            )
        }

        private suspend fun readSchemaVersion(
            database: PosatoDatabase,
            opened: OpenedLocalPolicyDriver,
        ): Long {
            return try {
                database.localExactDomainPolicyQueries.selectSchemaVersion().awaitAsOne()
            } catch (expectedCancellation: CancellationException) {
                throw expectedCancellation
            } catch (expectedSchemaFailure: Exception) {
                fail(opened.invalidStorageFailure())
            }
        }

        private suspend fun validateOpenedStorage(opened: OpenedLocalPolicyDriver) {
            val integrityValid =
                try {
                    quickCheck(opened.driver)
                } catch (expectedCancellation: CancellationException) {
                    throw expectedCancellation
                } catch (expectedIntegrityFailure: Exception) {
                    fail(opened.invalidStorageFailure())
                }
            if (!integrityValid) {
                fail(opened.invalidStorageFailure())
            }
            if (opened.existedBeforeOpen) {
                val schemaPresent =
                    try {
                        hasPolicySchema(opened.driver)
                    } catch (expectedCancellation: CancellationException) {
                        throw expectedCancellation
                    } catch (expectedSchemaLookupFailure: Exception) {
                        fail(LocalPolicyFailure.CORRUPTION)
                    }
                if (!schemaPresent) {
                    fail(LocalPolicyFailure.UNSUPPORTED_SCHEMA)
                }
            }
        }

        private suspend fun quickCheck(driver: SqlDriver): Boolean {
            return driver
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA quick_check",
                    mapper = { cursor ->
                        var rowCount = 0
                        var valid = true
                        while (cursor.next().value) {
                            rowCount += 1
                            valid = valid && cursor.getString(0) == "ok"
                        }
                        QueryResult.Value(valid && rowCount == 1)
                    },
                    parameters = 0,
                ).await()
        }

        private suspend fun hasPolicySchema(driver: SqlDriver): Boolean {
            return driver
                .executeQuery(
                    identifier = null,
                    sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'policy_schema'",
                    mapper = { cursor ->
                        check(cursor.next().value)
                        QueryResult.Value(cursor.getLong(0) == 1L)
                    },
                    parameters = 0,
                ).await()
        }
    }
}

internal class LocalPolicyStoreException(
    val reason: LocalPolicyFailure,
) : IllegalStateException(reason.name)

private fun fail(reason: LocalPolicyFailure): Nothing {
    throw LocalPolicyStoreException(reason)
}

private fun OpenedLocalPolicyDriver.invalidStorageFailure(): LocalPolicyFailure {
    return if (existedBeforeOpen) {
        LocalPolicyFailure.CORRUPTION
    } else {
        LocalPolicyFailure.STORAGE_FAILURE
    }
}

private class OpenedDriverOwner {
    var opened: OpenedLocalPolicyDriver? = null
        private set

    fun acquire(
        rawOpened: OpenedLocalPolicyDriver,
        databaseDispatcher: DatabaseDispatcher,
    ): OpenedLocalPolicyDriver {
        val acquired =
            OpenedLocalPolicyDriver(
                driver = DispatchingSqlDriver(rawOpened.driver, databaseDispatcher),
                existedBeforeOpen = rawOpened.existedBeforeOpen,
            )
        opened = acquired
        return acquired
    }

    fun release() {
        opened = null
    }
}

private suspend fun closeOwnedStoreAfterFailure(
    owner: OpenedDriverOwner,
    reason: LocalPolicyFailure,
    store: SqlLocalExactDomainPolicyStore,
): LocalPolicyResult.Failure {
    val result = closeAfterFailure(reason, store::close)
    owner.release()
    return result
}

private suspend fun closeOwnedDriverAfterFailure(
    owner: OpenedDriverOwner,
    reason: LocalPolicyFailure,
    databaseDispatcher: DatabaseDispatcher,
): LocalPolicyResult.Failure {
    val result = closeOpenedAfterFailure(owner.opened, reason, databaseDispatcher)
    owner.release()
    return result
}

private suspend fun closeOpenedAfterFailure(
    opened: OpenedLocalPolicyDriver?,
    reason: LocalPolicyFailure,
    databaseDispatcher: DatabaseDispatcher,
): LocalPolicyResult.Failure {
    return if (opened == null) {
        LocalPolicyResult.Failure(reason)
    } else {
        closeAfterFailure(reason) {
            databaseDispatcher.executeNonCancellable {
                opened.driver.close()
            }
        }
    }
}

private suspend fun closeOpenedAfterCancellation(
    opened: OpenedLocalPolicyDriver?,
    cancellation: CancellationException,
    databaseDispatcher: DatabaseDispatcher,
) {
    if (opened != null) {
        try {
            databaseDispatcher.executeNonCancellable {
                opened.driver.close()
            }
        } catch (closeCancellation: CancellationException) {
            cancellation.addSuppressed(closeCancellation)
        } catch (expectedCloseFailure: Exception) {
            cancellation.addSuppressed(expectedCloseFailure)
        }
    }
}

private suspend fun closeAfterFailure(
    reason: LocalPolicyFailure,
    close: suspend () -> Unit,
): LocalPolicyResult.Failure {
    return try {
        close()
        LocalPolicyResult.Failure(reason)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (expectedCloseFailure: Exception) {
        LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
    }
}
