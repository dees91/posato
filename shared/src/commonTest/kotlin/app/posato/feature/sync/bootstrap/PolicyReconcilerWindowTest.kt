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
import app.posato.feature.targets.data.pauseSetIntents
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.WebsiteWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Window rules between this device and the workspace (ADR 0006 TARGETS-013 amendment). Failure modes: an edit
 * authors its rule before the website exists, a removal leaves a stale rule that reopens the website when it
 * returns, a local rule overwrites the workspace's at first link, or a received rule never reaches this device.
 */
class PolicyReconcilerWindowTest {
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val workspaceId = testContext.workspaceId.value.copyBytes()
    private val news = domain("news")
    private val mail = domain("mail")
    private val hourly = checkNotNull(WebsiteWindow.of(60, 5))
    private val halfHourly = checkNotNull(WebsiteWindow.of(30, 15))

    @Test
    fun `given edits of a set when diffed then rules follow the websites and a removed website authors no rule`() {
        val before = sets(LocalPauseSet(work, "Work", listOf(mail, news), windows = mapOf(news to hourly)))

        assertEquals(
            listOf(
                StoredPolicyIntent.PresentDomain(domain("added"), work),
                StoredPolicyIntent.SetWindow(domain("added"), work, halfHourly),
                StoredPolicyIntent.SetWindow(mail, work, hourly),
                StoredPolicyIntent.SetWindow(news, work, null),
            ),
            pauseSetIntents(
                before,
                sets(
                    LocalPauseSet(
                        work,
                        "Work",
                        listOf(domain("added"), mail, news),
                        windows = mapOf(domain("added") to halfHourly, mail to hourly),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf<StoredPolicyIntent>(StoredPolicyIntent.RemoveDomain(news, work)),
            pauseSetIntents(before, sets(LocalPauseSet(work, "Work", listOf(mail)))),
        )
        assertEquals(listOf<StoredPolicyIntent>(), pauseSetIntents(before, before))
    }

    @Test
    fun `given a rule received for a live set when applied then this device keeps it, and a queued edit wins over it`() = runTest {
        withStore("window-apply.db") { store ->
            store.replaceLocal(LocalPauseSet(work, "Work", listOf(mail, news)))
            val projection = reduce(
                SyncOperationPayload.PauseSetPut(work, "Work"),
                SyncOperationPayload.DomainPresent(mail, work),
                SyncOperationPayload.DomainPresent(news, work),
                SyncOperationPayload.SetDomainWindow(work, news, hourly),
                SyncOperationPayload.SetDomainWindow(work, mail, hourly),
            )
            assertIs<LocalPolicyResult.Success<Unit>>(
                store.recordIntents(PolicySyncWrite(workspaceId, listOf(StoredPolicyIntent.SetWindow(mail, work, null)))),
            )

            assertEquals(ReconcileOutcome.AppliedClean, PolicyReconciler(store).apply(projection, null))

            assertEquals(mapOf(news to hourly), store.local().sets.sets.single { it.id == work }.windows)
        }
    }

    @Test
    fun `given a queued removal of a ruled website when applied then the stale rule does not come back with the website`() = runTest {
        withStore("window-apply-removed.db") { store ->
            store.replaceLocal(LocalPauseSet(work, "Work", listOf(news)))
            val projection = reduce(
                SyncOperationPayload.PauseSetPut(work, "Work"),
                SyncOperationPayload.DomainPresent(news, work),
                SyncOperationPayload.SetDomainWindow(work, news, hourly),
            )
            assertIs<LocalPolicyResult.Success<Unit>>(
                store.recordIntents(
                    PolicySyncWrite(
                        workspaceId,
                        listOf(StoredPolicyIntent.RemoveDomain(news, work), StoredPolicyIntent.PresentDomain(news, work)),
                    ),
                ),
            )

            PolicyReconciler(store).apply(projection, null)

            val local = store.local().sets.sets.single { it.id == work }
            assertEquals(listOf(news), local.domains)
            assertEquals(emptyMap(), local.windows)
        }
    }

    @Test
    fun `given local rules at first link when seeded then only websites the workspace never ruled publish theirs, after the website`() = runTest {
        withStore("window-seed.db") { store ->
            store.replaceLocal(
                LocalPauseSet(work, "Work", listOf(domain("fresh"), mail, news), windows = mapOf(news to hourly, mail to hourly, domain("fresh") to hourly)),
            )
            val projection = reduce(
                SyncOperationPayload.PauseSetPut(work, "Work"),
                SyncOperationPayload.DomainPresent(mail, work),
                SyncOperationPayload.DomainPresent(news, work),
                SyncOperationPayload.SetDomainWindow(work, mail, halfHourly),
                SyncOperationPayload.SetDomainWindow(work, mail, null),
            )

            assertIs<LocalPolicyResult.Success<Unit>>(PolicyReconciler(store).seedLocalExtras(workspaceId, projection))

            assertEquals(
                listOf(
                    StoredPolicyIntent.PresentDomain(domain("fresh"), work),
                    StoredPolicyIntent.SetWindow(domain("fresh"), work, hourly),
                    StoredPolicyIntent.SetWindow(news, work, hourly),
                ),
                store.pending(),
            )
        }
    }

    private fun domain(label: String): ExactDomain {
        return checkNotNull(ExactDomain.restore("$label.example"))
    }

    private fun sets(vararg sets: LocalPauseSet): PauseSets {
        return checkNotNull(PauseSets.of(sets.toList(), null))
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
