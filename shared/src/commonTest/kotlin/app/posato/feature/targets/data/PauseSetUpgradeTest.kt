package app.posato.feature.targets.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one-time step after migration 13: a manual session or schedule occurrence running at the upgrade keeps
 * the first set's items of that moment (the maintainer's 2026-09-30 choice), recorded once, from a database a
 * released 1.2 build left behind.
 */
class PauseSetUpgradeTest {
    @Test
    fun `given a running session and occurrence at the upgrade when completed then both keep the first set's items once`() = runTest {
        withUpgradedDatabase("upgrade-running.db", endedEarly = false) { database, driver ->
            val kept = listOf(KeptApplication(ByteArray(32) { 5 }, "Mail".encodeToByteArray(), byteArrayOf(9)))
            val upgrade = PauseSetUpgrade(database, Dispatchers.Default, { NOW }, { kept })

            upgrade.complete()
            driver.executeSql("INSERT INTO local_pause_set_domain VALUES (zeroblob(16), 'late.example')")
            upgrade.complete()

            assertEquals(
                listOf("occurrence|a.example", "occurrence|b.example", "session|a.example", "session|b.example"),
                driver.rows("SELECT part_kind || '|' || canonical_domain FROM local_retained_domain ORDER BY part_kind, canonical_domain"),
            )
            assertEquals(listOf("occurrence", "session"), driver.rows("SELECT part_kind FROM local_retained_application ORDER BY part_kind"))
            assertEquals(emptyList(), driver.rows("SELECT singleton FROM local_pause_set_upgrade"))
            assertEquals(listOf("-"), driver.rows("SELECT frozen_domains FROM local_session"))
        }
    }

    @Test
    fun `given a session ended before the upgrade when completed then nothing is kept for it`() = runTest {
        withUpgradedDatabase("upgrade-ended.db", endedEarly = true) { database, driver ->
            PauseSetUpgrade(database, Dispatchers.Default, { NOW }, { emptyList() }).complete()

            assertEquals(emptyList(), driver.rows("SELECT canonical_domain FROM local_retained_domain WHERE part_kind = 'session'"))
            assertEquals(emptyList(), driver.rows("SELECT singleton FROM local_pause_set_upgrade"))
        }
    }

    private suspend fun withUpgradedDatabase(
        name: String,
        endedEarly: Boolean,
        block: suspend (PosatoDatabase, SqlDriver) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val seeding = testDatabase.openDriverAt(RELEASED_VERSION)
        listOf(
            "INSERT INTO exact_domain_policy(canonical_domain) VALUES ('a.example'), ('b.example')",
            "INSERT INTO local_session(singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early, frozen_domains, " +
                "frozen_application_count, origin) VALUES (1, X'$SESSION', ${NOW - 1_000}, ${NOW + 600_000}, ${if (endedEarly) 1 else 0}, " +
                "'[\"a.example\"]', 0, 'local')",
            "INSERT INTO local_schedule(schedule_id, name, weekdays, start_minute, end_minute, enabled, refused) " +
                "VALUES (X'$FOCUS', 'Focus', 127, 540, 600, 1, 0)",
            "INSERT INTO local_schedule_pin VALUES (X'$FOCUS', 2026, 9, 30, ${NOW - 60_000}, 0)",
        ).forEach(seeding::executeSql)
        seeding.close()
        val driver = testDatabase.openDriver()
        try {
            block(PosatoDatabase(driver), driver)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val RELEASED_VERSION: Long = 12
        const val NOW: Long = 1_790_000_000_000L
        const val SESSION: String = "000102030405060708090A0B0C0D0E0F"
        const val FOCUS: String = "000000000000400080000000000000A1"
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}

private fun SqlDriver.rows(sql: String): List<String> {
    return executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor -> QueryResult.Value(buildList { while (cursor.next().value) add(cursor.getString(0) ?: "-") }) },
        parameters = 0,
    ).value
}
