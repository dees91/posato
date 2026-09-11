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

    @Test
    fun `given an established workspace when cleared then the identifier is tombstoned and state is none`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-tombstone.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlBootstrapStore(database, Dispatchers.Default)
            val workspace = establishedWorkspace(1)

            assertEquals(BootstrapStoreResult.Success(Unit), store.commitEstablished(workspace))
            assertEquals(BootstrapStoreResult.Success(Unit), store.clearEstablished(workspace))
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), store.read())
            assertEquals(BootstrapStoreResult.Success(true), store.containsRemoved(workspace.context.workspaceId))
            assertEquals(
                1L,
                database.syncBootstrapQueries.countRemovedWorkspaces().awaitAsList().single(),
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a tombstone when the store is reopened then the identifier is still removed`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-tombstone-relaunch.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlBootstrapStore(PosatoDatabase(driver), Dispatchers.Default)
            val workspace = establishedWorkspace(2)
            assertEquals(BootstrapStoreResult.Success(Unit), store.commitEstablished(workspace))
            assertEquals(BootstrapStoreResult.Success(Unit), store.clearEstablished(workspace))
        } finally {
            driver.close()
        }
        val reopened = testDatabase.openDriver()
        try {
            val store = SqlBootstrapStore(PosatoDatabase(reopened), Dispatchers.Default)
            assertEquals(BootstrapStoreResult.Success(true), store.containsRemoved(establishedWorkspace(2).context.workspaceId))
            assertEquals(BootstrapStoreResult.Success(false), store.containsRemoved(establishedWorkspace(3).context.workspaceId))
            assertEquals(BootstrapStoreResult.Success(BootstrapState.None), store.read())
        } finally {
            reopened.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given more than thirty two removals when cleared then the oldest identifier is evicted`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-tombstone-bound.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlBootstrapStore(PosatoDatabase(driver), Dispatchers.Default)
            val workspaces = (1..(REMOVED_WORKSPACE_LIMIT + 1)).map { establishedWorkspace(it) }
            for (workspace in workspaces) {
                assertEquals(BootstrapStoreResult.Success(Unit), store.commitEstablished(workspace))
                assertEquals(BootstrapStoreResult.Success(Unit), store.clearEstablished(workspace))
            }

            assertEquals(BootstrapStoreResult.Success(false), store.containsRemoved(workspaces.first().context.workspaceId))
            assertEquals(BootstrapStoreResult.Success(true), store.containsRemoved(workspaces[1].context.workspaceId))
            assertEquals(BootstrapStoreResult.Success(true), store.containsRemoved(workspaces.last().context.workspaceId))
            assertEquals(
                REMOVED_WORKSPACE_LIMIT.toLong(),
                PosatoDatabase(driver).syncBootstrapQueries.countRemovedWorkspaces().awaitAsList().single(),
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a mismatched established row when clearing then no tombstone is written`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-bootstrap-tombstone-mismatch.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlBootstrapStore(database, Dispatchers.Default)
            val workspace = establishedWorkspace(7)
            assertEquals(BootstrapStoreResult.Success(Unit), store.commitEstablished(workspace))

            val failure = assertIs<BootstrapStoreResult.Failure>(store.clearEstablished(establishedWorkspace(8)))
            assertEquals(BootstrapStoreFailure.CORRUPTION, failure.reason)
            assertEquals(BootstrapStoreResult.Success(BootstrapState.Established(workspace)), store.read())
            assertEquals(BootstrapStoreResult.Success(false), store.containsRemoved(workspace.context.workspaceId))
            assertEquals(
                0L,
                database.syncBootstrapQueries.countRemovedWorkspaces().awaitAsList().single(),
            )
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

private fun establishedWorkspace(id: Int): EstablishedWorkspace {
    return EstablishedWorkspace(
        SyncContext(
            WorkspaceId(testIdentifier(id)),
            TransportEpochId(testIdentifier(id + 100)),
            KeyEpochId(testIdentifier(id + 200)),
        ),
        bindingA,
    )
}
