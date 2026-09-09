package app.posato.feature.onboarding.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SqlLocalSetupStoreTest {
    @Test
    fun `given no setup row when read then the completion is incomplete`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-empty.db")
        val driver = testDatabase.openDriver()
        try {
            val store = SqlLocalSetupStore(PosatoDatabase(driver), Dispatchers.Default)
            val result = store.read()

            assertIs<LocalSetupResult.Success<SetupCompletion>>(result)
            assertEquals(SetupCompletion.INCOMPLETE, result.value)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given marked complete when read then the completion is complete across reopen`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-complete.db")
        var driver = testDatabase.openDriver()
        try {
            val first = SqlLocalSetupStore(PosatoDatabase(driver), Dispatchers.Default)
            val marked = first.markComplete()

            assertIs<LocalSetupResult.Success<Unit>>(marked)
            driver.close()

            driver = testDatabase.openDriver()
            val second = SqlLocalSetupStore(PosatoDatabase(driver), Dispatchers.Default)
            val reread = second.read()

            assertIs<LocalSetupResult.Success<SetupCompletion>>(reread)
            assertEquals(SetupCompletion.COMPLETE, reread.value)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    @Test
    fun `given marked twice when read then the completion stays complete with one row`() = runTest {
        val testDatabase = createLocalPolicyTestDatabase("setup-twice.db")
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalSetupStore(database, Dispatchers.Default)
            store.markComplete()
            store.markComplete()
            val result = store.read()

            assertIs<LocalSetupResult.Success<SetupCompletion>>(result)
            assertEquals(SetupCompletion.COMPLETE, result.value)
            assertEquals(1, database.localSetupQueries.selectSetupState().executeAsList().size)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}
