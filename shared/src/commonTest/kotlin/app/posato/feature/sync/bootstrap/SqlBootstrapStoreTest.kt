package app.posato.feature.sync.bootstrap

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlBootstrapStoreTest {
    @Test
    fun `given an empty database when read then none is reported`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-empty.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlBootstrapStore(PosatoDatabase(driver), Dispatchers.Default)

            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a candidate when persisted then the exact identifiers and binding round-trip`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-candidate.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlBootstrapStore(PosatoDatabase(driver), Dispatchers.Default)
            val candidate = PersistedCandidate(
                WorkspaceId(testIdentifier(1)),
                TransportEpochId(testIdentifier(2)),
                KeyEpochId(testIdentifier(3)),
                bindingA,
            )

            assertEquals(BootstrapStoreResult.Success(Unit), store.persistCandidate(candidate))
            assertEquals(BootstrapStoreResult.Success(BootstrapState.Candidate(candidate)), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a candidate when committed then the established workspace replaces it`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-commit.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlBootstrapStore(PosatoDatabase(driver), Dispatchers.Default)
            val candidate = PersistedCandidate(
                WorkspaceId(testIdentifier(1)),
                TransportEpochId(testIdentifier(2)),
                KeyEpochId(testIdentifier(3)),
                bindingA,
            )
            val workspace = EstablishedWorkspace(
                SyncContext(candidate.workspaceId, candidate.transportEpochId, candidate.keyEpochId),
                bindingA,
            )

            assertEquals(BootstrapStoreResult.Success(Unit), store.persistCandidate(candidate))
            assertEquals(BootstrapStoreResult.Success(Unit), store.commitEstablished(workspace))
            assertEquals(BootstrapStoreResult.Success(BootstrapState.Established(workspace)), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given invalid persisted bootstrap rows when read then corruption is reported`() = runTest {
        bootstrapCorruptions.forEachIndexed { index, corruption ->
            val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-corruption-$index.db")
            val driver = testDatabase.openDriver()
            try {
                val database = PosatoDatabase(driver)
                val store = SqlBootstrapStore(database, Dispatchers.Default)
                store.persistCandidate(
                    PersistedCandidate(
                        WorkspaceId(testIdentifier(1)),
                        TransportEpochId(testIdentifier(2)),
                        KeyEpochId(testIdentifier(3)),
                        bindingA,
                    ),
                )
                driver.execute(null, "PRAGMA foreign_keys = OFF", 0)
                database.transaction {
                    driver.execute(null, "PRAGMA ignore_check_constraints = ON", 0)
                    driver.execute(null, corruption, 0)
                    driver.execute(null, "PRAGMA ignore_check_constraints = OFF", 0)
                }

                assertEquals(
                    BootstrapStoreFailure.CORRUPTION,
                    assertIs<BootstrapStoreResult.Failure>(store.read()).reason,
                    corruption,
                )
            } finally {
                driver.close()
                testDatabase.delete()
            }
        }
    }

    @Test
    fun `given existing replica data when bootstrap state changes then replica rows survive unchanged`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-coexist.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlBootstrapStore(database, Dispatchers.Default)
            database.syncReplicaQueries.insertSyncReplicaState(
                workspace_id = testContext.workspaceId.value.copyBytes(),
                transport_epoch_id = testContext.transportEpochId.value.copyBytes(),
                key_epoch_id = testContext.keyEpochId.value.copyBytes(),
            )

            store.persistCandidate(
                PersistedCandidate(
                    WorkspaceId(testIdentifier(11)),
                    TransportEpochId(testIdentifier(12)),
                    KeyEpochId(testIdentifier(13)),
                    bindingA,
                ),
            )
            store.commitEstablished(
                EstablishedWorkspace(
                    SyncContext(
                        WorkspaceId(testIdentifier(11)),
                        TransportEpochId(testIdentifier(12)),
                        KeyEpochId(testIdentifier(13)),
                    ),
                    bindingA,
                ),
            )

            val states = database.syncReplicaQueries.selectSyncReplicaState { _, workspaceId, _, _, _, _, _, _, _ ->
                workspaceId
            }.awaitAsList()
            assertEquals(listOf(testContext.workspaceId.value.copyBytes().toList()), states.map { it.toList() })
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        val bootstrapCorruptions = listOf(
            "UPDATE sync_bootstrap_state SET binding = zeroblob(33)",
            "UPDATE sync_bootstrap_state SET binding = NULL",
            "UPDATE sync_bootstrap_state SET candidate_key_epoch_id = NULL",
            "UPDATE sync_bootstrap_state SET candidate_workspace_id = zeroblob(16)",
            "UPDATE sync_bootstrap_state SET established_workspace_id = zeroblob(16)," +
                " established_transport_epoch_id = zeroblob(16), established_key_epoch_id = zeroblob(16)",
        )
    }
}
