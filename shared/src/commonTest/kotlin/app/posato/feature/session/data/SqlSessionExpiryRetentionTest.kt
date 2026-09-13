package app.posato.feature.session.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlSessionExpiryRetentionTest {
    @Test
    fun `given a replacement when it lands then earlier markers are retained`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-retain-across-replace.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            val old = SessionId(testIdentifier(124))
            val fresh = SessionId(testIdentifier(125))
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                store.start(old, NOW, NOW + MINIMUM, NOW, START_SET),
            )
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.markExpired(old))

            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                store.adopt(fresh, NOW, NOW + MINIMUM, NOW, START_SET),
            )

            assertEquals(
                setOf(old),
                assertIs<LocalSessionResult.Success<Set<SessionId>>>(store.retainedExpiryMarkers()).value,
            )
            assertIs<LocalSessionStatus.Active>(
                assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.read(NOW)).value,
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a retained marker when deleted then the row read is unaffected`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-marker-delete.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            val old = SessionId(testIdentifier(126))
            val fresh = SessionId(testIdentifier(127))
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                store.start(old, NOW, NOW + MINIMUM, NOW, START_SET),
            )
            assertIs<LocalSessionResult.Success<Unit>>(store.retainExpiryMarker(old))
            assertIs<LocalSessionResult.Success<Unit>>(store.retainExpiryMarker(fresh))
            assertIs<LocalSessionResult.Success<Unit>>(store.deleteExpiryMarker(old))

            assertEquals(
                setOf(fresh),
                assertIs<LocalSessionResult.Success<Set<SessionId>>>(store.retainedExpiryMarkers()).value,
            )
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given ownership end when markers drop then only the occupant survives`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-marker-drop.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            val old = SessionId(testIdentifier(128))
            val fresh = SessionId(testIdentifier(129))
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                store.start(old, NOW, NOW + MINIMUM, NOW, START_SET),
            )
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.markExpired(old))
            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
                store.adopt(fresh, NOW, NOW + MINIMUM, NOW, START_SET),
            )

            assertIs<LocalSessionResult.Success<Unit>>(store.dropRetainedMarkersExceptCurrent())

            assertEquals(
                emptySet(),
                assertIs<LocalSessionResult.Success<Set<SessionId>>>(store.retainedExpiryMarkers()).value,
            )
            // The occupant row itself is untouched by the drop.
            val current = assertIs<LocalSessionStatus.Active>(
                assertIs<LocalSessionResult.Success<LocalSessionStatus>>(store.read(NOW)).value,
            )
            assertEquals(fresh, current.record.sessionId)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
        const val MINIMUM: Long = SessionLimits.MIN_DURATION_MILLIS
        val START_SET: FrozenStartSet = FrozenStartSet(persistentListOf("stable.example"), 1)
    }
}
