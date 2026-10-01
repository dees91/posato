package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createDesktopDatabaseDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * The Mac opens one SQLite connection per database thread, so a read runs while another connection writes,
 * such as the Pause sets list loading while a running pause is applied again. A read must take only a read
 * lock: one that also writes waits for the other writer and then fails, and the list says it could not load.
 */
class ScheduleStoreContentionTest {
    @Test
    fun `given another connection holding the write lock when schedules are read then the read succeeds`() = runBlocking<Unit> {
        val path = Files.createTempDirectory("posato-contention-").resolve("posato-policy.db").toString()
        val driver = createDesktopDatabaseDriver(path)
        val store = SqlScheduleStore(PosatoDatabase(driver), Dispatchers.IO)
        assertIs<ScheduleResult.Success<*>>(store.read())
        val writer = DriverManager.getConnection("jdbc:sqlite:$path")
        try {
            writer.createStatement().use { statement -> statement.execute("BEGIN IMMEDIATE") }

            val read = withContext(Dispatchers.IO) { store.read() }

            assertIs<ScheduleResult.Success<*>>(read)
        } finally {
            writer.createStatement().use { statement -> statement.execute("ROLLBACK") }
            writer.close()
            driver.close()
        }
    }
}
