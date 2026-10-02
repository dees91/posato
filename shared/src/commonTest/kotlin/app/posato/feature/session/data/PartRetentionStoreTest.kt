package app.posato.feature.session.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.data.ScheduleHostUpdate
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.OccurrencePin
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.toBytes
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.KeptApplication
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A running part's retention grows with what it pauses and never outlives the part. */
class PartRetentionStoreTest {
    private val sessionId = SessionId(testIdentifier(21))
    private val sessionPart = RetainedPart(PART_SESSION, sessionId.value.copyBytes())

    @Test
    fun `given a running session when its items are held then they read back with each app's requirement`() = runTest {
        withStores("part-retention-session.db") { database, retention ->
            SqlLocalSessionStore(database, Dispatchers.Default).start(sessionId, NOW, NOW + MINIMUM, NOW, FrozenStartSet(persistentListOf(), 0))

            retention.hold(sessionPart, RetainedItems(setOf("a.example", "b.example"), listOf(mail)))
            retention.hold(sessionPart, RetainedItems(setOf("b.example", "c.example")))

            val read = retention.read(sessionPart)
            assertEquals(setOf("a.example", "b.example", "c.example"), read.domains)
            assertEquals(listOf(9.toByte()), read.applications.single().designatedRequirement.toList())
        }
    }

    @Test
    fun `given a session that ended early when its items are held then nothing is kept`() = runTest {
        withStores("part-retention-ended.db") { database, retention ->
            val sessions = SqlLocalSessionStore(database, Dispatchers.Default)
            sessions.start(sessionId, NOW, NOW + MINIMUM, NOW, FrozenStartSet(persistentListOf(), 0))
            sessions.endEarly(NOW + 1_000)

            retention.hold(sessionPart, RetainedItems(setOf("late.example")))

            assertEquals(emptySet(), retention.read(sessionPart).domains)
        }
    }

    @Test
    fun `given an occurrence with and without its pin when held then only the pinned one keeps items`() = runTest {
        withStores("part-retention-occurrence.db") { database, retention ->
            val pinned = OccurrenceKey(FOCUS, ScheduleDate(2026, 10, 1))
            SqlScheduleStore(database, Dispatchers.Default).recordHost(ScheduleHostUpdate(pins = listOf(OccurrencePin(pinned, NOW, 0))))
            val pinnedPart = RetainedPart(PART_OCCURRENCE, FOCUS.toBytes(), 2026, 10, 1)
            val unpinnedPart = RetainedPart(PART_OCCURRENCE, FOCUS.toBytes(), 2026, 10, 2)

            retention.hold(pinnedPart, RetainedItems(setOf("kept.example")))
            retention.hold(unpinnedPart, RetainedItems(setOf("stray.example")))

            assertEquals(setOf("kept.example"), retention.read(pinnedPart).domains)
            assertEquals(emptySet(), retention.read(unpinnedPart).domains)
        }
    }

    private val mail = KeptApplication(ByteArray(32) { 5 }, "Mail".encodeToByteArray(), byteArrayOf(9))

    private suspend fun withStores(
        name: String,
        block: suspend (PosatoDatabase, PartRetentionStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            block(database, SqlPartRetentionStore(database, Dispatchers.Default))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val NOW: Long = 1_790_000_000_000L
        const val MINIMUM: Long = SessionLimits.MIN_DURATION_MILLIS
        val FOCUS: ScheduleId = ScheduleId("000000000000400080000000000000a1")
    }
}
