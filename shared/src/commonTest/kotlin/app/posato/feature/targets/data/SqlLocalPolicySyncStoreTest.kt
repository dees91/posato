package app.posato.feature.targets.data

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ApplicationPolicyNameResult
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlLocalPolicySyncStoreTest {
    @Test
    fun `given a save with intents when replacing then policy and ordered rows commit together`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-atomic.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val added = checkNotNull(ExactDomain.restore("added.example"))
            val removed = checkNotNull(ExactDomain.restore("removed.example"))
            val policy = testPolicy("removed.example")
            val write = PolicySyncWrite(
                workspaceId = testWorkspaceId(),
                intents = listOf(
                    StoredPolicyIntent.RemoveDomain(removed),
                    StoredPolicyIntent.PresentDomain(added),
                    StoredPolicyIntent.PresentApplicationPolicy(testGroupName()),
                ),
            )
            val replaced = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(0, policy, write))
            assertEquals(testPolicy("removed.example"), replaced.value.policy)
            val rows = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(
                store.readIntents(),
            ).value
            assertEquals(write.intents, rows.map { row -> row.intent })
            assertTrue(rows.zipWithNext { first, second -> first.sequence < second.sequence }.all { ordered -> ordered })
            assertTrue(rows.all { row -> row.workspaceId.contentEquals(testWorkspaceId()) })
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a save without intents when replacing then no rows are recorded`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-plain.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(0, testPolicy("plain.example")))
            val rows = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(
                store.readIntents(),
            ).value
            assertTrue(rows.isEmpty())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a revision conflict when replacing with intents then neither policy nor rows are written`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-conflict.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(0, testPolicy("kept.example")))
            val domain = checkNotNull(ExactDomain.restore("lost.example"))
            val write = PolicySyncWrite(testWorkspaceId(), listOf(StoredPolicyIntent.PresentDomain(domain)))
            val conflicted = store.replace(7, testPolicy("lost.example"), write)
            assertIs<LocalPolicyResult.Failure>(conflicted)
            assertEquals(LocalPolicyFailure.REVISION_CONFLICT, conflicted.reason)
            val current = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value
            assertEquals(testPolicy("kept.example"), current.policy)
            val rows = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(
                store.readIntents(),
            ).value
            assertTrue(rows.isEmpty())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a non-canonical domain row when reading intents then corruption is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-corrupt-domain.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalTargetPolicyStore(database, testDispatcher())
            driver.executeSql(
                "INSERT INTO sync_policy_intent(workspace_id, kind, canonical_domain, canonical_name) " +
                    "VALUES (X'000102030405060708090A0B0C0D0E0F', 'domain_present', 'a..b', NULL)",
            )
            val result = store.readIntents()
            assertIs<LocalPolicyResult.Failure>(result)
            assertEquals(LocalPolicyFailure.CORRUPTION, result.reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a non-canonical name row when reading intents then corruption is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-corrupt-name.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalTargetPolicyStore(database, testDispatcher())
            driver.executeSql(
                "INSERT INTO sync_policy_intent(workspace_id, kind, canonical_domain, canonical_name) " +
                    "VALUES (X'000102030405060708090A0B0C0D0E0F', 'application_present', NULL, ' Untrimmed ')",
            )
            val result = store.readIntents()
            assertIs<LocalPolicyResult.Failure>(result)
            assertEquals(LocalPolicyFailure.CORRUPTION, result.reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given no base marker when reading base then absent is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-no-base.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val base = assertIs<LocalPolicyResult.Success<PolicySyncBase?>>(store.readBase()).value
            assertNull(base)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a base write when reading base then the applied projection round-trips`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-base.db")
        val driver = testDatabase.openDriver()
        try {
            seedReplicaState(driver)
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val policy = testPolicy("applied.example", groupName = "Example group")
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceWithBase(0, policy, policy))
            assertEquals(policy, assertIs<LocalPolicyResult.Success<PolicySyncBase?>>(store.readBase()).value?.policy)
            val next = testPolicy("next.example")
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceWithBase(1, next, next))
            assertEquals(next, assertIs<LocalPolicyResult.Success<PolicySyncBase?>>(store.readBase()).value?.policy)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a base without replica state when reading base then corruption is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-base-orphan.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            driver.executeSql("INSERT INTO sync_policy_base(singleton) VALUES (1)")
            driver.executeSql("INSERT INTO sync_policy_base_domain(canonical_domain) VALUES ('orphan.example')")
            val result = store.readBase()
            assertIs<LocalPolicyResult.Failure>(result)
            assertEquals(LocalPolicyFailure.CORRUPTION, result.reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a revision conflict when replacing with base then policy and base stay`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-base-conflict.db")
        val driver = testDatabase.openDriver()
        try {
            seedReplicaState(driver)
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val kept = testPolicy("kept.example")
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replaceWithBase(0, kept, kept))
            val conflicted = store.replaceWithBase(7, testPolicy("lost.example"), testPolicy("lost.example"))
            assertIs<LocalPolicyResult.Failure>(conflicted)
            assertEquals(LocalPolicyFailure.REVISION_CONFLICT, conflicted.reason)
            val current = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.read()).value
            assertEquals(kept, current.policy)
            assertEquals(kept, assertIs<LocalPolicyResult.Success<PolicySyncBase?>>(store.readBase()).value?.policy)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given recorded rows when deleting then single and full clears behave`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-delete.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val first = checkNotNull(ExactDomain.restore("first.example"))
            val second = checkNotNull(ExactDomain.restore("second.example"))
            val write = PolicySyncWrite(
                testWorkspaceId(),
                listOf(StoredPolicyIntent.PresentDomain(first), StoredPolicyIntent.PresentDomain(second)),
            )
            assertIs<LocalPolicyResult.Success<Unit>>(store.recordIntents(write))
            val rows = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(
                store.readIntents(),
            ).value
            assertEquals(2, rows.size)
            assertIs<LocalPolicyResult.Success<Unit>>(store.deleteIntent(rows.first().sequence))
            val remaining = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(
                store.readIntents(),
            ).value
            assertEquals(listOf(StoredPolicyIntent.PresentDomain(second)), remaining.map { row -> row.intent })
            assertIs<LocalPolicyResult.Success<Unit>>(store.clearIntents())
            val cleared = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(
                store.readIntents(),
            ).value
            assertTrue(cleared.isEmpty())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a held write gate when entering again then the second block waits for release`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-gate.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val release = CompletableDeferred<Unit>()
            var secondEntered = false
            val first = launch {
                store.withWriteGate {
                    release.await()
                }
            }
            runCurrent()
            val second = launch {
                store.withWriteGate {
                    secondEntered = true
                }
            }
            runCurrent()
            assertFalse(secondEntered)
            release.complete(Unit)
            first.join()
            second.join()
            assertTrue(secondEntered)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a base replace when committed then the change signal emits once`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-signal.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val received = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                store.policyChanges.collect { received.add(it) }
            }
            runCurrent()
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                store.replaceWithBase(0, testPolicy("signalled.example"), testPolicy("signalled.example")),
            )
            runCurrent()
            assertEquals(1, received.size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a plain replace when committed then the change signal stays silent`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("policy-sync-silent.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalTargetPolicyStore(PosatoDatabase(driver), testDispatcher())
            val received = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                store.policyChanges.collect { received.add(it) }
            }
            runCurrent()
            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(store.replace(0, testPolicy("quiet.example")))
            runCurrent()
            assertTrue(received.isEmpty())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private fun testPolicy(
        vararg domains: String,
        groupName: String? = null,
    ): TargetPolicy {
        return (TargetPolicy.fromStoredValues(domains.toList(), groupName) as TargetPolicyValidationResult.Success).policy
    }

    private fun testGroupName(): ApplicationPolicyName {
        return (
            ApplicationPolicyName.parse("Example group") as ApplicationPolicyNameResult.Success
        ).name
    }

    private fun testWorkspaceId(): ByteArray {
        return ByteArray(16) { index -> index.toByte() }
    }

    private fun testDispatcher(): CoroutineDispatcher {
        return Dispatchers.Unconfined
    }

    private fun seedReplicaState(driver: SqlDriver) {
        driver.executeSql(
            "INSERT INTO sync_replica_state(singleton, workspace_id, transport_epoch_id, key_epoch_id, " +
                "revision, hlc_physical, hlc_logical, hlc_exhausted, transport_progress) VALUES " +
                "(1, X'000102030405060708090A0B0C0D0E0F', X'000102030405060708090A0B0C0D0E0F', " +
                "X'000102030405060708090A0B0C0D0E0F', 0, 0, 0, 0, NULL)",
        )
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(
        identifier = null,
        sql = sql,
        parameters = 0,
    ).value
}
