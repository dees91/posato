package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `SYNC-024`: a name and a default chosen on a device before it links. Renames made without a workspace are
 * never queued, so linking is the only moment they can reach the workspace.
 */
class AppleSyncFirstSetNameTest {
    private val evening = checkNotNull(PauseSetId.of(testIdentifier(82)))

    @Test
    fun `given a renamed first set and another default when this device creates the workspace then both are kept and published`() = runTest {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = "first-set-create.db")
        try {
            harness.replaceLocal(
                checkNotNull(
                    PauseSets.of(
                        listOf(
                            LocalPauseSet(PauseSetId.FIRST, "Focus", domains("example.com")),
                            LocalPauseSet(evening, "Evening", domains("example.net")),
                        ),
                        evening,
                    ),
                ),
            )
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()

            val local = harness.localSets()
            assertEquals("Focus", local.sets.single { set -> set.id == PauseSetId.FIRST }.name)
            assertEquals(evening, local.defaultSetId)
            val authored = harness.acceptedPayloads()
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Focus") in authored, "first set's name not published")
            assertTrue(SyncOperationPayload.PauseSetDefault(evening) in authored, "default not published")
        } finally {
            harness.close()
        }
    }

    @Test
    fun `given a workspace that named its first set when a renamed device joins then the workspace's name wins`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mailbox = SharedFakeMailboxPort()
        val member = AppleSyncTestHarness(dispatcher, "first-set-join-member.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 })
        val joiner = AppleSyncTestHarness(
            dispatcher,
            "first-set-join-joiner.db",
            mailboxPort = mailbox,
            wallClock = SyncWallClock { 200 },
            cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
        )
        try {
            member.establish()
            member.sync.onForeground()
            advanceUntilIdle()
            val linked = member.localSets()
            val workspaceId = checkNotNull(
                assertIs<BootstrapStoreResult.Success<EstablishedWorkspace?>>(member.sync.captureWorkspace()).value,
            ).context.workspaceId.value.copyBytes()
            assertIs<LocalPolicyResult.Success<*>>(
                member.syncPolicy.replaceSets(
                    member.localState().revision,
                    checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Work", emptyList())), linked.defaultSetId)),
                ),
            )
            member.sync.syncNow()
            advanceUntilIdle()
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Work") in member.acceptedPayloads(), "workspace $workspaceId")

            joiner.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList())), null)))
            joiner.establish()
            joiner.sync.onForeground()
            advanceUntilIdle()

            assertEquals("Work", joiner.localSets().sets.single { set -> set.id == PauseSetId.FIRST }.name)
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Focus") !in joiner.acceptedPayloads())
        } finally {
            member.close()
            joiner.close()
        }
    }

    private fun domains(vararg labels: String): List<ExactDomain> {
        return labels.map { label -> checkNotNull(ExactDomain.restore(label)) }.sortedBy(ExactDomain::canonicalValue)
    }

    private suspend fun AppleSyncTestHarness.localState(): LocalTargetPolicyState {
        return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.read()).value
    }

    private suspend fun AppleSyncTestHarness.localSets(): PauseSets {
        return localState().sets
    }

    private suspend fun AppleSyncTestHarness.replaceLocal(sets: PauseSets) {
        assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.replaceSets(localState().revision, sets))
    }

    /** Every operation this replica holds, its own and the ones it accepted from the folder or iCloud. */
    private suspend fun AppleSyncTestHarness.acceptedPayloads(): List<SyncOperationPayload> {
        return snapshot().acceptedBundles.values.map { stored -> stored.operation.payload }
    }
}
