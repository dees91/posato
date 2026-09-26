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

class SqlSyncLocalPolicyMigrationTest {
    @Test
    fun `given domains and policies when migrated then rows survive and policy sync tables exist empty`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("sync-local-policy-migration-seeded.db")
        downgrade(
            testDatabase,
            "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('example.com')",
            "INSERT INTO application_policy(singleton, canonical_name) VALUES (1, 'Example group')",
        )
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(
                listOf("example.com"),
                database.localExactDomainPolicyQueries.selectDomains(MAXIMUM_ROWS).awaitAsList(),
            )
            assertEquals(
                listOf("Example group"),
                database.localExactDomainPolicyQueries.selectApplicationPolicyNameBytes().awaitAsList()
                    .map { bytes -> bytes.decodeToString() },
            )
            assertPolicySyncTablesEmpty(database)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun assertPolicySyncTablesEmpty(database: PosatoDatabase) {
        val queries = database.syncLocalPolicyQueries
        assertTrue(queries.selectIntents().awaitAsList().isEmpty())
        assertTrue(queries.selectBaseMarker().awaitAsList().isEmpty())
        assertTrue(queries.selectBaseDomains().awaitAsList().isEmpty())
        assertTrue(queries.selectBaseApplicationName().awaitAsList().isEmpty())
    }

    private fun downgrade(
        testDatabase: LocalPolicyTestDatabase,
        vararg seeds: String,
    ) {
        val driver = testDatabase.openDriver()
        seeds.forEach { seed -> driver.executeSql(seed) }
        driver.executeSql("DROP TABLE sync_policy_intent")
        driver.executeSql("DROP TABLE sync_policy_base")
        driver.executeSql("DROP TABLE sync_policy_base_domain")
        driver.executeSql("DROP TABLE sync_policy_base_application")
        driver.executeSql("DROP TABLE sync_removed_workspace")
        driver.executeSql("DROP TABLE sync_session_intent")
        driver.executeSql("DROP TABLE local_update_maintenance")
        driver.executeSql("ALTER TABLE local_session DROP COLUMN origin")
        driver.executeSql("PRAGMA user_version = $PREVIOUS_VERSION")
        driver.close()
    }

    private companion object {
        const val PREVIOUS_VERSION: Int = 7
        const val MAXIMUM_ROWS: Long = 100L
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(
        identifier = null,
        sql = sql,
        parameters = 0,
    ).value
}
