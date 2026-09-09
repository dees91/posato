package app.posato.feature.onboarding.data

import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createDesktopDatabaseDriver
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlLocalSetupMigrationTest {
    @Test
    fun `given an empty version 6 database when migrated then no setup row is seeded`() {
        val path = migrateFromVersion6("setup-migration-empty.db") { }
        val driver = createDesktopDatabaseDriver(path)
        try {
            assertEquals(0, setupRowCount(PosatoDatabase(driver)))
        } finally {
            driver.close()
            deleteDatabase(path)
        }
    }

    @Test
    fun `given a domain when migrated then the setup row is seeded and the domain survives`() {
        val path = migrateFromVersion6("setup-migration-domain.db") { connection ->
            connection.prepareStatement("INSERT INTO exact_domain_policy(canonical_domain) VALUES (?)").use { statement ->
                statement.setString(1, "example.com")
                statement.executeUpdate()
            }
        }
        val driver = createDesktopDatabaseDriver(path)
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(database))
            assertEquals(
                listOf("example.com"),
                database.localExactDomainPolicyQueries.selectDomains(MAXIMUM_ROWS).executeAsList(),
            )
        } finally {
            driver.close()
            deleteDatabase(path)
        }
    }

    @Test
    fun `given an application policy when migrated then the setup row is seeded and the policy survives`() {
        val path = migrateFromVersion6("setup-migration-policy.db") { connection ->
            connection.prepareStatement("INSERT INTO application_policy(singleton, canonical_name) VALUES (1, ?)").use { statement ->
                statement.setString(1, "Social feeds")
                statement.executeUpdate()
            }
        }
        val driver = createDesktopDatabaseDriver(path)
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(database))
            assertTrue(database.localExactDomainPolicyQueries.selectApplicationPolicyNameBytes().executeAsList().isNotEmpty())
        } finally {
            driver.close()
            deleteDatabase(path)
        }
    }

    @Test
    fun `given a bootstrap row when migrated then the setup row is seeded and the candidate survives`() {
        val path = migrateFromVersion6("setup-migration-bootstrap.db") { connection ->
            connection.prepareStatement(
                "INSERT INTO sync_bootstrap_state(" +
                    "singleton, candidate_workspace_id, candidate_transport_epoch_id, " +
                    "candidate_key_epoch_id, binding) VALUES (1, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setBytes(1, ByteArray(IDENTIFIER_BYTES) { it.toByte() })
                statement.setBytes(2, ByteArray(IDENTIFIER_BYTES) { (it + 1).toByte() })
                statement.setBytes(3, ByteArray(IDENTIFIER_BYTES) { (it + 2).toByte() })
                statement.setBytes(4, ByteArray(BINDING_BYTES))
                statement.executeUpdate()
            }
        }
        val driver = createDesktopDatabaseDriver(path)
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(database))
            assertEquals(1, database.syncBootstrapQueries.selectBootstrapState().executeAsList().size)
        } finally {
            driver.close()
            deleteDatabase(path)
        }
    }

    @Test
    fun `given a session when migrated then the setup row is seeded and the session survives`() {
        val path = migrateFromVersion6("setup-migration-session.db") { connection ->
            connection.prepareStatement(
                "INSERT INTO local_session(" +
                    "singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early) " +
                    "VALUES (1, ?, ?, ?, 0)",
            ).use { statement ->
                statement.setBytes(1, ByteArray(IDENTIFIER_BYTES) { it.toByte() })
                statement.setLong(2, START_MILLIS)
                statement.setLong(3, END_MILLIS)
                statement.executeUpdate()
            }
        }
        val driver = createDesktopDatabaseDriver(path)
        try {
            val database = PosatoDatabase(driver)
            assertEquals(1, setupRowCount(database))
            assertEquals(1, database.localSessionQueries.selectSession().executeAsList().size)
        } finally {
            driver.close()
            deleteDatabase(path)
        }
    }

    private fun setupRowCount(database: PosatoDatabase): Int {
        return database.localSetupQueries.selectSetupState().executeAsList().size
    }

    private fun migrateFromVersion6(name: String, seed: (Connection) -> Unit): String {
        val directory = Files.createTempDirectory("posato-onboarding-001-")
        val path = directory.resolve(name).toString()
        val driver = createDesktopDatabaseDriver(path)
        driver.close()
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DROP TABLE local_setup_state")
            }
            seed(connection)
            connection.createStatement().use { statement ->
                statement.executeUpdate("PRAGMA user_version = $PREVIOUS_VERSION")
            }
        }
        return path
    }

    private fun deleteDatabase(path: String) {
        val file = Path.of(path)
        listOf(
            file,
            file.resolveSibling("${file.fileName}-journal"),
            file.resolveSibling("${file.fileName}-shm"),
            file.resolveSibling("${file.fileName}-wal"),
        ).forEach(Files::deleteIfExists)
        Files.deleteIfExists(file.parent)
    }

    private companion object {
        const val PREVIOUS_VERSION: Int = 6
        const val IDENTIFIER_BYTES: Int = 16
        const val BINDING_BYTES: Int = 32
        const val START_MILLIS: Long = 1_700_000_000_000L
        const val END_MILLIS: Long = 1_700_000_150_000L
        const val MAXIMUM_ROWS: Long = 100L
    }
}
