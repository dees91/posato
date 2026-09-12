package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.AppleWorkspaceRemoval
import app.posato.feature.sync.bootstrap.BootstrapState
import app.posato.feature.sync.bootstrap.EstablishedCheck
import app.posato.feature.sync.bootstrap.EstablishedStatus
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.sync.bootstrap.FakeBootstrapKeyPort
import app.posato.feature.sync.bootstrap.FakeBootstrapStore
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.bootstrap.bindingA
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Removal → re-establishment → removal through the real common path on one
 * adapter instance. The adapter alone cannot observe workspace replacement:
 * only [AppleWorkspaceRemoval] invokes the resume-state hook, so these tests
 * drive removal directly and assert the next workspace starts fresh.
 */
class MacOsRemovalLifecycleTest {
    private val token = byteArrayOf(4, 5)

    @Test
    fun `given sweep-completed removal when removing again then the next workspace starts fresh`() = runTest {
        val transport = ScriptTransport(
            message(SyncCompanionOutcome.Incomplete, token),
            outcome(SyncCompanionOutcome.UnknownOutcome),
            outcome(SyncCompanionOutcome.Swept),
            outcome(SyncCompanionOutcome.DeletedAndAbsent),
        )
        val store = FakeBootstrapStore(BootstrapState.Established(workspaceA()))
        val removal = AppleWorkspaceRemoval(MacOsMailboxAdapter(transport), FakeBootstrapKeyPort(), store)

        assertEquals(SyncStatus.RETRYABLE, removal.remove(check(EstablishedStatus.READY, workspaceA()), {}))
        assertEquals(SyncStatus.LOCAL_ONLY, removal.remove(check(EstablishedStatus.ANCHOR_MISSING, workspaceA()), {}))
        store.state = BootstrapState.Established(workspaceB())
        assertEquals(SyncStatus.LOCAL_ONLY, removal.remove(check(EstablishedStatus.READY, workspaceB()), {}))
        assertTrue(transport.sentPayloads[3].contentEquals(plainPayload()))
    }

    @Test
    fun `given anchorless completions when removing again then the next workspace starts fresh`() = runTest {
        for (status in listOf(EstablishedStatus.DIFFERENT_ANCHOR, EstablishedStatus.ZONE_MISSING)) {
            val transport = ScriptTransport(
                message(SyncCompanionOutcome.Incomplete, token),
                outcome(SyncCompanionOutcome.UnknownOutcome),
                outcome(SyncCompanionOutcome.DeletedAndAbsent),
            )
            val store = FakeBootstrapStore(BootstrapState.Established(workspaceA()))
            val removal = AppleWorkspaceRemoval(MacOsMailboxAdapter(transport), FakeBootstrapKeyPort(), store)

            assertEquals(SyncStatus.RETRYABLE, removal.remove(check(EstablishedStatus.READY, workspaceA()), {}))
            val expected = if (status == EstablishedStatus.DIFFERENT_ANCHOR) {
                SyncStatus.ACTION_REQUIRED
            } else {
                SyncStatus.LOCAL_ONLY
            }
            assertEquals(expected, removal.remove(check(status, workspaceA()), {}))
            store.state = BootstrapState.Established(workspaceB())
            assertEquals(SyncStatus.LOCAL_ONLY, removal.remove(check(EstablishedStatus.READY, workspaceB()), {}))
            assertTrue(transport.sentPayloads[2].contentEquals(plainPayload()))
        }
    }

    @Test
    fun `given kept row when removing again then the stored cursor is preserved`() = runTest {
        val transport = ScriptTransport(
            message(SyncCompanionOutcome.Incomplete, token),
            outcome(SyncCompanionOutcome.UnknownOutcome),
            outcome(SyncCompanionOutcome.Retryable),
            outcome(SyncCompanionOutcome.DeletedAndAbsent),
        )
        val store = FakeBootstrapStore(BootstrapState.Established(workspaceA()))
        val removal = AppleWorkspaceRemoval(MacOsMailboxAdapter(transport), FakeBootstrapKeyPort(), store)

        assertEquals(SyncStatus.RETRYABLE, removal.remove(check(EstablishedStatus.READY, workspaceA()), {}))
        assertEquals(SyncStatus.RETRYABLE, removal.remove(check(EstablishedStatus.ANCHOR_MISSING, workspaceA()), {}))
        assertEquals(SyncStatus.LOCAL_ONLY, removal.remove(check(EstablishedStatus.READY, workspaceA()), {}))
        val resumed = MacOsSyncCompanionProtocol.deleteResumePayload(bindingA.copyBytes(), token)
        assertTrue(transport.sentPayloads[3].contentEquals(resumed))
    }

    private fun workspaceA(): EstablishedWorkspace {
        return EstablishedWorkspace(testContext, bindingA)
    }

    private fun workspaceB(): EstablishedWorkspace {
        val context = testContext.copy(workspaceId = WorkspaceId(testIdentifier(9)))
        return EstablishedWorkspace(context, bindingA)
    }

    private fun check(
        status: EstablishedStatus,
        workspace: EstablishedWorkspace,
    ): EstablishedCheck {
        return EstablishedCheck(status, workspace)
    }

    private fun plainPayload(): ByteArray {
        return MacOsSyncCompanionProtocol.cloudPayload(bindingA.copyBytes())
    }

    private fun outcome(outcome: SyncCompanionOutcome): CompanionExchange {
        return message(outcome, ByteArray(0))
    }

    private fun message(
        outcome: SyncCompanionOutcome,
        payload: ByteArray,
    ): CompanionExchange {
        return CompanionExchange.Message(
            SyncCompanionMessage(
                operation = SyncCompanionOperation.DeleteWorkspaceRecords,
                requestIdentifier = ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 },
                deadlineMilliseconds = 5_000,
                capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
                outcome = outcome,
                payload = payload,
            ),
        )
    }

    private class ScriptTransport(
        private vararg val exchanges: CompanionExchange,
    ) : SyncCompanionTransport {
        val sentPayloads = mutableListOf<ByteArray>()
        private var exchangeCount = 0

        override suspend fun transact(message: SyncCompanionMessage): CompanionExchange {
            sentPayloads.add(message.payload.copyOf())
            val index = minOf(exchangeCount, exchanges.size - 1)
            exchangeCount += 1
            return exchanges[index]
        }

        override fun newRequestIdentifier(): ByteArray {
            return ByteArray(MacOsSyncCompanionProtocol.IDENTIFIER_BYTES) { 9 }
        }
    }
}
