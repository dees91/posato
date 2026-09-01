package app.posato.feature.sync.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.sync.testTransportProgress
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqlSyncReplicaStoreContractTest {
    @Test
    fun `given an out of range persisted physical clock when read then corruption is reported`() = runTest {
        listOf(-1L, SyncFormatLimits.MAX_PHYSICAL_MILLIS + 1).forEachIndexed { index, physical ->
            val testDatabase = createLocalPolicyTestDatabase("sync-invalid-physical-$index.db")
            val driver = testDatabase.openDriver()
            try {
                val database = PosatoDatabase(driver)
                val store = SqlSyncReplicaStore(database, Dispatchers.Default)
                assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext))
                database.transaction {
                    driver.execute(null, "PRAGMA ignore_check_constraints = ON", 0)
                    driver.execute(
                        identifier = null,
                        sql = "UPDATE sync_replica_state SET hlc_physical = $physical WHERE singleton = 1",
                        parameters = 0,
                    )
                    driver.execute(null, "PRAGMA ignore_check_constraints = OFF", 0)
                }

                val result = store.read(testContext)

                assertEquals(SyncStoreFailure.CORRUPTION, assertIs<SyncStoreResult.Failure>(result).reason)
            } finally {
                driver.close()
                testDatabase.delete()
            }
        }
    }

    @Test
    fun `given bounded and oversized transport progress when wrapped then only the bounded bytes are retained`() {
        val source = ByteArray(SyncFormatLimits.MAX_TRANSPORT_PROGRESS_BYTES)
        val progress = assertNotNull(OpaqueTransportProgress.fromBytes(source))

        source[0] = 1

        assertEquals(0, progress.copyBytes()[0])
        assertNull(
            OpaqueTransportProgress.fromBytes(
                ByteArray(SyncFormatLimits.MAX_TRANSPORT_PROGRESS_BYTES + 1),
            ),
        )
    }

    @Test
    fun `given bounded progress when committed then an oversized direct write is rejected and bounded state remains`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-progress-limit.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlSyncReplicaStore(PosatoDatabase(driver), Dispatchers.Default)
            val initial = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext)).value
            val progress = assertNotNull(
                OpaqueTransportProgress.fromBytes(ByteArray(SyncFormatLimits.MAX_TRANSPORT_PROGRESS_BYTES)),
            )
            val committed = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                store.commitTransportProgress(initial.revision, progress),
            ).value

            assertFails {
                driver.execute(
                    identifier = null,
                    sql = "UPDATE sync_replica_state SET transport_progress = zeroblob(65537) WHERE singleton = 1",
                    parameters = 0,
                )
            }

            assertEquals(committed, assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.read(testContext)).value)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given oversized persisted transport progress when read then corruption is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-progress-corruption.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlSyncReplicaStore(database, Dispatchers.Default)
            assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext))
            database.transaction {
                driver.execute(null, "PRAGMA ignore_check_constraints = ON", 0)
                driver.execute(
                    identifier = null,
                    sql = "UPDATE sync_replica_state SET transport_progress = zeroblob(65537) WHERE singleton = 1",
                    parameters = 0,
                )
                driver.execute(null, "PRAGMA ignore_check_constraints = OFF", 0)
            }

            val result = store.read(testContext)

            assertEquals(SyncStoreFailure.CORRUPTION, assertIs<SyncStoreResult.Failure>(result).reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given local state when committed and reopened then exact accepted pending clock and expiry facts persist`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-lifecycle.db")
        var driver = testDatabase.openDriver()
        try {
            var store = SqlSyncReplicaStore(PosatoDatabase(driver), Dispatchers.Default)
            val initial = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext)).value
            val sessionId = SessionId(testIdentifier(90))
            val registration = prepared(1, 1, SyncOperationPayload.AuthorRegister)
            val session = prepared(2, 2, SyncOperationPayload.SessionStart(sessionId, 100, 200))
            val committed = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                store.commitLocal(
                    initial.revision,
                    listOf(registration, session),
                    DurableClockState(HybridLogicalClock(100, 1), false),
                ),
            ).value
            val staged = prepared(3, 2, SyncOperationPayload.ApplicationPolicyAbsent)
            val progress = testTransportProgress(7, 8, 9)
            val withStaged = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                store.commitStagedRemote(committed.revision, staged, committed.clockState, progress),
            ).value
            assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.markTerminalExpiry(withStaged.revision, sessionId))

            driver.close()
            driver = testDatabase.openDriver()
            store = SqlSyncReplicaStore(PosatoDatabase(driver), Dispatchers.Default)
            val reopened = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext)).value

            assertEquals(2, reopened.acceptedBundles.size)
            assertEquals(2, reopened.pendingBundles.size)
            assertEquals(setOf(staged.operation.operationId), reopened.stagedBundles.keys)
            assertEquals(progress, reopened.transportProgress)
            assertEquals(setOf(sessionId), reopened.terminalExpiryFacts)
            assertEquals(DurableClockState(HybridLogicalClock(100, 1), false), reopened.clockState)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a state row with an invalid singleton key when reopened then corruption is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-invalid-singleton.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlSyncReplicaStore(database, Dispatchers.Default)
            assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext))
            database.transaction {
                driver.execute(null, "PRAGMA ignore_check_constraints = ON", 0)
                driver.execute(null, "UPDATE sync_replica_state SET singleton = 2 WHERE singleton = 1", 0)
                driver.execute(null, "PRAGMA ignore_check_constraints = OFF", 0)
            }

            val result = store.open(testContext)

            assertEquals(SyncStoreFailure.CORRUPTION, assertIs<SyncStoreResult.Failure>(result).reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a missing state row with retained replica records when reopened then corruption is reported`() = runTest {
        data class ResidueCase(
            val name: String,
            val persist: suspend (SqlSyncReplicaStore, SyncReplicaSnapshot) -> Unit,
        )

        val cases = listOf(
            ResidueCase("accepted") { store, initial ->
                val bundle = prepared(1, 1, SyncOperationPayload.AuthorRegister)
                assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                    store.commitAcceptedRemote(
                        initial.revision,
                        listOf(bundle),
                        emptySet(),
                        DurableClockState(bundle.operation.clock, false),
                        null,
                    ),
                )
            },
            ResidueCase("staged") { store, initial ->
                assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                    store.commitStagedRemote(
                        initial.revision,
                        prepared(2, 2, SyncOperationPayload.ApplicationPolicyAbsent),
                        initial.clockState,
                        null,
                    ),
                )
            },
            ResidueCase("terminal expiry") { store, initial ->
                assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                    store.markTerminalExpiry(initial.revision, SessionId(testIdentifier(91))),
                )
            },
        )

        cases.forEachIndexed { index, case ->
            val testDatabase = createLocalPolicyTestDatabase("sync-orphaned-$index.db")
            val driver = testDatabase.openDriver()
            try {
                val store = SqlSyncReplicaStore(PosatoDatabase(driver), Dispatchers.Default)
                val initial = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext)).value
                case.persist(store, initial)
                driver.execute(null, "DELETE FROM sync_replica_state", 0)

                val result = store.open(testContext)

                assertEquals(SyncStoreFailure.CORRUPTION, assertIs<SyncStoreResult.Failure>(result).reason, case.name)
            } finally {
                driver.close()
                testDatabase.delete()
            }
        }
    }

    @Test
    fun `given an accepted insert failure when remote progress commits then history clock and progress roll back together`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-rollback.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlSyncReplicaStore(PosatoDatabase(driver), Dispatchers.Default)
            val initial = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext)).value
            val priorProgress = testTransportProgress(1)
            val withProgress = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                store.commitTransportProgress(initial.revision, priorProgress),
            ).value
            driver.execute(
                identifier = null,
                sql =
                    """
                    CREATE TRIGGER fail_sync_accept
                    BEFORE INSERT ON sync_accepted_bundle
                    BEGIN
                      SELECT RAISE(ABORT, 'synthetic insert failure');
                    END
                    """.trimIndent(),
                parameters = 0,
            )

            val result = store.commitAcceptedRemote(
                expectedRevision = withProgress.revision,
                bundles = listOf(prepared(1, 1, SyncOperationPayload.AuthorRegister)),
                stagedBundleIdsToDelete = emptySet(),
                clockState = DurableClockState(HybridLogicalClock(1, 0), false),
                transportProgress = testTransportProgress(2),
            )
            val after = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.read(testContext)).value

            assertEquals(SyncStoreFailure.STORAGE_FAILURE, assertIs<SyncStoreResult.Failure>(result).reason)
            assertEquals(withProgress, after)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given persisted operation or pending bytes diverge when read then corruption is reported`() = runTest {
        listOf(
            "UPDATE sync_accepted_bundle SET operation_bytes = x'00'" to "operation bytes",
            "UPDATE sync_pending_bundle SET bundle_bytes = x'01'" to "pending bytes",
        ).forEachIndexed { index, (corruption, description) ->
            val testDatabase = createLocalPolicyTestDatabase("sync-corruption-$index.db")
            val driver = testDatabase.openDriver()
            try {
                val store = SqlSyncReplicaStore(PosatoDatabase(driver), Dispatchers.Default)
                val initial = assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(store.open(testContext)).value
                assertIs<SyncStoreResult.Success<SyncReplicaSnapshot>>(
                    store.commitLocal(
                        initial.revision,
                        listOf(prepared(1, 1, SyncOperationPayload.AuthorRegister)),
                        DurableClockState(HybridLogicalClock(1, 0), false),
                    ),
                )
                driver.execute(null, corruption, 0)

                val result = store.read(testContext)

                assertEquals(SyncStoreFailure.CORRUPTION, assertIs<SyncStoreResult.Failure>(result).reason, description)
            } finally {
                driver.close()
                testDatabase.delete()
            }
        }
    }
}

private fun prepared(
    id: Int,
    sequence: Long,
    payload: SyncOperationPayload,
): PreparedStoredBundle {
    return PreparedStoredBundle(
        bundle = checkNotNull(EncryptedBundle.fromBytes(ByteArray(32) { id.toByte() })),
        operation = testOperation(id, sequence, payload),
    )
}
