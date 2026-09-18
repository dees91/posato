package app.posato.feature.onboarding.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.data.LocalPolicyTestDatabase
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlLocalSetupMigrationTest {
    @Test
    fun `given an empty version six database when migrated then no setup row is seeded`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-migration-empty.db")
        downgrade(testDatabase, "UPDATE local_policy_metadata SET revision = 0 WHERE singleton = 1")
        val driver = testDatabase.openDriver()
        try {
            assertEquals(0, setupRowCount(driver))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a domain when migrated then the setup row is seeded and the domain survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-migration-domain.db")
        downgrade(
            testDatabase,
            "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('example.com')",
        )
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(driver))
            assertEquals(
                listOf("example.com"),
                database.localExactDomainPolicyQueries.selectDomains(MAXIMUM_ROWS).awaitAsList(),
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an application policy when migrated then the setup row is seeded and the policy survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-migration-policy.db")
        downgrade(
            testDatabase,
            "INSERT INTO application_policy(singleton, canonical_name) VALUES (1, 'Social feeds')",
        )
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(driver))
            assertTrue(database.localExactDomainPolicyQueries.selectApplicationPolicyNameBytes().awaitAsList().isNotEmpty())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a bootstrap row when migrated then the setup row is seeded and the candidate survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-migration-bootstrap.db")
        downgrade(
            testDatabase,
            "INSERT INTO sync_bootstrap_state(" +
                "singleton, candidate_workspace_id, candidate_transport_epoch_id, " +
                "candidate_key_epoch_id, binding) VALUES " +
                "(1, X'$IDENTIFIER_HEX', X'$IDENTIFIER_HEX', X'$IDENTIFIER_HEX', X'$BINDING_HEX')",
        )
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(driver))
            assertEquals(1, database.syncBootstrapQueries.selectBootstrapState().awaitAsList().size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a session when migrated then the setup row is seeded and the session survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-migration-session.db")
        downgrade(
            testDatabase,
            "INSERT INTO local_session(" +
                "singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early, origin) " +
                "VALUES (1, X'$IDENTIFIER_HEX', $START_MILLIS, $END_MILLIS, 0, 'local')",
        )
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(driver))
            assertEquals(1, database.localSessionQueries.selectSession().awaitAsList().size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun setupRowCount(driver: SqlDriver): Int {
        return PosatoDatabase(driver).localSetupQueries.selectSetupState().awaitAsList().size
    }

    private fun downgrade(
        testDatabase: LocalPolicyTestDatabase,
        seed: String,
    ) {
        val driver = testDatabase.openDriver()
        driver.executeSql(seed)
        driver.executeSql("DROP TABLE local_setup_state")
        driver.executeSql("DROP TABLE sync_policy_intent")
        driver.executeSql("DROP TABLE sync_policy_base")
        driver.executeSql("DROP TABLE sync_policy_base_domain")
        driver.executeSql("DROP TABLE sync_policy_base_application")
        driver.executeSql("DROP TABLE sync_removed_workspace")
        driver.executeSql("DROP TABLE sync_session_intent")
        driver.executeSql("DROP TABLE www_counterpart_expansion")
        driver.executeSql("ALTER TABLE local_session DROP COLUMN origin")
        driver.executeSql("PRAGMA user_version = $PREVIOUS_VERSION")
        driver.close()
    }

    private companion object {
        const val PREVIOUS_VERSION: Int = 6
        const val START_MILLIS: Long = 1_700_000_000_000L
        const val END_MILLIS: Long = 1_700_000_150_000L
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
