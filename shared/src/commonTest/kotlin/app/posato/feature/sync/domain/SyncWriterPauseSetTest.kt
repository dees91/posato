package app.posato.feature.sync.domain

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncWriterPauseSetTest {
    @Test
    fun `given terminal expiry of a session started with kind 18 when reopened then the fact is valid and keeps it concluded`() = runTest {
        val provider = FakeSyncCryptoProvider()
        val sessionId = SessionId(testIdentifier(99))
        val workSet = checkNotNull(PauseSetId.of(testIdentifier(81)))
        val accepted = acceptedSnapshot(
            provider,
            listOf(
                testOperation(38, 1, SyncOperationPayload.AuthorRegister),
                testOperation(39, 2, SyncOperationPayload.SessionStart(sessionId, 100, 200, workSet)),
            ),
        ).copy(terminalExpiryFacts = setOf(sessionId))

        val writer = assertIs<OpenSyncWriterResult.Success>(
            SyncOperationCore(FakeSyncReplicaStore(accepted), provider, SyncWallClock { 150 }).open(testContext, transportKey()),
        ).writer

        assertEquals(SessionCandidate.Concluded(sessionId, SessionConclusionKind.EXPIRED), writer.sessionCandidate(150))
    }
}
