package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.data.OccurrenceFact
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SequencedScheduleIntent
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleBreak
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.FakeSyncReplicaStore
import app.posato.feature.sync.domain.OpenSyncWriterResult
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.RemoteAcceptanceResult
import app.posato.feature.sync.domain.RemoteTransportReceipt
import app.posato.feature.sync.domain.ScheduleOccurrenceRef
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.domain.SynchronizedBreak
import app.posato.feature.sync.domain.remoteBundle
import app.posato.feature.sync.domain.snapshot
import app.posato.feature.sync.domain.transportKey
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.sync.testTransportProgress
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleSyncTest {
    private val today = ScheduleDate(2026, 9, 27)
    private val workspace = EstablishedWorkspace(testContext, bindingA)
    private val provider = FakeSyncCryptoProvider()

    private fun plan(
        value: Int,
        name: String = "Plan $value",
    ): SchedulePlan {
        return SchedulePlan(ScheduleSyncId(testIdentifier(value)).let { ScheduleId(it.hex) }, name, 0b0011111, 540, 600, true)
    }

    private suspend fun withSync(
        name: String,
        block: suspend (SqlScheduleStore, SyncWriter) -> Unit,
    ) {
        withSyncReplica(name) { store, writer, _ -> block(store, writer) }
    }

    private suspend fun withSyncReplica(
        name: String,
        block: suspend (SqlScheduleStore, SyncWriter, FakeSyncReplicaStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val store = SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default)
            val replica = FakeSyncReplicaStore(snapshot())
            val writer = assertIs<OpenSyncWriterResult.Success>(
                SyncOperationCore(replica, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
            ).writer
            block(store, writer, replica)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun SyncWriter.receive(vararg payloads: SyncOperationPayload) {
        val operations = listOf(testOperation(900, 1, SyncOperationPayload.AuthorRegister, author = 40)) +
            payloads.mapIndexed { index, payload -> testOperation(901 + index, index + 2L, payload, author = 40) }
        operations.forEachIndexed { index, operation ->
            assertIs<RemoteAcceptanceResult.Accepted>(
                acceptRemote(remoteBundle(provider, operation).copyBytes(), RemoteTransportReceipt(testTransportProgress(index.toByte()), false)),
            )
        }
    }

    private suspend fun SqlScheduleStore.shown(): ScheduleSnapshot {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(read()).value
    }

    private suspend fun SqlScheduleStore.pending(): List<SequencedScheduleIntent> {
        return assertIs<ScheduleResult.Success<List<SequencedScheduleIntent>>>(readIntents()).value
    }

    @Test
    fun `given a plan saved before linking when a pass runs then it is published once and stays shown`() = runTest {
        withSync("schedule-sync-seed.db") { store, writer ->
            store.save(plan(10), workspaceId = null)
            val sync = ScheduleSync(store) { today }

            assertEquals(ScheduleSyncResult.Done(refused = false), sync.pass(writer, workspace) { null })
            assertEquals(ScheduleSyncResult.Done(refused = false), sync.pass(writer, workspace) { null })

            assertEquals(listOf(plan(10).name), writer.projection().schedules.map { it.name })
            assertEquals(emptyList(), store.pending())
            assertEquals(listOf(plan(10)), store.shown().runnable)
        }
    }

    @Test
    fun `given a plan in another set when passes run then it is authored once as kind 17 and keeps its set`() = runTest {
        withSync("schedule-sync-set.db") { store, writer ->
            val inSet = plan(11).copy(setId = checkNotNull(PauseSetId.of(testIdentifier(80))))
            store.save(inSet, workspaceId = null)
            val sync = ScheduleSync(store) { today }

            sync.pass(writer, workspace) { null }
            val authoredAfterFirst = writer.projection().audit.size
            sync.pass(writer, workspace) { null }

            assertEquals(authoredAfterFirst, writer.projection().audit.size)
            assertEquals(listOf(inSet.setId), writer.projection().schedules.map { it.setId })
            assertEquals(listOf(inSet), store.shown().runnable)
        }
    }

    @Test
    fun `given a plan from another device when a pass runs then it is shown here`() = runTest {
        withSync("schedule-sync-remote.db") { store, writer ->
            val remote = plan(20, "From the iPhone")
            writer.receive(SyncOperationPayload.SchedulePut(ScheduleSyncId(testIdentifier(20)), remote.name, 0b0011111, 540, 600, true))

            ScheduleSync(store) { today }.pass(writer, workspace) { null }

            assertEquals(listOf(remote), store.shown().runnable)
        }
    }

    @Test
    fun `given a change the writer refuses as invalid when drained then it is discarded and the pass continues`() = runTest {
        withSync("schedule-sync-invalid.db") { store, writer ->
            store.save(plan(10), workspaceId = null)
            ScheduleSync(store) { today }.pass(writer, workspace) { null }
            store.recordFact(
                setOf(OccurrenceKey(plan(10).id, today.plusDays(500))),
                OccurrenceFact.Skip,
                today,
                testContext.workspaceId.value.copyBytes(),
            )

            val result = ScheduleSync(store) { today }.pass(writer, workspace) { null }

            assertEquals(ScheduleSyncResult.Done(refused = false), result)
            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `given the shared cap is full when this device adds a schedule then it is kept as refused and reported`() = runTest {
        withSync("schedule-sync-cap.db") { store, writer ->
            writer.receive(
                *(1..10).map { index ->
                    SyncOperationPayload.SchedulePut(ScheduleSyncId(testIdentifier(100 + index)), "Remote $index", 1, 540, 600, true)
                }.toTypedArray(),
            )
            ScheduleSync(store) { today }.pass(writer, workspace) { null }
            store.remove(plan(101).id, workspaceId = null)
            store.save(plan(30), testContext.workspaceId.value.copyBytes())

            val result = ScheduleSync(store) { today }.pass(writer, workspace) { null }

            assertEquals(ScheduleSyncResult.Done(refused = true), result)
            assertEquals(true, store.shown().schedules.single { it.plan.id == plan(30).id }.refused)
        }
    }

    @Test
    fun `given a publish failure after draining when a pass runs then it halts with that status`() = runTest {
        withSync("schedule-sync-halt.db") { store, writer ->
            store.save(plan(10), workspaceId = null)

            val result = ScheduleSync(store) { today }.pass(writer, workspace) { SyncStatus.RETRYABLE }

            assertEquals(ScheduleSyncResult.Halted(SyncStatus.RETRYABLE), result)
        }
    }

    @Test
    fun `given a change recorded for another workspace when drained then it is discarded without authoring`() = runTest {
        withSyncReplica("schedule-sync-other.db") { store, writer, replica ->
            ScheduleSync(store) { today }.pass(writer, workspace) { null }
            val published = replica.current.pendingBundles.size
            assertIs<ScheduleResult.Success<SchedulePlan>>(store.save(plan(40), ByteArray(16) { 7 }))

            val result = ScheduleSync(store) { today }.pass(writer, workspace) { null }

            assertEquals(ScheduleSyncResult.Done(refused = false), result)
            assertEquals(emptyList(), writer.projection().schedules.filter { it.name == plan(40).name })
            assertEquals(emptyList(), store.pending())
            assertEquals(published, replica.current.pendingBundles.size)
        }
    }

    @Test
    fun `given a change the shared plans already show when drained then no new operation is authored`() = runTest {
        withSyncReplica("schedule-sync-reflected.db") { store, writer, replica ->
            store.save(plan(10), workspaceId = null)
            ScheduleSync(store) { today }.pass(writer, workspace) { null }
            val published = replica.current.pendingBundles.size
            store.save(plan(10), testContext.workspaceId.value.copyBytes())

            ScheduleSync(store) { today }.pass(writer, workspace) { null }

            assertEquals(published, replica.current.pendingBundles.size)
            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `given a break pressed after the writer's clock when drained then it is authored clamped and the intent is consumed`() = runTest {
        // Failure mode: a clock set back between the press and the drain must neither freeze the writer nor keep the
        // intent pending forever; the local fact keeps the press instant.
        withSync("schedule-sync-break.db") { store, writer ->
            val focus = plan(30)
            store.save(focus, workspaceId = null)
            val sync = ScheduleSync(store) { today }
            sync.pass(writer, workspace) { null }
            val key = OccurrenceKey(focus.id, today)
            val ref = ScheduleOccurrenceRef(ScheduleSyncId(testIdentifier(30)), today)
            val workspaceId = testContext.workspaceId.value.copyBytes()
            store.recordFact(setOf(key), OccurrenceFact.Break(ScheduleBreak(5_000, 10)), today, workspaceId)
            store.recordFact(setOf(key), OccurrenceFact.BreakEnd, today, workspaceId)

            assertEquals(ScheduleSyncResult.Done(refused = false), sync.pass(writer, workspace) { null })

            assertEquals(emptyList(), store.pending())
            assertEquals(mapOf(ref to SynchronizedBreak(100, 10)), writer.projection().scheduleBreaks)
            assertEquals(setOf(ref), writer.projection().scheduleBreakEnds)
            assertEquals(mapOf(key to ScheduleBreak(100, 10)), store.shown().facts.breaks)
            assertEquals(setOf(key), store.shown().facts.breakEnds)
        }
    }

    @Test
    fun `given a break from another device when a pass runs then this device shows it with its end`() = runTest {
        withSync("schedule-sync-remote-break.db") { store, writer ->
            val focus = plan(31)
            val id = ScheduleSyncId(testIdentifier(31))
            val ref = ScheduleOccurrenceRef(id, today)
            writer.receive(
                SyncOperationPayload.SchedulePut(id, focus.name, 0b0011111, 540, 600, true),
                SyncOperationPayload.ScheduleBreak(ref, 2, 15),
                SyncOperationPayload.ScheduleBreakEnd(ref),
            )

            ScheduleSync(store) { today }.pass(writer, workspace) { null }

            assertEquals(mapOf(OccurrenceKey(focus.id, today) to ScheduleBreak(2, 15)), store.shown().facts.breaks)
            assertEquals(setOf(OccurrenceKey(focus.id, today)), store.shown().facts.breakEnds)
        }
    }

    @Test
    fun `given a pending break earlier than a received one when drained then the earlier break is published`() = runTest {
        // Failure mode: treating any shared break as this intent's would keep the earlier local break off every
        // other device, so devices release different windows after a completed sync.
        withSync("schedule-sync-earlier-break.db") { store, writer ->
            val focus = plan(32)
            val ref = ScheduleOccurrenceRef(ScheduleSyncId(testIdentifier(32)), today)
            val key = OccurrenceKey(focus.id, today)
            val sync = ScheduleSync(store) { today }
            store.save(focus, workspaceId = null)
            sync.pass(writer, workspace) { null }
            store.recordFact(setOf(key), OccurrenceFact.Break(ScheduleBreak(1, 5)), today, testContext.workspaceId.value.copyBytes())
            writer.receive(SyncOperationPayload.ScheduleBreak(ref, 2, 15))

            assertEquals(ScheduleSyncResult.Done(refused = false), sync.pass(writer, workspace) { null })

            assertEquals(mapOf(ref to SynchronizedBreak(1, 5)), writer.projection().scheduleBreaks)
            assertEquals(mapOf(key to ScheduleBreak(1, 5)), store.shown().facts.breaks)
            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `given a pending break later than a received one when drained then nothing is authored and the shared break shows`() = runTest {
        withSync("schedule-sync-later-break.db") { store, writer ->
            val focus = plan(33)
            val ref = ScheduleOccurrenceRef(ScheduleSyncId(testIdentifier(33)), today)
            val key = OccurrenceKey(focus.id, today)
            val sync = ScheduleSync(store) { today }
            store.save(focus, workspaceId = null)
            sync.pass(writer, workspace) { null }
            store.recordFact(setOf(key), OccurrenceFact.Break(ScheduleBreak(3, 5)), today, testContext.workspaceId.value.copyBytes())
            writer.receive(SyncOperationPayload.ScheduleBreak(ref, 2, 15))
            val auditBefore = writer.projection().audit.size

            assertEquals(ScheduleSyncResult.Done(refused = false), sync.pass(writer, workspace) { null })

            assertEquals(auditBefore, writer.projection().audit.size)
            assertEquals(mapOf(ref to SynchronizedBreak(2, 15)), writer.projection().scheduleBreaks)
            assertEquals(mapOf(key to ScheduleBreak(2, 15)), store.shown().facts.breaks)
            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `given a local break earlier than the workspace's when first linked then the local break is published`() = runTest {
        // Failure mode: seeding skipped every occurrence the workspace already holds a break for, whatever its start.
        withSync("schedule-sync-seed-break.db") { store, writer ->
            val focus = plan(34)
            val id = ScheduleSyncId(testIdentifier(34))
            val ref = ScheduleOccurrenceRef(id, today)
            val key = OccurrenceKey(focus.id, today)
            store.save(focus, workspaceId = null)
            store.recordFact(setOf(key), OccurrenceFact.Break(ScheduleBreak(1, 5)), today, null)
            writer.receive(
                SyncOperationPayload.SchedulePut(id, focus.name, 0b0011111, 540, 600, true),
                SyncOperationPayload.ScheduleBreak(ref, 2, 15),
            )

            assertEquals(ScheduleSyncResult.Done(refused = false), ScheduleSync(store) { today }.pass(writer, workspace) { null })

            assertEquals(mapOf(ref to SynchronizedBreak(1, 5)), writer.projection().scheduleBreaks)
            assertEquals(mapOf(key to ScheduleBreak(1, 5)), store.shown().facts.breaks)
            assertEquals(emptyList(), store.pending())
        }
    }
}
