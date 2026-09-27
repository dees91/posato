package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleSnapshot
import app.posato.feature.schedules.data.SequencedScheduleIntent
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.OccurrenceKey
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.FakeSyncReplicaStore
import app.posato.feature.sync.domain.OpenSyncWriterResult
import app.posato.feature.sync.domain.RemoteAcceptanceResult
import app.posato.feature.sync.domain.RemoteTransportReceipt
import app.posato.feature.sync.domain.ScheduleSyncId
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.domain.SyncWriter
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
            store.stop(
                setOf(OccurrenceKey(plan(10).id, today.plusDays(500))),
                app.posato.feature.schedules.data.OccurrenceStop.SKIP,
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
}
