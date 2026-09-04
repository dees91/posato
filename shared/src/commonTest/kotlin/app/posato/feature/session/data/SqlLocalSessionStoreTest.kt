package app.posato.feature.session.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionLimits
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlLocalSessionStoreTest {
    @Test
    fun `given no session when read then the status is inactive`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-empty.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            val result = store.read(NOW)

            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(result)
            assertEquals(LocalSessionStatus.Inactive, result.value)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a started session when read then it stays active across reopen`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-restart.db")
        var driver = testDatabase.openDriver()
        try {
            val first = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            val started = first.start(SessionId(testIdentifier(11)), NOW, NOW + MINIMUM, NOW)

            assertIs<LocalSessionResult.Success<LocalSessionStatus>>(started)
            driver.close()

            driver = testDatabase.openDriver()
            val second = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            val reread = second.read(NOW + 60_000L)
            val success = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(reread)
            val active = assertIs<LocalSessionStatus.Active>(success.value)

            assertEquals(MINIMUM - 60_000L, active.remainingMillis)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an active session when a second start arrives then it is refused without a write`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-double-start.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            store.start(SessionId(testIdentifier(21)), NOW, NOW + MINIMUM, NOW)
            val second = store.start(SessionId(testIdentifier(22)), NOW, NOW + MINIMUM, NOW)

            assertIs<LocalSessionResult.Failure>(second)
            assertEquals(LocalSessionFailure.ALREADY_ACTIVE, second.reason)
            val reread = store.read(NOW)
            val success = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(reread)
            val active = assertIs<LocalSessionStatus.Active>(success.value)

            assertEquals(SessionId(testIdentifier(21)), active.record.sessionId)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an active session when the end passes then the marker commits before the expired status`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-expiry.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            store.start(SessionId(testIdentifier(31)), NOW, NOW + MINIMUM, NOW)
            val expired = store.read(NOW + MINIMUM)
            val success = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(expired)
            val ended = assertIs<LocalSessionStatus.Ended>(success.value)

            assertEquals(SessionEndKind.EXPIRED, ended.kind)
            val markers = database.localSessionQueries.selectExpiryMarker(testIdentifier(31).copyBytes()).awaitAsList()

            assertEquals(1, markers.size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an expired session when the clock rolls back then it stays expired`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-rollback.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            store.start(SessionId(testIdentifier(41)), NOW, NOW + MINIMUM, NOW)
            store.read(NOW + MINIMUM)
            val reread = store.read(NOW - 3_600_000L)
            val success = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(reread)
            val ended = assertIs<LocalSessionStatus.Ended>(success.value)

            assertEquals(SessionEndKind.EXPIRED, ended.kind)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an active session when ended early then it ends without a marker`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-early-end.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            store.start(SessionId(testIdentifier(51)), NOW, NOW + MINIMUM, NOW)
            val ended = store.endEarly(NOW + 60_000L)
            val success = assertIs<LocalSessionResult.Success<LocalSessionStatus>>(ended)
            val early = assertIs<LocalSessionStatus.Ended>(success.value)

            assertEquals(SessionEndKind.ENDED_EARLY, early.kind)
            val markers = database.localSessionQueries.selectExpiryMarker(testIdentifier(51).copyBytes()).awaitAsList()

            assertEquals(0, markers.size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given no session when ended early then the command fails`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-early-end-empty.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            val result = store.endEarly(NOW)

            assertIs<LocalSessionResult.Failure>(result)
            assertEquals(LocalSessionFailure.SESSION_NOT_ACTIVE, result.reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given stored bytes without a v4 identifier when read then corruption fails closed without a marker`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-bad-identifier.db")
        val driver = testDatabase.openDriver()
        try {
            driver.executeSql(
                "INSERT INTO local_session(singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early)" +
                    " VALUES (1, X'11111111111111111111111111111111', $NOW, ${NOW + MINIMUM}, 0)",
            )
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            val result = store.read(NOW)

            assertIs<LocalSessionResult.Failure>(result)
            assertEquals(LocalSessionFailure.CORRUPTION, result.reason)
            val markers = database.localSessionQueries.selectExpiryMarker(ByteArray(16) { 0x11.toByte() }).awaitAsList()

            assertEquals(0, markers.size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a stored end before its start when read then corruption fails closed without a marker`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-inverted-record.db")
        val driver = testDatabase.openDriver()
        try {
            val identifier = testIdentifier(71).copyBytes()
            driver.executeSql(
                "INSERT INTO local_session(singleton, session_id, start_epoch_millis, end_epoch_millis, ended_early)" +
                    " VALUES (1, X'${identifier.toHexString()}', ${NOW + MINIMUM}, $NOW, 0)",
            )
            val database = PosatoDatabase(driver)
            val store = SqlLocalSessionStore(database, Dispatchers.Default)
            val result = store.read(NOW)

            assertIs<LocalSessionResult.Failure>(result)
            assertEquals(LocalSessionFailure.CORRUPTION, result.reason)
            val markers = database.localSessionQueries.selectExpiryMarker(identifier).awaitAsList()

            assertEquals(0, markers.size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a past end when started then the session is refused`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("session-past.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalSessionStore(PosatoDatabase(driver), Dispatchers.Default)
            val result = store.start(SessionId(testIdentifier(61)), NOW - MINIMUM, NOW - 1_000L, NOW)

            assertIs<LocalSessionResult.Failure>(result)
            assertEquals(LocalSessionFailure.INVALID_SESSION, result.reason)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
        const val MINIMUM: Long = SessionLimits.MIN_DURATION_MILLIS
    }
}

private fun SqlDriver.executeSql(sql: String) {
    execute(
        identifier = null,
        sql = sql,
        parameters = 0,
    ).value
}

private fun ByteArray.toHexString(): String {
    return joinToString("") { ((it.toInt() and 0xFF).toString(16)).padStart(2, '0') }
}
