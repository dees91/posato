package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SqlLocalTargetPolicyStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LocalPolicySyncStore {
    private val writeGate = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun <T> withWriteGate(block: suspend () -> T): T {
        return writeGate.withLock {
            block()
        }
    }

    override val policyChanges: Flow<Unit>
        get() = changes

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return withContext(databaseDispatcher) {
            readResult(database)
        }
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
        syncWrite: PolicySyncWrite?,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return withContext(databaseDispatcher) {
            when {
                expectedRevision < 0 -> {
                    LocalPolicyResult.Failure(LocalPolicyFailure.INVALID_REVISION)
                }

                expectedRevision == Long.MAX_VALUE -> {
                    LocalPolicyResult.Failure(LocalPolicyFailure.REVISION_EXHAUSTED)
                }

                else -> {
                    replaceValidRevision(database, expectedRevision, policy, syncWrite)
                }
            }
        }
    }

    override suspend fun wwwCounterpartExpansionCompleted(): Boolean {
        return withContext(databaseDispatcher) {
            database.localExactDomainPolicyQueries
                .selectWwwCounterpartExpansion()
                .awaitAsList()
                .isNotEmpty()
        }
    }

    override suspend fun markWwwCounterpartExpansionCompleted(): LocalPolicyResult<Unit> {
        return transact(databaseDispatcher, database) {
            localExactDomainPolicyQueries.insertWwwCounterpartExpansion()
        }
    }

    override suspend fun recordIntents(write: PolicySyncWrite): LocalPolicyResult<Unit> {
        return transact(databaseDispatcher, database) {
            insertIntents(write)
        }
    }

    override suspend fun readIntents(): LocalPolicyResult<List<SequencedPolicyIntent>> {
        return transactResult(databaseDispatcher, database) {
            readIntentsOrThrow()
        }
    }

    override suspend fun deleteIntent(sequence: Long): LocalPolicyResult<Unit> {
        return transact(databaseDispatcher, database) {
            syncLocalPolicyQueries.deleteIntent(sequence)
        }
    }

    override suspend fun clearIntents(): LocalPolicyResult<Unit> {
        return transact(databaseDispatcher, database) {
            syncLocalPolicyQueries.deleteIntents()
        }
    }

    override suspend fun readBase(): LocalPolicyResult<PolicySyncBase?> {
        return transactResult(databaseDispatcher, database) {
            readBaseOrThrow()
        }
    }

    override suspend fun replaceWithBase(
        expectedRevision: Long,
        policy: TargetPolicy,
        base: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return withContext(databaseDispatcher) {
            when {
                expectedRevision < 0 -> {
                    LocalPolicyResult.Failure(LocalPolicyFailure.INVALID_REVISION)
                }

                expectedRevision == Long.MAX_VALUE -> {
                    LocalPolicyResult.Failure(LocalPolicyFailure.REVISION_EXHAUSTED)
                }

                else -> {
                    replaceValidRevisionWithBase(database, expectedRevision, policy, base).also { result ->
                        if (result is LocalPolicyResult.Success) {
                            changes.tryEmit(Unit)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun transact(
    dispatcher: CoroutineDispatcher,
    database: PosatoDatabase,
    block: suspend PosatoDatabase.() -> Unit,
): LocalPolicyResult<Unit> {
    return withContext(dispatcher) {
        try {
            database.transactionWithResult {
                database.block()
            }
            LocalPolicyResult.Success(Unit)
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (failure: LocalPolicyStoreException) {
            LocalPolicyResult.Failure(failure.reason)
        } catch (_: Exception) {
            LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
        }
    }
}

private suspend fun <T> transactResult(
    dispatcher: CoroutineDispatcher,
    database: PosatoDatabase,
    block: suspend PosatoDatabase.() -> T,
): LocalPolicyResult<T> {
    return withContext(dispatcher) {
        try {
            LocalPolicyResult.Success(
                database.transactionWithResult {
                    database.block()
                },
            )
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (failure: LocalPolicyStoreException) {
            LocalPolicyResult.Failure(failure.reason)
        } catch (_: Exception) {
            LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
        }
    }
}

private suspend fun readResult(database: PosatoDatabase): LocalPolicyResult<LocalTargetPolicyState> {
    return try {
        val state = database.transactionWithResult {
            database.readStateOrThrow()
        }

        LocalPolicyResult.Success(state)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (failure: LocalPolicyStoreException) {
        LocalPolicyResult.Failure(failure.reason)
    } catch (_: Exception) {
        LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
    }
}

private suspend fun replaceValidRevision(
    database: PosatoDatabase,
    expectedRevision: Long,
    policy: TargetPolicy,
    syncWrite: PolicySyncWrite?,
): LocalPolicyResult<LocalTargetPolicyState> {
    return try {
        val state = database.transactionWithResult {
            database.advanceRevisionOrThrow(expectedRevision)
            database.writePolicyRows(policy)
            if (syncWrite != null) {
                database.insertIntents(syncWrite)
            }
            database.readStateOrThrow()
        }
        LocalPolicyResult.Success(state)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (failure: LocalPolicyStoreException) {
        LocalPolicyResult.Failure(failure.reason)
    } catch (_: Exception) {
        LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
    }
}

private suspend fun replaceValidRevisionWithBase(
    database: PosatoDatabase,
    expectedRevision: Long,
    policy: TargetPolicy,
    base: TargetPolicy,
): LocalPolicyResult<LocalTargetPolicyState> {
    return try {
        val state = database.transactionWithResult {
            database.advanceRevisionOrThrow(expectedRevision)
            database.writePolicyRows(policy)
            database.writeBaseRows(base)
            database.readStateOrThrow()
        }
        LocalPolicyResult.Success(state)
    } catch (expectedCancellation: CancellationException) {
        throw expectedCancellation
    } catch (failure: LocalPolicyStoreException) {
        LocalPolicyResult.Failure(failure.reason)
    } catch (_: Exception) {
        LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
    }
}
