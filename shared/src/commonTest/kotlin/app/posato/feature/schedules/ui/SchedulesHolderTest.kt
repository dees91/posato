package app.posato.feature.schedules.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.data.FakeScheduleSyncLink
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.data.SyncScheduleStore
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.domain.utc
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.bootstrap.BootstrapStoreFailure
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Sunday 2026-09-27 08:00 local (06:00 UTC, summer time). */
private val NOW = utc(ScheduleDate(2026, 9, 27), 6 * 60)

private val formatDays = object : SessionTimeFormat {
    override fun formatTime(
        epochMillis: Long,
        nowEpochMillis: Long,
    ): String {
        return CentralEuropeanZone.localAt(epochMillis).date.toString()
    }
}

private suspend fun TestScope.withHolder(
    name: String,
    link: FakeScheduleSyncLink? = null,
    block: suspend (SchedulesHolder, SqlScheduleStore, MutableList<Unit>) -> Unit,
) {
    val testDatabase = createLocalPolicyTestDatabase(name)
    val driver = testDatabase.openDriver()
    try {
        val store = SqlScheduleStore(PosatoDatabase(driver), StandardTestDispatcher(testScheduler))
        val saved = mutableListOf<Unit>()
        var next = 0
        val holder = SchedulesHolder(
            store = link?.let { SyncScheduleStore(store, it) } ?: store,
            zone = CentralEuropeanZone,
            clock = SessionClock { NOW },
            timeFormat = formatDays,
            ids = { ScheduleId("00000000000040008000${(++next).toString(16).padStart(12, '0')}") },
            scope = this,
            onSaved = { saved += Unit },
        )
        val collecting = launch { holder.run() }
        advanceUntilIdle()
        try {
            block(holder, store, saved)
        } finally {
            collecting.cancel()
        }
    } finally {
        driver.close()
        testDatabase.delete()
    }
}

class SchedulesHolderTest {
    @Test
    fun `given a draft the rules refuse when saved then the editor names the problem and nothing is stored`() = runTest {
        withHolder("holder-invalid.db") { holder, store, saved ->
            holder.openEditor(null)
            holder.updateDraft(ScheduleDraft(name = "Focus", days = persistentSetOf()))

            holder.save()
            advanceUntilIdle()

            assertEquals(ScheduleEditorError.NO_DAY, holder.state.editorError)
            assertEquals(emptyList(), saved)
            assertEquals(0, (store.read() as app.posato.feature.schedules.data.ScheduleResult.Success).value.schedules.size)
        }
    }

    @Test
    fun `given a name with a line break when saved then the editor refuses it like the wire would`() = runTest {
        withHolder("holder-name.db") { holder, _, _ ->
            holder.openEditor(null)
            holder.updateDraft(ScheduleDraft(name = "Two\nlines"))

            holder.save()

            assertEquals(ScheduleEditorError.NAME, holder.state.editorError)
        }
    }

    @Test
    fun `given a valid new schedule when saved then the row appears with its next run and notices are asked once`() = runTest {
        withHolder("holder-save.db") { holder, _, saved ->
            holder.openEditor(null)
            holder.updateDraft(ScheduleDraft(name = "Morning"))

            holder.save()
            advanceUntilIdle()

            assertNull(holder.state.editor)
            val row = holder.state.schedules.single()
            assertEquals("Morning", row.name)
            assertEquals("Next: Mon, ${ScheduleDate(2026, 9, 28)}", row.nextRunLabel)
            assertEquals(1, saved.size)
        }
    }

    @Test
    fun `given a schedule when its next occurrence is skipped then the next run moves and the skip is shown`() = runTest {
        withHolder("holder-skip.db") { holder, store, _ ->
            store.save(SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Weekdays", 0b0011111, 540, 600, true), null)
            advanceUntilIdle()
            holder.skipNext(holder.state.schedules.single())
            advanceUntilIdle()

            val row = holder.state.schedules.single()
            assertEquals("Next: Tue, ${ScheduleDate(2026, 9, 29)}", row.nextRunLabel)
            assertEquals("Skipped: Mon, ${ScheduleDate(2026, 9, 28)}", row.skippedLabel)
        }
    }

    @Test
    fun `given a delete request when not confirmed then nothing is deleted and when confirmed the row goes`() = runTest {
        withHolder("holder-delete.db") { holder, store, _ ->
            val id = ScheduleId("000000000000400080000000000000a1")
            store.save(SchedulePlan(id, "Weekdays", 0b0011111, 540, 600, true), null)
            advanceUntilIdle()

            holder.confirmDelete(id)
            holder.confirmDelete(null)
            holder.delete()
            advanceUntilIdle()
            assertEquals(1, holder.state.schedules.size)

            holder.confirmDelete(id)
            holder.delete()
            advanceUntilIdle()
            assertEquals(0, holder.state.schedules.size)
        }
    }

    @Test
    fun `given ten schedules when another is saved then the editor says the most is ten`() = runTest {
        withHolder("holder-cap.db") { holder, store, _ ->
            (1..10).forEach { value ->
                store.save(
                    SchedulePlan(ScheduleId("00000000000040008000${(100 + value).toString(16).padStart(12, '0')}"), "S$value", 1, 540, 600, true),
                    null,
                )
            }
            holder.openEditor(null)
            holder.updateDraft(ScheduleDraft(name = "Eleventh"))

            holder.save()
            advanceUntilIdle()

            assertEquals(ScheduleEditorError.FULL, holder.state.editorError)
            assertNotNull(holder.state.editor)
        }
    }

    @Test
    fun `given a change that cannot be saved when a row is turned off then it stays on and the list says so`() = runTest {
        val link = FakeScheduleSyncLink()
        withHolder("holder-failed.db", link) { holder, store, _ ->
            store.save(SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Weekdays", 0b0011111, 540, 600, true), null)
            advanceUntilIdle()
            link.captured = BootstrapStoreResult.Failure(BootstrapStoreFailure.STORAGE_FAILURE)

            holder.setEnabled(holder.state.schedules.single(), false)
            advanceUntilIdle()

            assertEquals(true, holder.state.changeFailed)
            assertEquals(true, holder.state.schedules.single().enabled)

            link.captured = BootstrapStoreResult.Success(null)
            holder.skipNext(holder.state.schedules.single())
            advanceUntilIdle()
            assertEquals(false, holder.state.changeFailed)
        }
    }
}
