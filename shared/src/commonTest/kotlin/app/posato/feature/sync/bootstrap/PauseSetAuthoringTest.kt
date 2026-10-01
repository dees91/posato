package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What a person's edits to pause sets author in a linked workspace, in the order other devices apply them. */
class PauseSetAuthoringTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))

    @Test
    fun `given a new named set with a website when saved then its set-put is authored before the website`() = runTest {
        withLinkedHarness("set-authoring-new.db") { harness ->
            save(harness, first(), LocalPauseSet(work, "Work", domains("a")))

            assertEquals(
                listOf(SyncOperationPayload.PauseSetPut(work, "Work"), SyncOperationPayload.DomainPresent(domain("a"), work)),
                harness.authoredSetChanges(),
            )
        }
    }

    @Test
    fun `given a renamed set and a new default when saved then set-put and set-default are authored`() = runTest {
        withLinkedHarness("set-authoring-rename.db") { harness ->
            save(harness, first(), LocalPauseSet(work, "Work", emptyList()))

            save(harness, first(), LocalPauseSet(work, "Home", emptyList()), defaultSetId = work)

            assertEquals(
                listOf(
                    SyncOperationPayload.PauseSetPut(work, "Work"),
                    SyncOperationPayload.PauseSetPut(work, "Home"),
                    SyncOperationPayload.PauseSetDefault(work),
                ),
                harness.authoredSetChanges(),
            )
        }
    }

    @Test
    fun `given a set with websites when deleted then only its removal is authored and no website leaves it`() = runTest {
        withLinkedHarness("set-authoring-delete.db") { harness ->
            save(harness, first(), LocalPauseSet(work, "Work", domains("a", "b")))

            save(harness, first())

            val authored = harness.authoredSetChanges()
            assertEquals(SyncOperationPayload.PauseSetRemove(work), authored.last())
            assertEquals(emptyList(), authored.filterIsInstance<SyncOperationPayload.DomainAbsent>())
        }
    }

    @Test
    fun `given websites moved between sets when saved then each set's change names its own set`() = runTest {
        withLinkedHarness("set-authoring-move.db") { harness ->
            save(harness, first("a"), LocalPauseSet(work, "Work", emptyList()))

            save(harness, first(), LocalPauseSet(work, "Work", domains("a")))

            assertEquals(
                listOf(
                    SyncOperationPayload.DomainAbsent(domain("a"), PauseSetId.FIRST),
                    SyncOperationPayload.DomainPresent(domain("a"), work),
                ),
                harness.authoredSetChanges().drop(2),
            )
        }
    }

    @Test
    fun `given queued changes of a set when the set is deleted then only its removal stays queued`() = runTest {
        withLinkedHarness("set-authoring-purge.db") { harness ->
            save(harness, first(), LocalPauseSet(work, "Work", emptyList()))
            val workspaceId = testContext.workspaceId.value.copyBytes()
            harness.sqlPolicy.recordIntents(
                PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.PutSet(work, "Later"), StoredPolicyIntent.PresentDomain(domain("q"), work))),
            )
            val current = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.sqlPolicy.read()).value
            val removed = checkNotNull(PauseSets.of(listOf(first()), null))

            assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(
                harness.sqlPolicy.replaceSets(current.revision, removed, PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.RemoveSet(work)))),
            )

            val queued = assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(harness.sqlPolicy.readIntents()).value
            assertEquals(listOf<StoredPolicyIntent>(StoredPolicyIntent.RemoveSet(work)), queued.map(SequencedPolicyIntent::intent))
        }
    }

    private fun first(vararg labels: String): LocalPauseSet {
        return LocalPauseSet(PauseSetId.FIRST, null, domains(*labels))
    }

    private fun domain(label: String): ExactDomain {
        return checkNotNull(ExactDomain.restore("$label.example"))
    }

    private fun domains(vararg labels: String): List<ExactDomain> {
        return labels.map(::domain)
    }

    private suspend fun TestScope.withLinkedHarness(
        name: String,
        block: suspend TestScope.(AppleSyncTestHarness) -> Unit,
    ) {
        val harness = AppleSyncTestHarness(StandardTestDispatcher(testScheduler), name = name)
        try {
            harness.establish()
            harness.sync.onForeground()
            advanceUntilIdle()
            block(harness)
        } finally {
            harness.close()
        }
    }

    private suspend fun TestScope.save(
        harness: AppleSyncTestHarness,
        vararg sets: LocalPauseSet,
        defaultSetId: PauseSetId? = null,
    ) {
        val current = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.read()).value
        val next = checkNotNull(PauseSets.of(sets.toList(), defaultSetId ?: current.sets.defaultSetId))
        assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(harness.syncPolicy.replaceSets(current.revision, next))
        advanceUntilIdle()
    }

    private suspend fun AppleSyncTestHarness.authoredSetChanges(): List<SyncOperationPayload> {
        return snapshot().acceptedBundles.values.map { stored -> stored.operation }
            .sortedBy(SyncOperation::authorSequence)
            .map(SyncOperation::payload)
            .filter { payload ->
                payload is SyncOperationPayload.PauseSetPut ||
                    payload is SyncOperationPayload.PauseSetRemove ||
                    payload is SyncOperationPayload.PauseSetDefault ||
                    payload is SyncOperationPayload.DomainPresent ||
                    payload is SyncOperationPayload.DomainAbsent
            }
    }
}
