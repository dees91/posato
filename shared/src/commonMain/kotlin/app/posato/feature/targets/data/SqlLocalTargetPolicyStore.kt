package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlLocalTargetPolicyStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LocalTargetPolicyStore {
    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return withContext(databaseDispatcher) {
            readResult()
        }
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
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
                    replaceValidRevision(expectedRevision, policy)
                }
            }
        }
    }

    private suspend fun readResult(): LocalPolicyResult<LocalTargetPolicyState> {
        return try {
            val state = database.transactionWithResult {
                readStateOrThrow()
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
        expectedRevision: Long,
        policy: TargetPolicy,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return try {
            val state = database.transactionWithResult {
                val changed = database.localExactDomainPolicyQueries.advanceRevision(
                    next_revision = expectedRevision + 1,
                    expected_revision = expectedRevision,
                )
                readStateOrThrow()
                if (changed != 1L) {
                    fail(LocalPolicyFailure.REVISION_CONFLICT)
                }
                database.localExactDomainPolicyQueries.deleteDomains()
                policy.domains.forEach { domain ->
                    database.localExactDomainPolicyQueries.insertDomain(domain.canonicalValue)
                }
                database.localExactDomainPolicyQueries.deleteApplicationPolicy()
                policy.applicationPolicyName?.let { name ->
                    database.localExactDomainPolicyQueries.insertApplicationPolicy(name.canonicalValue)
                }
                readStateOrThrow()
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

    private suspend fun readStateOrThrow(): LocalTargetPolicyState {
        val revisions = database.localExactDomainPolicyQueries
            .selectRevision()
            .awaitAsList()
        if (revisions.size != 1 || revisions.single() < 0) {
            fail(LocalPolicyFailure.CORRUPTION)
        }
        val canonicalDomains = database.localExactDomainPolicyQueries
            .selectDomains(ExactDomainPolicyLimits.MAX_DOMAIN_COUNT.toLong() + 1)
            .awaitAsList()
        if (canonicalDomains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
            fail(LocalPolicyFailure.CORRUPTION)
        }
        val applicationPolicyNames = database.localExactDomainPolicyQueries
            .selectApplicationPolicyNames()
            .awaitAsList()
        if (applicationPolicyNames.size > 1) {
            fail(LocalPolicyFailure.CORRUPTION)
        }
        val policy = when (
            val validation = TargetPolicy.fromStoredValues(
                canonicalDomains = canonicalDomains,
                applicationPolicyName = applicationPolicyNames.singleOrNull(),
            )
        ) {
            is TargetPolicyValidationResult.Success -> {
                validation.policy
            }

            is TargetPolicyValidationResult.Failure -> {
                fail(LocalPolicyFailure.CORRUPTION)
            }
        }

        return LocalTargetPolicyState(revisions.single(), policy)
    }
}

private class LocalPolicyStoreException(
    val reason: LocalPolicyFailure,
) : Exception()

private fun fail(reason: LocalPolicyFailure): Nothing {
    throw LocalPolicyStoreException(reason)
}
