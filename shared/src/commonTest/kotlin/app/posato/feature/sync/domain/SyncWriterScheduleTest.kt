package app.posato.feature.sync.domain

import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.ImmutableBytes
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.sync.testTransportProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncWriterScheduleTest {
    private val scheduleId = ScheduleSyncId(testIdentifier(70))
    private val today = ScheduleDate(2026, 9, 27)

    private suspend fun openWriter(store: FakeSyncReplicaStore): SyncWriter {
        return assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, FakeSyncCryptoProvider(), SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
    }

    @Test
    fun `given invalid schedule mutations when mutated then each is refused and the writer stays usable`() = runTest {
        listOf(
            LocalSyncMutation.PutSchedule(scheduleId, "   ", 1, 540, 720, true, PauseSetId.FIRST),
            LocalSyncMutation.PutSchedule(scheduleId, "Focus", 0, 540, 720, true, PauseSetId.FIRST),
            LocalSyncMutation.PutSchedule(scheduleId, "Focus", 1, 540, 545, true, PauseSetId.FIRST),
            LocalSyncMutation.PutSchedule(scheduleId, "Focus\u0000", 1, 540, 720, true, PauseSetId.FIRST),
            LocalSyncMutation.SkipOccurrence(ScheduleOccurrenceRef(scheduleId, today.plusDays(401)), today),
            LocalSyncMutation.EndOccurrence(ScheduleOccurrenceRef(scheduleId, ScheduleDate(2023, 2, 29)), today),
            LocalSyncMutation.RemoveSchedule(ScheduleSyncId(checkNotNull(SyncIdentifier.fromExactBytes(ByteArray(16) { 1 })))),
        ).forEach { invalid ->
            val store = FakeSyncReplicaStore(snapshot())
            val writer = openWriter(store)
            val before = store.current

            assertEquals(LocalMutationFailure.INVALID_MUTATION, assertIs<LocalMutationResult.Failure>(writer.mutate(invalid)).reason)
            assertEquals(before, store.current)
            assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.EndSession(SessionId(testIdentifier(77)))))
        }
    }

    @Test
    fun `given a skip exactly four hundred days ahead when mutated then it is authored`() = runTest {
        val store = FakeSyncReplicaStore(snapshot())

        val result = openWriter(store).mutate(LocalSyncMutation.SkipOccurrence(ScheduleOccurrenceRef(scheduleId, today.plusDays(400)), today))

        assertIs<LocalMutationResult.Success>(result)
    }

    @Test
    fun `given a decomposed and padded name when authored then the writer stores it trimmed and in NFC`() = runTest {
        val store = FakeSyncReplicaStore(snapshot())

        assertIs<LocalMutationResult.Success>(
            openWriter(store).mutate(LocalSyncMutation.PutSchedule(scheduleId, "  Café ", 1, 540, 720, true, PauseSetId.FIRST)),
        )

        val authored = store.current.acceptedBundles.values.map { it.operation.payload }.filterIsInstance<SyncOperationPayload.SchedulePut>()
        assertEquals(listOf("Caf\u00e9"), authored.map { it.name })
    }

    @Test
    fun `given an optional operation from a known author when accepted then it is kept and survives a reopen`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val store = FakeSyncReplicaStore(snapshot())
        val writer = openWriter(store)
        val registration = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
        val optional = testOperation(2, 2, SyncOperationPayload.OptionalExtension(200, ImmutableBytes(byteArrayOf(5, 6, 7))))
        val before = writer.projection()

        assertIs<RemoteAcceptanceResult.Accepted>(
            writer.acceptRemote(remoteBundle(provider, registration).copyBytes(), RemoteTransportReceipt(testTransportProgress(1), false)),
        )
        assertIs<RemoteAcceptanceResult.Accepted>(
            writer.acceptRemote(remoteBundle(provider, optional).copyBytes(), RemoteTransportReceipt(testTransportProgress(2), false)),
        )

        assertEquals(before.pauseSetDomains(PauseSetId.FIRST), writer.projection().pauseSetDomains(PauseSetId.FIRST))
        assertEquals(before.schedules, writer.projection().schedules)
        assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(FakeSyncReplicaStore(acceptedSnapshot(provider, listOf(registration, optional))), provider, SyncWallClock { 100 })
                .open(testContext, transportKey()),
        )
    }

    @Test
    fun `given a break pressed later than the writer's clock when authored then its start is clamped and the writer stays usable`() = runTest {
        // Failure mode: a clock set back between the press and the drain would make the authored start later than
        // its own HLC, which every decoder rejects; refusing instead would freeze the writer and lose the break.
        val store = FakeSyncReplicaStore(snapshot())
        val writer = openWriter(store)
        val ref = ScheduleOccurrenceRef(scheduleId, today)

        val result = writer.mutate(LocalSyncMutation.BreakOccurrence(ref, today, startEpochMillis = 5_000, minutes = 10))

        assertIs<LocalMutationResult.Success>(result)
        val authored = store.current.acceptedBundles.values.map { it.operation }.single { it.payload is SyncOperationPayload.ScheduleBreak }
        val payload = authored.payload as SyncOperationPayload.ScheduleBreak
        assertEquals(authored.clock.physicalMillis, payload.startEpochMillis)
        assertEquals(100, payload.startEpochMillis)
        assertEquals(mapOf(ref to SynchronizedBreak(100, 10)), writer.projection().scheduleBreaks)
        assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.EndOccurrenceBreak(ref, today)))
        assertEquals(setOf(ref), writer.projection().scheduleBreakEnds)
    }

    @Test
    fun `given a break pressed before the writer's clock when authored then the press instant is kept`() = runTest {
        val store = FakeSyncReplicaStore(snapshot())
        val ref = ScheduleOccurrenceRef(scheduleId, today)

        assertIs<LocalMutationResult.Success>(openWriter(store).mutate(LocalSyncMutation.BreakOccurrence(ref, today, 40, 5)))

        val payload = store.current.acceptedBundles.values.map { it.operation.payload }.filterIsInstance<SyncOperationPayload.ScheduleBreak>()
        assertEquals(listOf(40L), payload.map { it.startEpochMillis })
    }

    @Test
    fun `given invalid break mutations when mutated then each is refused and the writer stays usable`() = runTest {
        listOf(
            LocalSyncMutation.BreakOccurrence(ScheduleOccurrenceRef(scheduleId, today), today, 50, 7),
            LocalSyncMutation.BreakOccurrence(ScheduleOccurrenceRef(scheduleId, today.plusDays(401)), today, 50, 5),
            LocalSyncMutation.BreakOccurrence(ScheduleOccurrenceRef(scheduleId, today), today, -1, 5),
            LocalSyncMutation.EndOccurrenceBreak(ScheduleOccurrenceRef(scheduleId, ScheduleDate(2023, 2, 29)), today),
        ).forEach { invalid ->
            val store = FakeSyncReplicaStore(snapshot())
            val writer = openWriter(store)

            assertEquals(LocalMutationFailure.INVALID_MUTATION, assertIs<LocalMutationResult.Failure>(writer.mutate(invalid)).reason)
            assertIs<LocalMutationResult.Success>(writer.mutate(LocalSyncMutation.EndSession(SessionId(testIdentifier(77)))))
        }
    }
}
