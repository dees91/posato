package app.posato.feature.schedules.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.ScheduleOccurrences
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun `given a released version twelve database when migrated then local stops are gone and pins and facts survive`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("schedule-migration-12.db")
        val seeding = testDatabase.openDriver()
        listOf("local_schedule_expiry", "local_schedule_pin").forEach { table -> seeding.executeSql("DROP TABLE $table") }
        seeding.executeSql("DROP TABLE IF EXISTS local_schedule_terminal")
        seeding.executeSql(
            "CREATE TABLE local_schedule_terminal (schedule_id BLOB NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, " +
                "day INTEGER NOT NULL, PRIMARY KEY (schedule_id, year, month, day))",
        )
        seeding.executeSql(
            "CREATE TABLE local_schedule_pin (schedule_id BLOB NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, " +
                "day INTEGER NOT NULL, start_epoch_millis INTEGER NOT NULL, notices INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (schedule_id, year, month, day))",
        )
        seeding.executeSql(
            "INSERT INTO local_schedule(schedule_id, name, weekdays, start_minute, end_minute, enabled, refused) " +
                "VALUES (X'$FOCUS_HEX', 'Focus', 1, 540, 600, 1, 0)",
        )
        seeding.executeSql("INSERT INTO local_schedule_terminal VALUES (X'$FOCUS_HEX', 2026, 9, 28)")
        seeding.executeSql("INSERT INTO local_schedule_fact VALUES (X'$FOCUS_HEX', 'skip', 2026, 10, 5)")
        seeding.executeSql("INSERT INTO local_schedule_pin VALUES (X'$FOCUS_HEX', 2026, 9, 21, 1790000000000, 1)")
        seeding.executeSql("PRAGMA user_version = 12")
        seeding.close()

        val driver = testDatabase.openDriver()
        try {
            val store = SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default)
            val snapshot = assertIs<ScheduleResult.Success<ScheduleSnapshot>>(store.read()).value
            val focus = ScheduleId(FOCUS_HEX.lowercase())
            val monday = ScheduleDate(2026, 9, 28)

            assertEquals(1, snapshot.pins.single().notices)
            assertEquals(setOf(OccurrenceKey(focus, ScheduleDate(2026, 10, 5))), snapshot.facts.skipped)
            val now = CentralEuropeanZone.instantOf(monday, 570)
            val running = ScheduleOccurrences.active(snapshot.runnable, snapshot.facts, now, CentralEuropeanZone)
            assertEquals(OccurrenceKey(focus, monday), running.single().key)
            val tables = driver.executeQuery(
                identifier = null,
                sql = "SELECT count(*) FROM sqlite_master WHERE name = 'local_schedule_terminal'",
                mapper = { cursor -> QueryResult.Value(cursor.next().value && cursor.getLong(0) == 0L) },
                parameters = 0,
            ).value
            assertTrue(tables)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}

private const val FOCUS_HEX: String = "000000000000400080000000000000A1"

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}
