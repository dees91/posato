package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.data.DurableClockState
import app.posato.feature.sync.data.SyncReplicaSnapshot
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDomainRedactionTest {
    @Test
    fun `given a sync identifier when converted to a string then its bytes and identity remain redacted`() {
        assertEquals("SyncIdentifier(redacted)", testIdentifier(7).toString())
        assertEquals("SessionId(value=SyncIdentifier(redacted))", SessionId(testIdentifier(8)).toString())
    }

    @Test
    fun `given a local mutation success when converted to a string then pending cardinality remains redacted`() {
        val result = LocalMutationResult.Success(
            pendingBundles = listOf(
                checkNotNull(EncryptedBundle.fromBytes(ByteArray(32) { 1 })),
                checkNotNull(EncryptedBundle.fromBytes(ByteArray(32) { 2 })),
            ),
            projection = SyncReducer.reduce(emptyList()),
        )

        assertEquals("LocalMutationResult.Success(redacted)", result.toString())
    }

    @Test
    fun `given a replica snapshot when converted to a string then durable state remains redacted`() {
        val snapshot = SyncReplicaSnapshot(
            context = testContext,
            revision = 42,
            clockState = DurableClockState(HybridLogicalClock(1_234_567_890L, 42), isExhausted = false),
            acceptedBundles = emptyMap(),
            stagedBundles = emptyMap(),
            pendingBundles = emptyMap(),
            terminalExpiryFacts = emptySet(),
            transportProgress = null,
        )

        assertEquals("SyncReplicaSnapshot(redacted)", snapshot.toString())
    }

    @Test
    fun `given authoring state carriers when converted to strings then metadata remains redacted`() {
        val incarnation = AuthoringIncarnation(
            authorId = AuthorId(testIdentifier(19)),
            signingKey = FakeSyncCryptoProvider().createSigningKey(),
            nextSequence = 4_294_967_296L,
        )
        val prepared = PreparedLocalMutation(
            bundles = emptyList(),
            clockState = DurableClockState(HybridLogicalClock(1_234_567_890L, 42), isExhausted = false),
            nextIncarnation = incarnation,
        )

        assertEquals("AuthoringIncarnation(redacted)", incarnation.toString())
        assertEquals("PreparedLocalMutation(redacted)", prepared.toString())
    }

    @Test
    fun `given a hybrid logical clock when converted to a string then exact time remains redacted`() {
        assertEquals(
            "HybridLogicalClock(redacted)",
            HybridLogicalClock(1_234_567_890L, 42).toString(),
        )
    }

    @Test
    fun `given session timing carriers when converted to strings then timing remains redacted`() {
        val sessionId = SessionId(testIdentifier(20))
        val startEpochMillis = 1_234_567_890L
        val mandatoryEndEpochMillis = 1_234_568_000L
        val operation = testOperation(
            id = 21,
            sequence = 2,
            payload = SyncOperationPayload.SessionEnd(sessionId),
            physical = startEpochMillis,
        )

        assertEquals(
            "SyncOperationPayload.SessionStart(redacted)",
            SyncOperationPayload.SessionStart(sessionId, startEpochMillis, mandatoryEndEpochMillis).toString(),
        )
        assertEquals(
            "LocalSyncMutation.StartSession(redacted)",
            LocalSyncMutation.StartSession(sessionId, startEpochMillis, mandatoryEndEpochMillis).toString(),
        )
        assertEquals(
            "SynchronizedSessionStart(redacted)",
            SynchronizedSessionStart(
                operationId = operation.operationId,
                sessionId = sessionId,
                startEpochMillis = startEpochMillis,
                mandatoryEndEpochMillis = mandatoryEndEpochMillis,
                order = operation.order(),
                isEnded = false,
            ).toString(),
        )
        assertEquals(
            "EffectiveSession.Active(redacted)",
            EffectiveSession.Active(sessionId, mandatoryEndEpochMillis).toString(),
        )
    }
}
