package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.domain.SyncReducer
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import app.posato.feature.sync.testOperation
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Reconciling several pause sets against projections the real reducer builds from operations. */
class PolicyReconcilerPauseSetTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val gone = checkNotNull(PauseSetId.of(testIdentifier(81)))
    private val workspaceId = testContext.workspaceId.value.copyBytes()

    @Test
    fun `given local sets at first link when seeded then websites and other sets' names are queued but never the first set's name`() = runTest {
        withStore("pause-set-seed.db") { store ->
            store.replaceLocal(
                LocalPauseSet(PauseSetId.FIRST, "Mine", domains("a")),
                LocalPauseSet(work, "Work", domains("b")),
                LocalPauseSet(gone, "Gone", domains("c")),
            )
            val projection = reduce(SyncOperationPayload.PauseSetRemove(gone))

            assertIs<LocalPolicyResult.Success<Unit>>(PolicyReconciler(store).seedLocalExtras(workspaceId, projection))

            assertEquals(
                listOf(
                    StoredPolicyIntent.PresentDomain(domain("a"), PauseSetId.FIRST),
                    StoredPolicyIntent.PutSet(work, "Work"),
                    StoredPolicyIntent.PresentDomain(domain("b"), work),
                ),
                store.pending(),
            )
        }
    }

    @Test
    fun `given a set the workspace removed when applied then the local set and its queued website are dropped`() = runTest {
        withStore("pause-set-removed.db") { store ->
            store.replaceLocal(LocalPauseSet(PauseSetId.FIRST, null, domains("a")), LocalPauseSet(work, "Work", domains("b")))
            store.recordIntents(PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.PresentDomain(domain("x"), work))))
            val projection = reduce(SyncOperationPayload.PauseSetPut(work, "Work"), SyncOperationPayload.PauseSetRemove(work))

            assertEquals(ReconcileOutcome.AppliedClean, PolicyReconciler(store).apply(projection, null))

            assertEquals(listOf(PauseSetId.FIRST), store.local().sets.sets.map(LocalPauseSet::id))
        }
    }

    @Test
    fun `given a set the workspace removed when applied then this device's app choices keep only the surviving sets`() = runTest {
        withStore("pause-set-removed-apps.db") { store ->
            store.replaceLocal(LocalPauseSet(PauseSetId.FIRST, null, domains("a")), LocalPauseSet(work, "Work", domains("b")))
            val projection = reduce(SyncOperationPayload.PauseSetPut(work, "Work"), SyncOperationPayload.PauseSetRemove(work))
            var kept: Set<PauseSetId>? = null

            PolicyReconciler(store) { surviving -> kept = surviving }.apply(projection, null)

            assertEquals(setOf(PauseSetId.FIRST), kept)
        }
    }

    @Test
    fun `given a local set the workspace refused at the cap when applied then it is kept and marked refused`() = runTest {
        withStore("pause-set-refused.db") { store ->
            store.replaceLocal(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", domains("b")))
            val fill = (1..9).map { index -> SyncOperationPayload.PauseSetPut(checkNotNull(PauseSetId.of(testIdentifier(100 + index))), "S$index") }
            val projection = reduce(*fill.toTypedArray(), SyncOperationPayload.PauseSetPut(work, "Work"))

            PolicyReconciler(store).apply(projection, null)

            val kept = store.local().sets.sets.single { set -> set.id == work }
            assertEquals(true, kept.refused)
            assertEquals(domains("b"), kept.domains)
        }
    }

    @Test
    fun `given a queued rename and default when applied then they win over the workspace until authored`() = runTest {
        withStore("pause-set-register.db") { store ->
            store.replaceLocal(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", emptyList()))
            store.recordIntents(
                PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.PutSet(work, "Home"), StoredPolicyIntent.ChooseDefault(work))),
            )
            val projection = reduce(SyncOperationPayload.PauseSetPut(work, "Office"))

            PolicyReconciler(store).apply(projection, null)

            val sets = store.local().sets
            assertEquals("Home", sets.sets.single { set -> set.id == work }.name)
            assertEquals(work, sets.defaultSetId)
        }
    }

    @Test
    fun `given no queued change when applied then the workspace names the set and chooses the default`() = runTest {
        withStore("pause-set-remote-register.db") { store ->
            store.replaceLocal(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", emptyList()))
            val projection = reduce(SyncOperationPayload.PauseSetPut(work, "Office"), SyncOperationPayload.PauseSetDefault(work))

            PolicyReconciler(store).apply(projection, null)

            val sets = store.local().sets
            assertEquals("Office", sets.sets.single { set -> set.id == work }.name)
            assertEquals(work, sets.defaultSetId)
        }
    }

    @Test
    fun `given the unique websites of two sets fill the workspace when one more is refused then the workspace is full`() = runTest {
        withStore("pause-set-full.db") { store ->
            val first = (0 until 1_024).map { index -> SyncOperationPayload.DomainPresent(domain("f$index"), PauseSetId.FIRST) }
            val second = (0 until 1_024).map { index -> SyncOperationPayload.DomainPresent(domain("w$index"), work) }
            val projection = reduce(
                SyncOperationPayload.PauseSetPut(work, "Work"),
                *(first + second).toTypedArray(),
                SyncOperationPayload.DomainPresent(domain("extra"), work),
            )

            assertEquals(ReconcileOutcome.RefusedWorkspaceFull, PolicyReconciler(store).apply(projection, null))
        }
    }

    private fun domain(label: String): ExactDomain {
        return checkNotNull(ExactDomain.restore("$label.example"))
    }

    private fun domains(vararg labels: String): List<ExactDomain> {
        return labels.map(::domain).sortedBy(ExactDomain::canonicalValue)
    }

    private fun reduce(vararg payloads: SyncOperationPayload): SyncProjection {
        val register = testOperation(1, 1, SyncOperationPayload.AuthorRegister)
        val operations: List<SyncOperation> = listOf(register) +
            payloads.mapIndexed { index, payload -> testOperation(index + 2, index.toLong() + 2, payload) }
        return SyncReducer.reduce(operations)
    }

    private suspend fun SqlLocalTargetPolicyStore.replaceLocal(vararg sets: LocalPauseSet) {
        val revision = local().revision
        assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(replaceSets(revision, checkNotNull(PauseSets.of(sets.toList(), null))))
    }

    private suspend fun SqlLocalTargetPolicyStore.local(): LocalTargetPolicyState {
        return assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(read()).value
    }

    private suspend fun SqlLocalTargetPolicyStore.pending(): List<StoredPolicyIntent> {
        return assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(readIntents()).value.map(SequencedPolicyIntent::intent)
    }

    private suspend fun withStore(
        name: String,
        block: suspend (SqlLocalTargetPolicyStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            block(SqlLocalTargetPolicyStore(PosatoDatabase(driver), Dispatchers.Default))
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }
}
