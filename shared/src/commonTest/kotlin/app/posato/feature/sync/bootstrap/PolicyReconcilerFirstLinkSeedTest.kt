package app.posato.feature.sync.bootstrap

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SyncFormatLimits
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

/**
 * `SYNC-024`: what first link queues for the first set's name and the default. A device publishes them only while
 * the workspace has not chosen (ADR 0006, "Migration identity").
 */
class PolicyReconcilerFirstLinkSeedTest {
    private val evening = checkNotNull(PauseSetId.of(testIdentifier(82)))
    private val workspaceId = testContext.workspaceId.value.copyBytes()

    @Test
    fun `given a workspace that named its first set when seeded then the local name is not queued`() = runTest {
        withStore("seed-named.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList())))

            seed(store, reduce(SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Work")))

            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `given a seed that was drained and another device renamed the first set when seeded again then nothing is queued`() = runTest {
        withStore("seed-after-drain.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList()), default = evening, extra = evening))
            seed(store, reduce())
            assertEquals(
                listOf(
                    StoredPolicyIntent.PutSet(PauseSetId.FIRST, "Focus"),
                    StoredPolicyIntent.PutSet(evening, "Evening"),
                    StoredPolicyIntent.ChooseDefault(evening),
                ),
                store.pending(),
            )
            assertIs<LocalPolicyResult.Success<Unit>>(store.clearIntents())

            seed(
                store,
                reduce(
                    SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Focus"),
                    SyncOperationPayload.PauseSetPut(evening, "Evening"),
                    SyncOperationPayload.PauseSetDefault(evening),
                    SyncOperationPayload.PauseSetPut(PauseSetId.FIRST, "Work"),
                ),
            )

            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `given a local default equal to the workspace's when seeded then no default is queued`() = runTest {
        withStore("seed-default-equal.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), default = PauseSetId.FIRST, extra = evening))

            seed(store, reduce())

            assertEquals(listOf(StoredPolicyIntent.PutSet(evening, "Evening")), store.pending())
        }
    }

    @Test
    fun `given a workspace that chose a default for a set it later removed when seeded then the local default is not queued`() = runTest {
        withStore("seed-default-chosen.db") { store ->
            val gone = checkNotNull(PauseSetId.of(testIdentifier(83)))
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), default = evening, extra = evening))

            seed(
                store,
                reduce(
                    SyncOperationPayload.PauseSetPut(gone, "Gone"),
                    SyncOperationPayload.PauseSetDefault(gone),
                    SyncOperationPayload.PauseSetRemove(gone),
                ),
            )

            assertEquals(listOf(StoredPolicyIntent.PutSet(evening, "Evening")), store.pending())
        }
    }

    @Test
    fun `given a workspace that removed the first set when seeded then neither its name nor the default on it is queued`() = runTest {
        withStore("seed-first-removed.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, "Focus", domains("a")), default = PauseSetId.FIRST, extra = evening))

            seed(store, reduce(SyncOperationPayload.PauseSetRemove(PauseSetId.FIRST)))

            assertEquals(listOf(StoredPolicyIntent.PutSet(evening, "Evening")), store.pending())
        }
    }

    @Test
    fun `given a local default that the workspace would refuse at the set cap when seeded then no default is queued`() = runTest {
        withStore("seed-default-cap.db") { store ->
            val full = (90 until 90 + SyncFormatLimits.MAX_PAUSE_SETS - 1).map { value -> checkNotNull(PauseSetId.of(testIdentifier(value))) }
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), default = evening, extra = evening))

            seed(store, reduce(*full.map { id -> SyncOperationPayload.PauseSetPut(id, "Full") }.toTypedArray()))

            assertEquals(listOf(StoredPolicyIntent.PutSet(evening, "Evening")), store.pending())
        }
    }

    @Test
    fun `given a default whose new set is already queued when seeded then the default is still queued after it`() = runTest {
        withStore("seed-default-queued-set.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), default = evening, extra = evening))
            assertIs<LocalPolicyResult.Success<Unit>>(
                store.recordIntents(PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.PutSet(evening, "Evening")))),
            )

            seed(store, reduce())

            assertEquals(listOf(StoredPolicyIntent.PutSet(evening, "Evening"), StoredPolicyIntent.ChooseDefault(evening)), store.pending())
        }
    }

    @Test
    fun `given an earlier new set that takes the workspace's last free slot when seeded then the later default set is not queued as default`() = runTest {
        withStore("seed-default-last-slot.db") { store ->
            val earlier = checkNotNull(PauseSetId.of(testIdentifier(81)))
            val full = (90 until 90 + SyncFormatLimits.MAX_PAUSE_SETS - 2).map { value -> checkNotNull(PauseSetId.of(testIdentifier(value))) }
            store.replaceLocal(
                checkNotNull(
                    PauseSets.of(
                        listOf(
                            LocalPauseSet(PauseSetId.FIRST, null, emptyList()),
                            LocalPauseSet(earlier, "Earlier", emptyList()),
                            LocalPauseSet(evening, "Evening", emptyList()),
                        ),
                        evening,
                    ),
                ),
            )

            seed(store, reduce(*full.map { id -> SyncOperationPayload.PauseSetPut(id, "Full") }.toTypedArray()))

            assertEquals(listOf(StoredPolicyIntent.PutSet(earlier, "Earlier"), StoredPolicyIntent.PutSet(evening, "Evening")), store.pending())
        }
    }

    @Test
    fun `given two seeds before any drain when seeded then each name and the default are queued once`() = runTest {
        withStore("seed-twice.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, "Focus", domains("a")), default = evening, extra = evening))

            seed(store, reduce())
            seed(store, reduce())

            assertEquals(
                listOf(
                    StoredPolicyIntent.PutSet(PauseSetId.FIRST, "Focus"),
                    StoredPolicyIntent.PresentDomain(domain("a"), PauseSetId.FIRST),
                    StoredPolicyIntent.PutSet(evening, "Evening"),
                    StoredPolicyIntent.ChooseDefault(evening),
                ),
                store.pending(),
            )
        }
    }

    @Test
    fun `given a rename recorded after linking but before the first seed when seeded then only the recorded rename is queued`() = runTest {
        withStore("seed-recorded-rename.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, "Focus", emptyList())))
            assertIs<LocalPolicyResult.Success<Unit>>(
                store.recordIntents(PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.PutSet(PauseSetId.FIRST, "Focus")))),
            )

            seed(store, reduce())

            assertEquals(listOf(StoredPolicyIntent.PutSet(PauseSetId.FIRST, "Focus")), store.pending())
        }
    }

    @Test
    fun `given queued changes for an earlier workspace when seeded then they do not suppress this workspace's seed`() = runTest {
        withStore("seed-stale-rows.db") { store ->
            store.replaceLocal(sets(LocalPauseSet(PauseSetId.FIRST, "Focus", domains("a")), default = evening, extra = evening))
            val earlier = testIdentifier(99).copyBytes()
            assertIs<LocalPolicyResult.Success<Unit>>(
                store.recordIntents(
                    PolicySyncWrite(
                        earlier,
                        listOf(
                            StoredPolicyIntent.PutSet(PauseSetId.FIRST, "Focus"),
                            StoredPolicyIntent.PresentDomain(domain("a"), PauseSetId.FIRST),
                            StoredPolicyIntent.ChooseDefault(evening),
                        ),
                    ),
                ),
            )

            seed(store, reduce())

            assertEquals(
                listOf(
                    StoredPolicyIntent.PutSet(PauseSetId.FIRST, "Focus"),
                    StoredPolicyIntent.PresentDomain(domain("a"), PauseSetId.FIRST),
                    StoredPolicyIntent.PutSet(evening, "Evening"),
                    StoredPolicyIntent.ChooseDefault(evening),
                ),
                store.pendingFor(workspaceId),
            )
        }
    }

    private suspend fun seed(
        store: SqlLocalTargetPolicyStore,
        projection: SyncProjection,
    ) {
        assertIs<LocalPolicyResult.Success<Unit>>(PolicyReconciler(store).seedLocalExtras(workspaceId, projection))
    }

    private fun sets(
        first: LocalPauseSet,
        default: PauseSetId? = null,
        extra: PauseSetId? = null,
    ): PauseSets {
        val others = listOfNotNull(extra?.let { id -> LocalPauseSet(id, "Evening", emptyList()) })
        return checkNotNull(PauseSets.of(listOf(first) + others, default))
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

    private suspend fun SqlLocalTargetPolicyStore.replaceLocal(sets: PauseSets) {
        val revision = assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(read()).value.revision
        assertIs<LocalPolicyResult.Success<LocalTargetPolicyState>>(replaceSets(revision, sets))
    }

    private suspend fun SqlLocalTargetPolicyStore.rows(): List<SequencedPolicyIntent> {
        return assertIs<LocalPolicyResult.Success<List<SequencedPolicyIntent>>>(readIntents()).value
    }

    private suspend fun SqlLocalTargetPolicyStore.pending(): List<StoredPolicyIntent> {
        return rows().map(SequencedPolicyIntent::intent)
    }

    private suspend fun SqlLocalTargetPolicyStore.pendingFor(workspace: ByteArray): List<StoredPolicyIntent> {
        return rows().filter { row -> row.workspaceId.contentEquals(workspace) }.map(SequencedPolicyIntent::intent)
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
