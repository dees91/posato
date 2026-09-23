package app.posato.feature.update.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlUpdateMaintenanceStoreTest {
    @Test
    fun `given a fresh database when read then the gate is open`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-fresh.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlUpdateMaintenanceStore(PosatoDatabase(driver), Dispatchers.Default)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceGate.Open), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given no session when the gate closes then it stays closed across reopen`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-close.db")
        var driver = testDatabase.openDriver()
        try {
            val first = SqlUpdateMaintenanceStore(PosatoDatabase(driver), Dispatchers.Default)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceCloseOutcome.CLOSED), first.close(FROM_BUILD, TARGET_BUILD, NOW))
            driver.close()

            driver = testDatabase.openDriver()
            val second = SqlUpdateMaintenanceStore(PosatoDatabase(driver), Dispatchers.Default)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceGate.Closed(FROM_BUILD, TARGET_BUILD, NOW)), second.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given an active session when the gate closes then closing is refused and the gate stays open`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-active.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val sessions = SqlLocalSessionStore(database, Dispatchers.Default)
            assertIs<LocalSessionResult.Success<*>>(sessions.start(SessionId(testIdentifier(31)), NOW, NOW + DURATION, NOW, START_SET))
            val store = SqlUpdateMaintenanceStore(database, Dispatchers.Default)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceCloseOutcome.SESSION_ACTIVE), store.close(FROM_BUILD, TARGET_BUILD, NOW + 1L))
            assertEquals(MaintenanceStoreResult.Success(MaintenanceGate.Open), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a session ended early when the gate closes then it closes`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-ended.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val sessions = SqlLocalSessionStore(database, Dispatchers.Default)
            sessions.start(SessionId(testIdentifier(32)), NOW, NOW + DURATION, NOW, START_SET)
            assertIs<LocalSessionResult.Success<*>>(sessions.endEarly(NOW + 1L, null))
            val store = SqlUpdateMaintenanceStore(database, Dispatchers.Default)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceCloseOutcome.CLOSED), store.close(FROM_BUILD, TARGET_BUILD, NOW + 2L))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a session past its end when the gate closes then it closes`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-expired.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val sessions = SqlLocalSessionStore(database, Dispatchers.Default)
            sessions.start(SessionId(testIdentifier(33)), NOW, NOW + DURATION, NOW, START_SET)
            val store = SqlUpdateMaintenanceStore(database, Dispatchers.Default)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceCloseOutcome.CLOSED), store.close(FROM_BUILD, TARGET_BUILD, NOW + DURATION))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a closed gate when it reopens then the gate is open`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-reopen.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlUpdateMaintenanceStore(PosatoDatabase(driver), Dispatchers.Default)
            store.close(FROM_BUILD, TARGET_BUILD, NOW)

            assertEquals(MaintenanceStoreResult.Success(Unit), store.reopen())
            assertEquals(MaintenanceStoreResult.Success(MaintenanceGate.Open), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given a closed gate when a new admission closes it again then the new target build is recorded`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("maintenance-reclose.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlUpdateMaintenanceStore(PosatoDatabase(driver), Dispatchers.Default)
            store.close(FROM_BUILD, TARGET_BUILD, NOW)

            assertEquals(MaintenanceStoreResult.Success(MaintenanceCloseOutcome.CLOSED), store.close(FROM_BUILD, NEWER_TARGET_BUILD, NOW + 1L))
            assertEquals(MaintenanceStoreResult.Success(MaintenanceGate.Closed(FROM_BUILD, NEWER_TARGET_BUILD, NOW + 1L)), store.read())
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private companion object {
        const val NOW: Long = 1_700_000_000_000L
        const val DURATION: Long = 1_800_000L
        const val FROM_BUILD: String = "8"
        const val TARGET_BUILD: String = "9"
        const val NEWER_TARGET_BUILD: String = "10"
        val START_SET: FrozenStartSet = FrozenStartSet(persistentListOf("example.com"), null)
    }
}
