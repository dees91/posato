package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.ui.FakeLocalSessionStore
import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.FakeSyncReplicaStore
import app.posato.feature.sync.domain.LocalCommitMode
import app.posato.feature.sync.domain.OpenSyncWriterResult
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.domain.acceptedSnapshot
import app.posato.feature.sync.domain.transportKey
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionReconcilerTerminalGateTest {
    @Test
    fun `given a stale current candidate when the bank succeeds then the expired session is not adopted`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val expired = SessionId(testIdentifier(101))
        val retained = SessionId(testIdentifier(102))
        val initial = acceptedSnapshot(
            provider,
            listOf(
                testOperation(1, 1, SyncOperationPayload.AuthorRegister),
                testOperation(2, 2, SyncOperationPayload.SessionStart(expired, 100, 200)),
            ),
        )
        val store = FakeSyncReplicaStore(initial)
        val writer = openWriter(store, provider)
        try {
            val sessions = FakeLocalSessionStore()
            sessions.record = SessionRecord(retained, 100, 200)
            sessions.endedEarly = true
            sessions.retainedMarkers += expired
            val workspace = EstablishedWorkspace(testContext, assertNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 1 })))

            val result = SessionReconciler(sessions).reconcile(writer, workspace, 150, SessionSyncAuthoring(sessions)) { frozen() }

            // The drain banks the expired session before any adoption decision,
            // so the fresh evaluation cannot revive it from a stale Current.
            assertIs<SessionReconcileResult.Completed>(result)
            val status = assertIs<LocalSessionStatus.Ended>(result.status)
            assertEquals(retained, status.record.sessionId)
            assertTrue(sessions.retainedExpiryMarkers().value().isEmpty())
            assertTrue(expired in store.current.terminalExpiryFacts)
        } finally {
            writer.close()
        }
    }

    @Test
    fun `given a retained terminal fact when the bank fails then adoption is refused`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val expired = SessionId(testIdentifier(103))
        val retained = SessionId(testIdentifier(104))
        val initial = acceptedSnapshot(
            provider,
            listOf(
                testOperation(1, 1, SyncOperationPayload.AuthorRegister),
                testOperation(2, 2, SyncOperationPayload.SessionStart(expired, 100, 200)),
            ),
        )
        val store = FakeSyncReplicaStore(initial, expiryCommitMode = LocalCommitMode.AMBIGUOUS_WITH_EXTRA_STATE)
        val writer = openWriter(store, provider)
        try {
            val sessions = FakeLocalSessionStore()
            sessions.record = SessionRecord(retained, 100, 200)
            sessions.endedEarly = true
            sessions.retainedMarkers += expired
            val workspace = EstablishedWorkspace(testContext, assertNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 1 })))

            val result = SessionReconciler(sessions).reconcile(writer, workspace, 150, SessionSyncAuthoring(sessions)) { frozen() }

            // The failed bank keeps the marker and bars adoption of that
            // identity; the ended row is untouched instead of revived.
            assertIs<SessionReconcileResult.Halted>(result)
            assertEquals(SyncStatus.ACTION_REQUIRED, result.status)
            val status = assertIs<LocalSessionStatus.Ended>(assertRead(sessions))
            assertEquals(retained, status.record.sessionId)
            assertEquals(setOf(expired), sessions.retainedExpiryMarkers().value())
        } finally {
            writer.close()
        }
    }

    private suspend fun openWriter(
        store: FakeSyncReplicaStore,
        provider: FakeSyncCryptoProvider,
    ): SyncWriter {
        return assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(store, provider, SyncWallClock { 100 }).open(testContext, transportKey()),
        ).writer
    }

    private suspend fun assertRead(sessions: LocalSessionSyncStore): LocalSessionStatus {
        return assertIs<LocalSessionResult.Success<LocalSessionStatus>>(sessions.read(150)).value
    }

    private fun frozen(): FrozenStartSet {
        return FrozenStartSet(persistentListOf("stable.example"), null)
    }

    private fun LocalSessionResult<Set<SessionId>>.value(): Set<SessionId> {
        return assertIs<LocalSessionResult.Success<Set<SessionId>>>(this).value
    }
}
