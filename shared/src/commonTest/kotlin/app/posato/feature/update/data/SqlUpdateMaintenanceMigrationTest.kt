package app.posato.feature.update.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlUpdateMaintenanceMigrationTest {
    @Test
    fun `given a version ten session when migrated then the gate is open and the session survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-migration.db")
        val seeding = testDatabase.openDriver()
        seeding.executeSql(
            "INSERT INTO local_session(" +
                "singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early, origin) " +
                "VALUES (1, X'$IDENTIFIER_HEX', $START_MILLIS, $END_MILLIS, 0, 'local')",
        )
        seeding.executeSql("DROP TABLE local_update_maintenance")
        seeding.executeSql("PRAGMA user_version = $PREVIOUS_VERSION")
        seeding.close()

        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)

            assertEquals(1, database.localSessionQueries.selectSession().awaitAsList().size)
            assertEquals(
                MaintenanceStoreResult.Success(MaintenanceGate.Open),
                SqlUpdateMaintenanceStore(database, Dispatchers.Default).read(),
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val PREVIOUS_VERSION: Int = 10
        const val START_MILLIS: Long = 1_700_000_000_000L
        const val END_MILLIS: Long = 1_700_000_150_000L
        const val IDENTIFIER_HEX: String = "000102030405060708090A0B0C0D0E0F"
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(
        identifier = null,
        sql = sql,
        parameters = 0,
    ).value
}
