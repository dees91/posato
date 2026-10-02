package app.posato.feature.session.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.data.ScheduleHostUpdate
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.OccurrencePin
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A running part's kept items live exactly as long as the part: rows exist only for the active local session
 * or an occurrence that still has its pin, so a stale row can never revive on a later part with the same key.
 */
class RetentionLifecycleTest {
    @Test
    fun `given a started session when it ends early then its kept websites go with it`() = runTest {
        withDatabase("retention-session.db") { database, driver ->
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            store.start(SessionId(testIdentifier(21)), NOW, NOW + MINIMUM, NOW, START_SET)

            assertEquals(listOf("session|stable.example"), driver.retained())

            store.endEarly(NOW + 1_000)

            assertEquals(emptyList(), driver.retained())
        }
    }

    @Test
    fun `given a session whose expiry is recorded when read then its kept websites are gone`() = runTest {
        withDatabase("retention-expiry.db") { database, driver ->
            val sessionId = SessionId(testIdentifier(22))
            SqlLocalSessionStore(database, Dispatchers.Default).start(sessionId, NOW, NOW + MINIMUM, NOW, START_SET)
            assertEquals(listOf("session|stable.example"), driver.retained())

            SqlSessionExpiryStore(database, Dispatchers.Default).retainExpiryMarker(sessionId)

            assertEquals(emptyList(), driver.retained())
        }
    }

    @Test
    fun `given a kept occurrence when its pin is released then its kept websites go and another pin's stay`() = runTest {
        withDatabase("retention-occurrence.db") { database, driver ->
            val store = SqlScheduleStore(database, Dispatchers.Default)
            val ended = OccurrenceKey(focus, ScheduleDate(2026, 9, 30))
            val running = OccurrenceKey(focus, ScheduleDate(2026, 10, 1))
            store.recordHost(ScheduleHostUpdate(pins = listOf(OccurrencePin(ended, NOW, 0), OccurrencePin(running, NOW + 86_400_000, 0))))
            listOf(30, 1).forEach { day ->
                val month = if (day == 30) 9 else 10
                driver.executeSql(
                    "INSERT INTO local_retained_domain VALUES ('occurrence', X'$FOCUS_HEX', 2026, $month, $day, 'kept$day.example')",
                )
            }

            store.recordHost(ScheduleHostUpdate(released = setOf(ended)))

            assertEquals(listOf("occurrence|kept1.example"), driver.retained())
        }
    }

    private suspend fun withDatabase(
        name: String,
        block: suspend (PosatoDatabase, SqlDriver) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            block(PosatoDatabase(driver), driver)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val NOW: Long = 1_790_000_000_000L
        const val MINIMUM: Long = SessionLimits.MIN_DURATION_MILLIS
        const val FOCUS_HEX: String = "000000000000400080000000000000a1"
        val START_SET: FrozenStartSet = FrozenStartSet(persistentListOf("stable.example"), 1)
        val focus = ScheduleId(FOCUS_HEX)
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).value
}

private fun SqlDriver.retained(): List<String> {
    return executeQuery(
        identifier = null,
        sql = "SELECT part_kind || '|' || canonical_domain FROM local_retained_domain ORDER BY part_kind, canonical_domain",
        mapper = { cursor -> QueryResult.Value(buildList { while (cursor.next().value) add(checkNotNull(cursor.getString(0))) }) },
        parameters = 0,
    ).value
}
