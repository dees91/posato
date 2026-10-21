package app.posato.feature.transfer

import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.transfer.domain.FileSchedule
import app.posato.feature.transfer.domain.FileSet
import app.posato.feature.transfer.domain.ImportCurrent
import app.posato.feature.transfer.domain.ImportIds
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.ImportPlan
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.ImportRefusal
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.feature.transfer.domain.planImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Import is a one-time copy that may add to or replace what a person keeps. Each rule here guards a way an
 * import could lose a choice the person made, take a set a pause is using, or break a limit.
 */
class ImportPlannerTest {
    private val work = setId(10)
    private val evening = setId(11)
    private val reading = setId(12)
    private val newOne = setId(20)
    private val newTwo = setId(21)
    private val newThree = setId(22)

    @Test
    fun `given combine when a file set shares a name then it gains the missing websites and keeps its own`() {
        val plan = planned(
            current(sets(first(name = "Focus", "a.example"), set(work, "Work", "b.example"))),
            file(fileSet("x", "work ", "b.example", "c.example")),
            ImportMode.COMBINE,
        )

        assertEquals(listOf("b.example", "c.example"), plan.sets.domainsOf(work).values())
        assertEquals(2, plan.sets.sets.size)
        assertEquals(listOf("Work"), plan.summary.combined)
    }

    @Test
    fun `given combine then the file's first set merges into this device's first set and the local name stays`() {
        val plan = planned(
            current(sets(first(name = "Focus", "a.example"))),
            file(fileSet("x", "Deep", "b.example", first = true)),
            ImportMode.COMBINE,
        )

        val firstSet = plan.sets.sets.single()
        assertEquals("Focus", firstSet.name)
        assertEquals(listOf("a.example", "b.example"), firstSet.domains.values())
    }

    @Test
    fun `given combine on a fresh install then an unnamed file first set fills the empty first set`() {
        val plan = planned(current(sets(first(name = null))), file(fileSet("x", null, "a.example", first = true)), ImportMode.COMBINE)

        assertEquals(listOf(LocalPauseSet(PauseSetId.FIRST, null, domains("a.example"))), plan.sets.sets)
    }

    @Test
    fun `given combine when a file set is named like this device's first set then it is new and marked imported`() {
        val plan = planned(
            current(sets(first(name = "Focus"))),
            file(fileSet("x", "Focus", "a.example")),
            ImportMode.COMBINE,
        )

        assertEquals("Focus (imported)", plan.sets.sets.single { set -> set.id == newOne }.name)
    }

    @Test
    fun `given combine when this device's first set is refused then an unnamed file first set is a new set named My set`() {
        val plan = planned(
            current(sets(first(name = null, refused = true), set(work, "My set"))),
            file(fileSet("x", null, "a.example", first = true)),
            ImportMode.COMBINE,
        )

        assertEquals("My set (imported)", plan.sets.sets.single { set -> set.id == newOne }.name)
    }

    @Test
    fun `given add then this device's default stays`() {
        val current = current(sets(first(name = null), set(work, "Work"), defaultSet = work))
        val file = file(fileSet("x", "Evening"), defaultSet = "x")

        ImportMode.entries.filter { mode -> mode != ImportMode.REPLACE }.forEach { mode ->
            assertEquals(work, planned(current, file, mode).sets.defaultSetId)
        }
    }

    @Test
    fun `given keep separate then every file set is new and clashing names are marked imported`() {
        val plan = planned(
            current(sets(first(name = "Focus"), set(work, "Work"))),
            file(fileSet("x", "Work", "a.example"), fileSet("y", null, "b.example", first = true)),
            ImportMode.SEPARATE,
        )

        assertEquals(setOf("Focus", "Work", "Work (imported)", "My set"), plan.sets.sets.map { set -> set.name }.toSet())
        assertEquals(listOf(), plan.sets.domainsOf(work).values())
    }

    @Test
    fun `given replace with a named first set then it lands on this device's first set with the file's name and apps stay`() {
        val plan = planned(
            current(sets(first(name = "Focus", "a.example"), set(work, "Work"))),
            file(fileSet("x", "Deep", "b.example", first = true)),
            ImportMode.REPLACE,
        )

        assertEquals(listOf(LocalPauseSet(PauseSetId.FIRST, "Deep", domains("b.example"))), plan.sets.sets)
        assertEquals(setOf(PauseSetId.FIRST), plan.keptChoices)
        assertTrue(plan.summary.firstSetAppsStay)
        assertEquals(listOf("Work"), plan.summary.removed)
    }

    @Test
    fun `given replace then the summary names the sets it overwrites and removes and the schedules it removes`() {
        val existing = SchedulePlan(scheduleId(9), "Deep work", 0b1, 0, 60, enabled = true)
        val plan = planned(
            current(sets(first(name = "Focus"), set(evening, "Evening", "a.example")), schedules = listOf(existing)),
            file(fileSet("x", "Reading", "b.example", first = true), fileSet("y", "Night", "c.example")),
            ImportMode.REPLACE,
        )

        assertEquals(listOf("Reading", "Night"), plan.summary.changed)
        assertEquals("Focus", plan.summary.firstSetWas)
        assertEquals(listOf("Evening"), plan.summary.removed)
        assertEquals(listOf("Deep work"), plan.summary.removedSchedules)
    }

    @Test
    fun `given replace with an unnamed first set then this device's first-set name is kept`() {
        val plan = planned(
            current(sets(first(name = "Focus", "a.example"))),
            file(fileSet("x", null, "b.example", first = true)),
            ImportMode.REPLACE,
        )

        assertEquals("Focus", plan.sets.sets.single().name)
        assertEquals("Focus", plan.summary.firstSetName)
    }

    @Test
    fun `given replace and a file without a first set then this device's first set is kept emptied with its name`() {
        val plan = planned(
            current(sets(first(name = "Focus", "a.example"), set(work, "Work"))),
            file(fileSet("x", "Evening", "b.example")),
            ImportMode.REPLACE,
        )

        assertEquals(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList()), plan.sets.sets.single { set -> set.id == PauseSetId.FIRST })
        assertEquals(setOf(PauseSetId.FIRST, newOne), plan.sets.sets.map(LocalPauseSet::id).toSet())
    }

    @Test
    fun `given replace when this device's first set is refused then it is removed and the file's first set is new`() {
        val plan = planned(
            current(sets(first(name = null, refused = true), set(work, "Work"))),
            file(fileSet("x", "Deep", "b.example", first = true)),
            ImportMode.REPLACE,
        )

        assertEquals(listOf(LocalPauseSet(newOne, "Deep", domains("b.example"))), plan.sets.sets)
        assertFalse(plan.summary.firstSetAppsStay)
    }

    @Test
    fun `given replace then refused sets go too and the file's default is taken`() {
        val plan = planned(
            current(sets(first(name = null), set(work, "Work", refused = true))),
            file(fileSet("x", null, first = true), fileSet("y", "Evening"), defaultSet = "y"),
            ImportMode.REPLACE,
        )

        assertEquals(setOf(PauseSetId.FIRST, newTwo), plan.sets.sets.map(LocalPauseSet::id).toSet())
        assertEquals(newTwo, plan.sets.defaultSetId)
    }

    @Test
    fun `given imported schedules then they arrive switched off and point at the imported sets`() {
        val plan = planned(
            current(sets(first(name = null))),
            file(fileSet("x", "Evening"), schedules = listOf(FileSchedule("Deep work", 0b11111, 540, 660, "x"))),
            ImportMode.COMBINE,
        )

        val saved = plan.schedulesToSave.single()
        assertFalse(saved.enabled)
        assertEquals(newOne, saved.setId)
        assertEquals(scheduleId(1), saved.id)
    }

    @Test
    fun `given add with a schedule already here then the duplicate is skipped`() {
        val existing = SchedulePlan(scheduleId(9), "Deep work", 0b11111, 540, 660, enabled = true, setId = work)
        val plan = planned(
            current(sets(first(name = null), set(work, "Work")), schedules = listOf(existing)),
            file(fileSet("x", "Work"), schedules = listOf(FileSchedule("Deep work", 0b11111, 540, 660, "x"))),
            ImportMode.COMBINE,
        )

        assertEquals(emptyList(), plan.schedulesToSave)
    }

    @Test
    fun `given replace then every schedule here is removed before the file's are saved`() {
        val existing = SchedulePlan(scheduleId(9), "Old", 0b1, 0, 60, enabled = true)
        val plan = planned(
            current(sets(first(name = null)), schedules = listOf(existing)),
            file(fileSet("x", null, first = true), schedules = listOf(FileSchedule("New", 0b1, 0, 60, "x"))),
            ImportMode.REPLACE,
        )

        assertEquals(listOf(scheduleId(9)), plan.schedulesToRemove)
        assertEquals(listOf("New"), plan.schedulesToSave.map(SchedulePlan::name))
    }

    @Test
    fun `given add beyond ten sets then the import is refused`() {
        val many = sets(first(name = null), *(1..9).map { index -> set(setId(30 + index), "Set $index") }.toTypedArray())

        assertEquals(
            ImportPlanResult.Refused(ImportRefusal.TOO_MANY_SETS),
            file(fileSet("x", "Extra")).let { file -> planImport(current(many), file, ImportMode.SEPARATE, ids(file)) },
        )
    }

    @Test
    fun `given add beyond the unique website limit then the import is refused`() {
        val full = sets(first(name = null, domains = (0 until 1_000).map(::longestDomain)))
        val more = FileSet("x", "More", first = false, domains = (1_000 until 1_030).map(::longestDomain))

        assertEquals(
            ImportPlanResult.Refused(ImportRefusal.TOO_MANY_WEBSITES),
            PauseSetFile(listOf(more), null, emptyList()).let { file -> planImport(current(full), file, ImportMode.COMBINE, ids(file)) },
        )
    }

    @Test
    fun `given add beyond ten schedules then the import is refused`() {
        val existing = (1..10).map { index -> SchedulePlan(scheduleId(40 + index), "S$index", 0b1, 0, 60, enabled = false) }

        assertEquals(
            ImportPlanResult.Refused(ImportRefusal.TOO_MANY_SCHEDULES),
            planImport(
                current(sets(first(name = null)), schedules = existing),
                file(fileSet("x", null, first = true), schedules = listOf(FileSchedule("New", 0b1, 0, 60, "x"))),
                ImportMode.COMBINE,
                ids(),
            ),
        )
    }

    @Test
    fun `given replace while a pause uses a set it would remove then it is refused`() {
        assertEquals(
            ImportPlanResult.Refused(ImportRefusal.IN_USE_BY_PAUSE),
            planImport(
                current(sets(first(name = null), set(work, "Work")), running = setOf(work)),
                file(fileSet("x", null, first = true)),
                ImportMode.REPLACE,
                ids(),
            ),
        )
    }

    @Test
    fun `given replace while a scheduled pause of a schedule it would remove runs then it is refused`() {
        val running = SchedulePlan(scheduleId(9), "Deep work", 0b1, 0, 60, enabled = true)

        assertEquals(
            ImportPlanResult.Refused(ImportRefusal.IN_USE_BY_PAUSE),
            planImport(
                current(
                    sets(first(name = null)),
                    schedules = listOf(running),
                    running = setOf(PauseSetId.FIRST),
                    runningSchedules = setOf(running.id),
                ),
                file(fileSet("x", null, first = true)),
                ImportMode.REPLACE,
                ids(),
            ),
        )
    }

    @Test
    fun `given a pause on this device's first set then replace that keeps it and add both go ahead`() {
        val current = current(sets(first(name = null, "a.example")), running = setOf(PauseSetId.FIRST))
        val file = file(fileSet("x", null, "b.example", first = true))

        ImportMode.entries.forEach { mode -> assertIs<ImportPlanResult.Planned>(planImport(current, file, mode, ids(file))) }
    }

    private fun planned(
        current: ImportCurrent,
        file: PauseSetFile,
        mode: ImportMode,
    ): ImportPlan {
        return assertIs<ImportPlanResult.Planned>(planImport(current, file, mode, ids(file))).plan
    }

    private fun ids(file: PauseSetFile? = null): ImportIds {
        val refs = file?.sets?.map(FileSet::ref).orEmpty()
        val pool = listOf(newOne, newTwo, newThree) + (60..80).map(::setId)
        return ImportIds(refs.mapIndexed { index, ref -> ref to pool[index] }.toMap(), (1..10).map(::scheduleId))
    }
}

private fun setId(value: Int): PauseSetId {
    return checkNotNull(PauseSetId.of(testIdentifier(value)))
}

private fun scheduleId(value: Int): ScheduleId {
    return ScheduleId(value.toString(16).padStart(32, '0'))
}

private fun first(
    name: String?,
    vararg domains: String,
    refused: Boolean = false,
): LocalPauseSet {
    return LocalPauseSet(PauseSetId.FIRST, name, domains(*domains), refused)
}

private fun first(
    name: String?,
    domains: List<ExactDomain>,
): LocalPauseSet {
    return LocalPauseSet(PauseSetId.FIRST, name, domains)
}

private fun set(
    id: PauseSetId,
    name: String,
    vararg domains: String,
    refused: Boolean = false,
): LocalPauseSet {
    return LocalPauseSet(id, name, domains(*domains), refused)
}

private fun sets(
    vararg sets: LocalPauseSet,
    defaultSet: PauseSetId? = null,
): PauseSets {
    return checkNotNull(PauseSets.of(sets.toList(), defaultSet))
}

private fun current(
    sets: PauseSets,
    schedules: List<SchedulePlan> = emptyList(),
    running: Set<PauseSetId> = emptySet(),
    runningSchedules: Set<ScheduleId> = emptySet(),
): ImportCurrent {
    return ImportCurrent(sets, schedules, running, runningSchedules)
}

private fun fileSet(
    ref: String,
    name: String?,
    vararg domains: String,
    first: Boolean = false,
): FileSet {
    return FileSet(ref, name, first, domains(*domains))
}

private fun file(
    vararg sets: FileSet,
    defaultSet: String? = null,
    schedules: List<FileSchedule> = emptyList(),
): PauseSetFile {
    return PauseSetFile(sets.toList(), defaultSet, schedules)
}

private fun List<ExactDomain>.values(): List<String> {
    return map(ExactDomain::canonicalValue)
}
