package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.FakeSyncCryptoProvider
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun `given a renamed first set and another default when this device creates the workspace then a later device receives both`() = runTest {
        val (creator, joiner) = pair("first-set-create")
        try {
            creator.replaceLocal(focusWithEveningDefault())
            creator.establish()
            creator.sync.onForeground()
            advanceUntilIdle()

            assertEquals("Focus", creator.firstName())
            assertEquals(evening, creator.localSets().defaultSetId)
            val published = creator.acceptedPayloads()
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Focus") in published, "first set's name not published")
            assertTrue(SyncOperationPayload.PauseSetDefault(evening) in published, "default not published")

            joiner.establish()
            joiner.sync.onForeground()
            advanceUntilIdle()

            assertEquals("Focus", joiner.firstName())
            assertEquals(evening, joiner.localSets().resolvedDefault())
        } finally {
            creator.close()
            joiner.close()
        }
    }

    @Test
    fun `given a workspace that named its first set and chose a default when a renamed device joins then the workspace's choices win`() = runTest {
        val (member, joiner) = pair("first-set-join-named")
        try {
            member.establish()
            member.sync.onForeground()
            advanceUntilIdle()
            assertIs<LocalPolicyResult.Success<*>>(
                member.syncPolicy.replaceSets(
                    member.localState().revision,
                    checkNotNull(
                        PauseSets.of(
                            listOf(LocalPauseSet(PauseSetId.FIRST, "Work", emptyList()), LocalPauseSet(evening, "Evening", domains("example.net"))),
                            evening,
                        ),
                    ),
                ),
            )
            member.sync.syncNow()
            advanceUntilIdle()

            joiner.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList())), PauseSetId.FIRST)))
            joiner.establish()
            joiner.sync.onForeground()
            advanceUntilIdle()

            assertEquals("Work", joiner.firstName())
            assertEquals(evening, joiner.localSets().resolvedDefault())
            val published = joiner.acceptedPayloads()
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Focus") !in published)
            assertEquals(1, published.count { payload -> payload is SyncOperationPayload.PauseSetDefault })
        } finally {
            member.close()
            joiner.close()
        }
    }

    @Test
    fun `given a workspace that never named its first set when a renamed device joins then the member receives its choices`() = runTest {
        val (member, joiner) = pair("first-set-join-unnamed")
        try {
            member.establish()
            member.sync.onForeground()
            advanceUntilIdle()

            joiner.replaceLocal(focusWithEveningDefault())
            joiner.establish()
            joiner.sync.onForeground()
            advanceUntilIdle()
            member.sync.syncNow()
            advanceUntilIdle()

            assertEquals("Focus", joiner.firstName())
            assertEquals("Focus", member.firstName())
            assertEquals(evening, member.localSets().resolvedDefault())
        } finally {
            member.close()
            joiner.close()
        }
    }

    @Test
    fun `given two renamed devices that link at the same time when they exchange then both publish and the later clock's name wins`() = runTest {
        val shared = SharedFakeMailboxPort()
        // Each first pass publishes before it can see the other's bundles, as with two links at the same moment.
        val held = HeldMailbox(shared)
        val (first, second) = pair("first-set-concurrent", held)
        try {
            first.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList())), null)))
            second.replaceLocal(checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, "Other", emptyList())), null)))
            first.establish()
            second.establish()
            first.sync.onForeground()
            second.sync.onForeground()
            advanceUntilIdle()
            held.holding = false
            repeat(2) {
                first.sync.syncNow()
                second.sync.syncNow()
                advanceUntilIdle()
            }

            val published = first.acceptedPayloads()
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Focus") in published)
            assertTrue(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Other") in published)
            // The second device's wall clock is later (200 against 100), so its put has the greater total order.
            assertEquals("Other", first.firstName())
            assertEquals("Other", second.firstName())
        } finally {
            first.close()
            second.close()
        }
    }

    private fun TestScope.pair(
        name: String,
        mailbox: MailboxPort = SharedFakeMailboxPort(),
    ): Pair<AppleSyncTestHarness, AppleSyncTestHarness> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AppleSyncTestHarness(dispatcher, "$name-a.db", mailboxPort = mailbox, wallClock = SyncWallClock { 100 }) to
            AppleSyncTestHarness(
                dispatcher,
                "$name-b.db",
                mailboxPort = mailbox,
                wallClock = SyncWallClock { 200 },
                cryptoProvider = FakeSyncCryptoProvider(streamSeed = 0x5A),
            )
    }

    private fun focusWithEveningDefault(): PauseSets {
        return checkNotNull(
            PauseSets.of(
                listOf(LocalPauseSet(PauseSetId.FIRST, "Focus", domains("example.com")), LocalPauseSet(evening, "Evening", domains("example.net"))),
                evening,
            ),
        )
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

    private suspend fun AppleSyncTestHarness.firstName(): String? {
        return localSets().sets.single { set -> set.id == PauseSetId.FIRST }.name
    }

    private suspend fun AppleSyncTestHarness.replaceLocal(sets: PauseSets) {
        assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(sqlPolicy.replaceSets(localState().revision, sets))
    }

    /** Every operation this replica holds, its own and the ones it accepted from the folder or iCloud. */
    private suspend fun AppleSyncTestHarness.acceptedPayloads(): List<SyncOperationPayload> {
        return snapshot().acceptedBundles.values.map { stored -> stored.operation.payload }
    }
}

/** Delivers nothing while [holding], so two devices each author before either sees the other. */
private class HeldMailbox(
    private val inner: SharedFakeMailboxPort,
) : MailboxPort by inner {
    var holding = true

    override suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor,
    ): ChangeFetchResult {
        return if (holding) ChangeFetchResult.Page(ChangePage(null, false, cursor)) else inner.fetchChanges(expectedBinding, cursor)
    }
}
