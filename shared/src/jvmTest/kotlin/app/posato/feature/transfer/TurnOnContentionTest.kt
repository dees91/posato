package app.posato.feature.transfer

import app.cash.sqldelight.db.QueryResult
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SyncScheduleStore
import app.posato.feature.schedules.domain.LocalMinute
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.ui.FakeSessionMappings
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.bootstrap.AppleSyncTestHarness
import app.posato.feature.sync.bootstrap.scheduleLink
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.transfer.data.ImportWriter
import app.posato.feature.transfer.data.PauseSetTransfer
import app.posato.feature.transfer.data.TransferImport
import app.posato.feature.transfer.data.TransferRead
import app.posato.feature.transfer.domain.ImportMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * On the Mac every database thread has its own SQLite connection, and the schedule host writes as soon as a
 * schedule changes, such as one turned on whose window is open now. Turning on the imported schedules must not
 * leave some of them off when that other writer takes the database between them.
 */
class TurnOnContentionTest {
    @Test
    fun `given another writer that takes the database after each schedule change then every imported schedule turns on`() = runBlocking<Unit> {
        val harness = AppleSyncTestHarness(Dispatchers.IO, "turn-on-contention.db", withSchedules = true)
        var writer: Connection? = null
        try {
            val transfer = harness.transfer()
            val file = assertIs<TransferRead.File>(transfer.read(FILE.encodeToByteArray(), null)).file
            val imported = assertIs<TransferImport.Imported>(transfer.import(checkNotNull(transfer.preview(file, ImportMode.COMBINE))))
            val path = harness.databasePath()
            // The host's write, at once after the first change: the next write on another connection meets it.
            val host = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                harness.schedules.changes.first()
                writer = DriverManager.getConnection("jdbc:sqlite:$path").also { connection ->
                    connection.createStatement().use { statement -> statement.execute("BEGIN IMMEDIATE") }
                }
            }

            val turnedOn = transfer.turnOn(imported.schedules)

            host.cancel()
            assertTrue(turnedOn)
            assertEquals(listOf(true, true, true), harness.plans().map(SchedulePlan::enabled))
        } finally {
            writer?.let { connection ->
                connection.createStatement().use { statement -> statement.execute("ROLLBACK") }
                connection.close()
            }
            harness.close()
        }
    }

    @Test
    fun `given another writer holding the database for a moment when an import is confirmed then it is written`() = runBlocking<Unit> {
        val harness = AppleSyncTestHarness(Dispatchers.IO, "import-contention.db", withSchedules = true)
        try {
            val transfer = harness.transfer()
            val file = assertIs<TransferRead.File>(transfer.read(FILE.encodeToByteArray(), null)).file
            val preview = checkNotNull(transfer.preview(file, ImportMode.COMBINE))
            val writer = DriverManager.getConnection("jdbc:sqlite:${harness.databasePath()}")
            writer.createStatement().use { statement -> statement.execute("BEGIN IMMEDIATE") }
            // The other writer finishes soon after the import starts, as the schedule host's short writes do.
            val release = Thread {
                Thread.sleep(HOLD_MILLIS)
                writer.createStatement().use { statement -> statement.execute("ROLLBACK") }
                writer.close()
            }.apply { start() }

            val imported = transfer.import(preview)

            release.join()
            assertIs<TransferImport.Imported>(imported)
            assertEquals(3, harness.plans().size)
        } finally {
            harness.close()
        }
    }

    private fun AppleSyncTestHarness.transfer(): PauseSetTransfer {
        val scheduleStore = SyncScheduleStore(schedules, sync.scheduleLink())
        return PauseSetTransfer(
            policy = syncPolicy,
            schedules = scheduleStore,
            writer = ImportWriter(database, dispatcher, syncPolicy, scheduleStore, sync.scheduleLink()) { emptySet<PauseSetId>() to emptySet() },
            crypto = FakeSyncCryptoProvider(),
            applicationMappings = FakeSessionMappings(),
            zone = ContentionZone,
            now = { 0L },
            computation = dispatcher,
        )
    }

    private suspend fun AppleSyncTestHarness.plans(): List<SchedulePlan> {
        return withContext(Dispatchers.IO) {
            assertIs<ScheduleResult.Success<ScheduleSnapshot>>(schedules.read()).value.schedules.map { stored -> stored.plan }
        }
    }

    private fun AppleSyncTestHarness.databasePath(): String {
        return driver.executeQuery(null, "PRAGMA database_list", { cursor ->
            cursor.next()
            QueryResult.Value(checkNotNull(cursor.getString(2)))
        }, 0).value
    }

    private companion object {
        const val HOLD_MILLIS = 150L
        const val FILE = """{"format":"posato-pause-sets","version":1,"sets":[{"ref":"a","name":null,"first":true,"domains":[]}],""" +
            """"schedules":[{"name":"Late","weekdays":96,"start":1320,"end":1380,"set":"a"},""" +
            """{"name":"Deep work","weekdays":31,"start":540,"end":660,"set":"a"},""" +
            """{"name":"Mornings","weekdays":127,"start":420,"end":480,"set":"a"}]}"""
    }
}

private object ContentionZone : ScheduleZone {
    override fun localAt(epochMillis: Long): LocalMinute {
        return LocalMinute(
            ScheduleDate.ofEpochDay(epochMillis.floorDiv(CONTENTION_DAY_MILLIS)),
            (
                epochMillis.mod(CONTENTION_DAY_MILLIS) /
                    CONTENTION_MINUTE_MILLIS
            ).toInt(),
        )
    }

    override fun instantOf(
        date: ScheduleDate,
        minuteOfDay: Int,
    ): Long {
        return date.epochDay * CONTENTION_DAY_MILLIS + minuteOfDay * CONTENTION_MINUTE_MILLIS
    }
}

private const val CONTENTION_DAY_MILLIS: Long = 86_400_000L
private const val CONTENTION_MINUTE_MILLIS: Long = 60_000L
