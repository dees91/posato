package app.posato.feature.sync.bootstrap

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.SyncAuditEntry
import app.posato.feature.sync.domain.SyncAuditOutcome
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PolicyReconcilerCapacityTest {
    @Test
    fun `given a projection over the local cap when applying to an empty policy then the refusal keeps policy and base`() = runTest {
        val database = createLocalPolicyTestDatabase("review-cap-projection.db")
        val driver = database.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val projection = SyncProjection(
                (1..1025).map { checkNotNull(ExactDomain.restore("site$it.example")) },
                null,
                emptyList(),
                emptySet(),
                emptyList(),
            )
            assertEquals(ReconcileOutcome.RefusedLocalCap, PolicyReconciler(store).apply(projection, null))
            assertEquals(emptyList(), localDomains(store))
            assertNull(localBase(store))
            assertEquals(0, localRevision(store))
        } finally {
            driver.close()
            database.delete()
        }
    }

    @Test
    fun `given a merged union over the local cap when applying then the refusal keeps policy and base`() = runTest {
        val database = createLocalPolicyTestDatabase("review-cap-merge.db")
        val driver = database.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val local = assertIs<TargetPolicyValidationResult.Success>(
                TargetPolicy.fromStoredValues(listOf("local.example"), null),
            ).policy
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(0, local))
            val projection = SyncProjection(
                (1..1024).map { checkNotNull(ExactDomain.restore("site$it.example")) },
                null,
                emptyList(),
                emptySet(),
                emptyList(),
            )
            assertEquals(ReconcileOutcome.RefusedLocalCap, PolicyReconciler(store).apply(projection, null))
            assertEquals(listOf("local.example"), localDomains(store))
            assertNull(localBase(store))
            assertEquals(1, localRevision(store))
        } finally {
            driver.close()
            database.delete()
        }
    }

    @Test
    fun `given a workspace-full projection when applying then the shared refusal keeps policy and base`() = runTest {
        val database = createLocalPolicyTestDatabase("review-cap-shared.db")
        val driver = database.openDriver()
        try {
            seedReplicaState(driver)
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val converged = assertIs<TargetPolicyValidationResult.Success>(
                TargetPolicy.fromStoredValues((1..1024).map { "site$it.example" }, null),
            ).policy
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceWithBase(0, converged, converged))
            val projection = SyncProjection(
                (1..2048).map { checkNotNull(ExactDomain.restore("site$it.example")) },
                null,
                emptyList(),
                emptySet(),
                listOf(SyncAuditEntry(BundleId(testIdentifier(7)), SyncAuditOutcome.DOMAIN_CAPACITY)),
            )
            assertEquals(
                ReconcileOutcome.RefusedWorkspaceFull,
                PolicyReconciler(store).apply(projection, PolicySyncBase(converged)),
            )
            assertEquals(1024, localDomains(store).size)
            assertEquals(1024, assertIs<PolicySyncBase>(localBase(store)).policy.domains.size)
            assertEquals(1, localRevision(store))
        } finally {
            driver.close()
            database.delete()
        }
    }

    @Test
    fun `given a refused apply when the set drops under the cap then the next apply converges`() = runTest {
        val database = createLocalPolicyTestDatabase("review-cap-recovery.db")
        val driver = database.openDriver()
        try {
            seedReplicaState(driver)
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
            val local = assertIs<TargetPolicyValidationResult.Success>(
                TargetPolicy.fromStoredValues(listOf("local.example"), null),
            ).policy
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(0, local))
            val over = SyncProjection(
                (1..1024).map { checkNotNull(ExactDomain.restore("site$it.example")) },
                null,
                emptyList(),
                emptySet(),
                emptyList(),
            )
            assertEquals(ReconcileOutcome.RefusedLocalCap, PolicyReconciler(store).apply(over, null))
            val under = SyncProjection(
                listOf(checkNotNull(ExactDomain.restore("local.example"))) +
                    (1..9).map { checkNotNull(ExactDomain.restore("site$it.example")) },
                null,
                emptyList(),
                emptySet(),
                emptyList(),
            )
            assertEquals(ReconcileOutcome.AppliedClean, PolicyReconciler(store).apply(under, null))
            assertEquals(10, localDomains(store).size)
            assertEquals(10, assertIs<PolicySyncBase>(localBase(store)).policy.domains.size)
            assertEquals(2, localRevision(store))
        } finally {
            driver.close()
            database.delete()
        }
    }

    private suspend fun localDomains(store: SqlLocalTargetPolicyStore): List<String> {
        return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value.policy.domains.map { it.canonicalValue }
    }

    private suspend fun localBase(store: SqlLocalTargetPolicyStore): PolicySyncBase? {
        return assertIs<LocalPolicyResult.Success<PolicySyncBase?>>(store.readBase()).value
    }

    private suspend fun localRevision(store: SqlLocalTargetPolicyStore): Long {
        return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value.revision
    }

    private fun seedReplicaState(driver: SqlDriver) {
        driver.execute(
            identifier = null,
            sql = "INSERT INTO sync_replica_state(singleton, workspace_id, transport_epoch_id, key_epoch_id, " +
                "revision, hlc_physical, hlc_logical, hlc_exhausted, transport_progress) VALUES " +
                "(1, X'000102030405060708090A0B0C0D0E0F', X'000102030405060708090A0B0C0D0E0F', " +
                "X'000102030405060708090A0B0C0D0E0F', 0, 0, 0, 0, NULL)",
            parameters = 0,
        ).value
    }
}
