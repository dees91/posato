package app.posato.feature.sync.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.data.LocalPolicyTestDatabase
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlRemovedWorkspaceMigrationTest {
    @Test
    fun `given an empty version eight database when migrated then the tombstone table exists empty`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("removed-workspace-migration-empty.db")
        downgrade(testDatabase, "UPDATE local_policy_metadata SET revision = 0 WHERE singleton = 1")
        val driver = testDatabase.openDriver()
        try {
            assertEquals(0L, PosatoDatabase(driver).syncBootstrapQueries.countRemovedWorkspaces().awaitAsList().single())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given bootstrap and policy rows when migrated then they survive and the tombstone table is empty`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("removed-workspace-migration-seeded.db")
        downgrade(
            testDatabase,
            "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('example.com')",
            "INSERT INTO sync_bootstrap_state(" +
                "singleton, candidate_workspace_id, candidate_transport_epoch_id, " +
                "candidate_key_epoch_id, binding) VALUES " +
                "(1, X'$IDENTIFIER_HEX', X'$IDENTIFIER_HEX', X'$IDENTIFIER_HEX', X'$BINDING_HEX')",
        )
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, database.syncBootstrapQueries.selectBootstrapState().awaitAsList().size)
            assertEquals(
                listOf("example.com"),
                database.localExactDomainPolicyQueries.selectDomains(MAXIMUM_ROWS).awaitAsList(),
            )
            assertEquals(0L, database.syncBootstrapQueries.countRemovedWorkspaces().awaitAsList().single())
            assertTrue(database.syncLocalPolicyQueries.selectIntents().awaitAsList().isEmpty())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private fun downgrade(
        testDatabase: LocalPolicyTestDatabase,
        vararg seeds: String,
    ) {
        val driver = testDatabase.openDriver()
        seeds.forEach { seed -> driver.executeSql(seed) }
        driver.executeSql("DROP TABLE sync_removed_workspace")
        driver.executeSql("DROP TABLE sync_session_intent")
        driver.executeSql("DROP TABLE local_update_maintenance")
        driver.executeSql("ALTER TABLE local_session DROP COLUMN origin")
        driver.executeSql("PRAGMA user_version = $PREVIOUS_VERSION")
        driver.close()
    }

    private companion object {
        const val PREVIOUS_VERSION: Int = 8
        const val MAXIMUM_ROWS: Long = 100L
        const val IDENTIFIER_HEX: String = "000102030405060708090A0B0C0D0E0F"
        const val BINDING_HEX: String = "000102030405060708090A0B0C0D0E0F000102030405060708090A0B0C0D0E0F"
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(
        identifier = null,
        sql = sql,
        parameters = 0,
    ).value
}
