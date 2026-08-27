package app.posato.persistence

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.persistence.db.PosatoDatabase
import app.posato.policy.ExactDomainPolicy
import app.posato.policy.ExactDomainPolicyLimits
import app.posato.policy.ExactDomainPolicyValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SqlLocalExactDomainPolicyStore(
    private val database: PosatoDatabase,
    private val databaseDispatcher: CoroutineDispatcher,
) : LocalExactDomainPolicyStore {
    override suspend fun read(): LocalPolicyResult<LocalExactDomainPolicyState> {
        return withContext(databaseDispatcher) {
            readResult()
        }
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: ExactDomainPolicy,
    ): LocalPolicyResult<LocalExactDomainPolicyState> {
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

    private suspend fun readResult(): LocalPolicyResult<LocalExactDomainPolicyState> {
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
        policy: ExactDomainPolicy,
    ): LocalPolicyResult<LocalExactDomainPolicyState> {
        return try {
            val state = database.transactionWithResult {
                val changed = database.localExactDomainPolicyQueries.advanceRevision(
                    next_revision = expectedRevision + 1,
                    expected_revision = expectedRevision,
                )
                if (changed != 1L) {
                    fail(LocalPolicyFailure.REVISION_CONFLICT)
                }
                readStateOrThrow()
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
        } catch (_: Exception) {
            LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE)
        }
    }

    private suspend fun readStateOrThrow(): LocalExactDomainPolicyState {
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
        val policy = when (val validation = ExactDomainPolicy.fromCanonicalValues(canonicalDomains)) {
            is ExactDomainPolicyValidationResult.Success -> {
                validation.policy
            }

            is ExactDomainPolicyValidationResult.Failure -> {
                fail(LocalPolicyFailure.CORRUPTION)
            }
        }

        return LocalExactDomainPolicyState(revisions.single(), policy)
    }
}

private class LocalPolicyStoreException(
    val reason: LocalPolicyFailure,
) : Exception()

private fun fail(reason: LocalPolicyFailure): Nothing {
    throw LocalPolicyStoreException(reason)
}
