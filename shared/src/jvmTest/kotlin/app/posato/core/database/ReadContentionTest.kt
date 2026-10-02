package app.posato.core.database

import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.SqlLocalSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * The Mac opens one SQLite connection per database thread, so a read runs while another connection writes,
 * such as the Pause sets list loading, or a running session being applied again, while a set edit is saved.
 * A read must take only a read lock: one that also writes fails at once against the other writer, the list
 * says it could not load, and the session says its restrictions need attention.
 */
class ReadContentionTest {
    @Test
    fun `given another connection holding the write lock when schedules are read then the read succeeds`() = runBlocking<Unit> {
        withWriteLockHeld { database ->
            val store = SqlScheduleStore(database, Dispatchers.IO)

            assertIs<ScheduleResult.Success<*>>(withContext(Dispatchers.IO) { store.read() })
        }
    }

    @Test
    fun `given another connection holding the write lock when the session is read then the read succeeds`() = runBlocking<Unit> {
        withWriteLockHeld { database ->
            val store = SqlLocalSessionStore(database, Dispatchers.IO)

            assertIs<LocalSessionResult.Success<*>>(withContext(Dispatchers.IO) { store.read(0L) })
        }
    }

    private suspend fun withWriteLockHeld(block: suspend (PosatoDatabase) -> Unit) {
        val path = Files.createTempDirectory("posato-contention-").resolve("posato-policy.db").toString()
        val driver = createDesktopDatabaseDriver(path)
        val database = PosatoDatabase(driver)
        // Opening runs the migrations, so the schema exists before the other connection locks the file.
        SqlScheduleStore(database, Dispatchers.IO).read()
        val writer = DriverManager.getConnection("jdbc:sqlite:$path")
        try {
            writer.createStatement().use { statement -> statement.execute("BEGIN IMMEDIATE") }
            block(database)
        } finally {
            writer.createStatement().use { statement -> statement.execute("ROLLBACK") }
            writer.close()
            driver.close()
        }
    }
}
