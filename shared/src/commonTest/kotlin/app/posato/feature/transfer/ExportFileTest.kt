package app.posato.feature.transfer

import app.posato.feature.schedules.data.StoredSchedule
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.transfer.domain.FileSchedule
import app.posato.feature.transfer.domain.FileSet
import app.posato.feature.transfer.domain.exportFile
import kotlin.test.Test
import kotlin.test.assertEquals

/** An export carries only what is live here, so a set or schedule the workspace refused never travels. */
class ExportFileTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(10)))
    private val waiting = checkNotNull(PauseSetId.of(testIdentifier(11)))

    @Test
    fun `given live and refused sets and schedules when exported then only live ones travel with their default`() {
        val sets = checkNotNull(
            PauseSets.of(
                listOf(
                    LocalPauseSet(PauseSetId.FIRST, null, domains("example.com")),
                    LocalPauseSet(work, "Work", domains("example.net")),
                    LocalPauseSet(waiting, "Waiting", emptyList(), refused = true),
                ),
                work,
            ),
        )
        val schedules = listOf(
            StoredSchedule(SchedulePlan(ScheduleId("01"), "Deep work", 0b11111, 540, 660, enabled = true, setId = work)),
            StoredSchedule(SchedulePlan(ScheduleId("02"), "Refused", 0b1, 0, 60, enabled = true), refused = true),
            StoredSchedule(SchedulePlan(ScheduleId("03"), "On waiting", 0b1, 0, 60, enabled = true, setId = waiting)),
        )

        val file = exportFile(sets, schedules)

        assertEquals(
            listOf(FileSet("s1", null, true, domains("example.com")), FileSet("s2", "Work", false, domains("example.net"))),
            file.sets,
        )
        assertEquals("s2", file.defaultSet)
        assertEquals(listOf(FileSchedule("Deep work", 0b11111, 540, 660, "s2")), file.schedules)
    }
}
