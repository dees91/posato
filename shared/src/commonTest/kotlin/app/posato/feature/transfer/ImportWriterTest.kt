package app.posato.feature.transfer

import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.ScheduleSyncLink
import app.posato.feature.schedules.data.SyncScheduleStore
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.bootstrap.AppleSyncTestHarness
import app.posato.feature.sync.bootstrap.BootstrapStoreFailure
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.sync.bootstrap.SharedFakeMailboxPort
import app.posato.feature.sync.bootstrap.scheduleLink
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.transfer.data.ImportWriteResult
import app.posato.feature.transfer.data.ImportWriter
import app.posato.feature.transfer.domain.FileSchedule
import app.posato.feature.transfer.domain.FileSet
import app.posato.feature.transfer.domain.ImportIds
import app.posato.feature.transfer.domain.ImportMode
import app.posato.feature.transfer.domain.ImportPlanResult
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.feature.transfer.domain.planImport
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * An import writes sets, schedules, and their sync intents at once, so a linked device's other members end
 * up with exactly what the importing device shows, and a state that changed since the preview writes nothing.
 */
class ImportWriterTest {
    private val work = setId(10)
    private val evening = setId(11)
    private val imported = setId(20)
    private val importedTwo = setId(21)

    @Test
    fun `given a linked pair when one device combines a file then the other ends with the same sets default and schedules`() = runTest {
        val (importer, peer) = linkedPair("import-combine")
        try {
            link(importer, peer)
            val file = PauseSetFile(
                listOf(FileSet("x", "Evening", false, domains("example.net")), FileSet("y", null, true, domains("example.com"))),
                null,
                listOf(FileSchedule("Deep work", 0b11111, 540, 660, "x")),
            )

            assertIs<ImportWriteResult.Written>(importer.import(file, ImportMode.COMBINE))
            exchange(importer, peer)

            assertConverged(importer, peer)
            assertEquals(setOf(PauseSetId.FIRST, imported), peer.sets().sets.map(LocalPauseSet::id).toSet())
            assertEquals(listOf("Deep work"), peer.plans().map(SchedulePlan::name))
            assertTrue(peer.plans().none(SchedulePlan::enabled))
        } finally {
            importer.close()
            peer.close()
        }
    }

    @Test
    fun `given a linked pair when one device replaces with a removed set and a new default then the other converges`() = runTest {
        val (importer, peer) = linkedPair("import-replace")
        try {
            link(importer, peer)
            importer.replaceLocalViaSync(
                checkNotNull(
                    PauseSets.of(
                        listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList()), LocalPauseSet(work, "Work", domains("example.org"))),
                        work,
                    ),
                ),
            )
            assertIs<ScheduleResult.Success<*>>(
                importer.scheduleStore().save(SchedulePlan(scheduleId(9), "Old", 0b1, 0, 60, enabled = true, setId = work), null),
            )
            exchange(importer, peer)
            val file = PauseSetFile(
                listOf(FileSet("x", null, true, domains("example.com")), FileSet("y", "Evening", false, domains("example.net"))),
                "y",
                listOf(FileSchedule("Evening reading", 0b1111111, 1200, 1260, "y")),
            )

            assertIs<ImportWriteResult.Written>(importer.import(file, ImportMode.REPLACE))
            exchange(importer, peer)

            assertConverged(importer, peer)
            assertEquals(setOf(PauseSetId.FIRST, importedTwo), peer.sets().sets.filterNot(LocalPauseSet::refused).map(LocalPauseSet::id).toSet())
            assertEquals(importedTwo, peer.sets().resolvedDefault())
            assertEquals("Focus", peer.sets().sets.single { set -> set.id == PauseSetId.FIRST }.name)
            assertEquals(listOf("Evening reading"), peer.plans().map(SchedulePlan::name))
        } finally {
            importer.close()
            peer.close()
        }
    }

    @Test
    fun `given a peer that fills the set cap while a device imports a new set then both agree which ten sets are live`() = runTest {
        val (importer, peer) = linkedPair("import-cap")
        try {
            link(importer, peer)
            peer.replaceLocalViaSync(
                checkNotNull(
                    PauseSets.of(
                        listOf(LocalPauseSet(PauseSetId.FIRST, null, emptyList())) + (1..9).map {
                            LocalPauseSet(setId(40 + it), "Peer $it", emptyList())
                        },
                        null,
                    ),
                ),
            )
            peer.sync.syncNow()
            advanceUntilIdle()
            // The importer previews before it hears of the peer's nine new sets.
            assertIs<ImportWriteResult.Written>(
                importer.import(PauseSetFile(listOf(FileSet("x", "Mine", false, emptyList())), null, emptyList()), ImportMode.SEPARATE),
            )
            exchange(importer, peer)

            val live = importer.sets().sets.filterNot(LocalPauseSet::refused).map(LocalPauseSet::id).toSet()
            assertEquals(live, peer.sets().sets.filterNot(LocalPauseSet::refused).map(LocalPauseSet::id).toSet())
            assertEquals(10, live.size)
            // The importer's clock is earlier, so its set takes the last slot and the peer's last set waits.
            assertTrue(imported in live)
        } finally {
            importer.close()
            peer.close()
        }
    }

    @Test
    fun `given sets that changed after the preview when confirmed then nothing is written and the new plan is returned`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), "import-changed.db", withSchedules = true)
        try {
            val file = PauseSetFile(listOf(FileSet("x", "Evening", false, domains("example.net"))), null, emptyList())
            val ids = ImportIds(mapOf("x" to imported), emptyList())
            val preview = assertIs<ImportPlanResult.Planned>(planImport(harness.current(), file, ImportMode.COMBINE, ids)).plan
            harness.replaceLocal(
                checkNotNull(
                    PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(evening, "Evening", emptyList())), null),
                ),
            )
            val before = harness.sets()

            val result = harness.writer().apply(preview) { current -> planImport(current, file, ImportMode.COMBINE, ids) }

            assertIs<ImportWriteResult.Changed>(result)
            assertEquals(before, harness.sets())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given the workspace cannot be read when an import is confirmed then it is refused and nothing is written`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), "import-unknown.db", withSchedules = true)
        try {
            val file = PauseSetFile(listOf(FileSet("x", "Evening", false, emptyList())), null, emptyList())
            val ids = ImportIds(mapOf("x" to imported), emptyList())
            val preview = assertIs<ImportPlanResult.Planned>(planImport(harness.current(), file, ImportMode.COMBINE, ids)).plan
            val failing = object : ScheduleSyncLink {
                override suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
                    return BootstrapStoreResult.Failure(BootstrapStoreFailure.STORAGE_FAILURE)
                }

                override fun syncNow() = Unit
            }

            val result = harness.writer(failing).apply(preview) { current -> planImport(current, file, ImportMode.COMBINE, ids) }

            assertEquals(ImportWriteResult.WorkspaceUnknown, result)
            assertEquals(listOf(PauseSetId.FIRST), harness.sets().sets.map(LocalPauseSet::id))
        } finally {
            harness.close()
        }
    }

    private fun TestScope.linkedPair(name: String): Pair<AppleSyncTestHarness, AppleSyncTestHarness> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        return AppleSyncTestHarness(
            dispatcher,
            "$name-a.db",
            mailboxPort = mailbox,
            wallClock = tickingClock(1_000),
            cryptoProvider = FakeSyncCryptoProvider(random = Random(1)),
            withSchedules = true,
        ) to
            AppleSyncTestHarness(
                dispatcher,
                "$name-b.db",
                mailboxPort = mailbox,
                wallClock = tickingClock(2_000),
                cryptoProvider = FakeSyncCryptoProvider(random = Random(2)),
                withSchedules = true,
            )
    }

    private suspend fun TestScope.link(
        first: AppleSyncTestHarness,
        second: AppleSyncTestHarness,
    ) {
        first.establish()
        second.establish()
        exchange(first, second)
    }

    private suspend fun TestScope.exchange(
        first: AppleSyncTestHarness,
        second: AppleSyncTestHarness,
    ) {
        repeat(5) {
            first.sync.onForeground()
            advanceUntilIdle()
            second.sync.onForeground()
            advanceUntilIdle()
        }
    }

    private suspend fun assertConverged(
        first: AppleSyncTestHarness,
        second: AppleSyncTestHarness,
    ) {
        assertEquals(first.sets(), second.sets())
        assertEquals(first.plans().toSet(), second.plans().toSet())
    }

    private suspend fun AppleSyncTestHarness.import(
        file: PauseSetFile,
        mode: ImportMode,
    ): ImportWriteResult {
        val ids = ImportIds(
            file.sets.mapIndexed {
                index,
                set
                ->
                set.ref to listOf(imported, importedTwo)[index]
            }.toMap(),
            file.schedules.indices.map { scheduleId(30 + it) },
        )
        val preview = assertIs<ImportPlanResult.Planned>(planImport(current(), file, mode, ids)).plan
        return writer().apply(preview) { current -> planImport(current, file, mode, ids) }
    }
}

private fun AppleSyncTestHarness.scheduleStore(): SyncScheduleStore {
    return SyncScheduleStore(schedules, sync.scheduleLink())
}

private fun AppleSyncTestHarness.writer(link: ScheduleSyncLink = sync.scheduleLink()): ImportWriter {
    return ImportWriter(database, dispatcher, syncPolicy, SyncScheduleStore(schedules, link), link) { setOf<PauseSetId>() to setOf<ScheduleId>() }
}

private suspend fun AppleSyncTestHarness.current() = checkNotNull(writer().current())

private suspend fun AppleSyncTestHarness.sets(): PauseSets {
    return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value.sets
}

private suspend fun AppleSyncTestHarness.plans(): List<SchedulePlan> {
    return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(schedules.read()).value.schedules.map { stored -> stored.plan }
}

private suspend fun AppleSyncTestHarness.replaceLocal(sets: PauseSets) {
    val state = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value
    assertIs<LocalPolicyResult.Success<*>>(sqlPolicy.replaceSets(state.revision, sets))
}

private suspend fun AppleSyncTestHarness.replaceLocalViaSync(sets: PauseSets) {
    val state = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value
    assertIs<LocalPolicyResult.Success<*>>(syncPolicy.replaceSets(state.revision, sets))
}

private fun setId(value: Int): PauseSetId {
    return checkNotNull(PauseSetId.of(testIdentifier(value)))
}

private fun scheduleId(value: Int): ScheduleId {
    return ScheduleId(ScheduleSyncId(testIdentifier(value)).hex)
}

/** A clock that moves on with every reading, as a device's does between passes. */
private fun tickingClock(start: Long): SyncWallClock {
    var now = start
    return SyncWallClock { now.also { now += 10 } }
}
