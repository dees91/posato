package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.testContext
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PolicyReconcilerSeedTest {
    @Test
    fun `given a save during first-link seeding when the seed lands then no stale present follows the removal`() = runTest {
        val database = createLocalPolicyTestDatabase("review-seed-race.db")
        val driver = database.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val domain = checkNotNull(ExactDomain.restore("seed-race.example"))
            val workspaceId = testContext.workspaceId.value.copyBytes()
            assertIs<LocalPolicyResult.Success<*>>(store.replace(0, testPolicy("seed-race.example")))
            val entered = CompletableDeferred<Unit>()
            val stalled = object : LocalPolicySyncStore by store {
                override suspend fun recordIntents(write: PolicySyncWrite): LocalPolicyResult<Unit> {
                    entered.complete(Unit)
                    delay(50)
                    return store.recordIntents(write)
                }
            }
            val seed = backgroundScope.launch { PolicyReconciler(stalled).seedLocalExtras(workspaceId) }
            entered.await()
            val removal = async {
                store.withWriteGate {
                    store.replace(1, testPolicy(), PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.RemoveDomain(domain))))
                }
            }
            advanceTimeBy(100)
            removal.await()
            seed.join()
            val rows = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(store.readIntents()).value.map { row -> row.intent }
            assertEquals(
                listOf(StoredPolicyIntent.PresentDomain(domain), StoredPolicyIntent.RemoveDomain(domain)),
                rows,
            )
            val local = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value.policy
            assertEquals(emptyList(), local.domains)
        } finally {
            driver.close()
            database.delete()
        }
    }
}
