package app.posato.feature.targets.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlWwwCounterpartExpansionMigrationTest {
    @Test
    fun `given an empty version ten database when migrated then the expansion table exists empty`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("www-expansion-migration-empty.db")
        downgrade(testDatabase)
        val driver = testDatabase.openDriver()
        try {
            assertTrue(
                PosatoDatabase(driver).localExactDomainPolicyQueries
                    .selectWwwCounterpartExpansion()
                    .awaitAsList()
                    .isEmpty(),
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a domain when migrated then the row survives and the expansion table is empty`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("www-expansion-migration-seeded.db")
        downgrade(testDatabase, "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('example.com')")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            assertEquals(
                listOf("example.com"),
                database.localExactDomainPolicyQueries.selectDomains(MAXIMUM_ROWS).awaitAsList(),
            )
            assertTrue(database.localExactDomainPolicyQueries.selectWwwCounterpartExpansion().awaitAsList().isEmpty())
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
        driver.executeSql("DROP TABLE www_counterpart_expansion")
        driver.executeSql("PRAGMA user_version = $PREVIOUS_VERSION")
        driver.close()
    }

    private companion object {
        const val PREVIOUS_VERSION: Int = 10
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
