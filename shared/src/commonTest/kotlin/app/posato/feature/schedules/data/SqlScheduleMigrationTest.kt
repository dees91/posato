package app.posato.feature.schedules.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlScheduleMigrationTest {
    @Test
    fun `given a version eleven database with a session when migrated then schedules work and the session survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("schedule-migration.db")
        val seeding = testDatabase.openDriver()
        seeding.executeSql(
            "INSERT INTO local_session(singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early, origin) " +
                "VALUES (1, X'000102030405060708090A0B0C0D0E0F', 1700000000000, 1700000150000, 0, 'local')",
        )
        listOf(
            "local_schedule",
            "local_schedule_fact",
            "local_schedule_terminal",
            "local_schedule_pin",
            "local_schedule_expiry",
            "sync_schedule_intent",
            "sync_schedule_seed",
        ).forEach { table -> seeding.executeSql("DROP TABLE $table") }
        seeding.executeSql("PRAGMA user_version = 11")
        seeding.close()

        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlScheduleStore(database, Dispatchers.Default)
            val plan = SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Focus", 1, 540, 600, true)

            assertIs<ScheduleResult.Success<SchedulePlan>>(store.save(plan, null))
            assertEquals(listOf(plan), assertIs<ScheduleResult.Success<ScheduleSnapshot>>(store.read()).value.runnable)
            assertEquals(1, database.localSessionQueries.selectSession().awaitAsList().size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}
