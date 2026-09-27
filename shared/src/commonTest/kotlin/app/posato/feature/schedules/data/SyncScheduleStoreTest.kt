package app.posato.feature.schedules.data

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.sync.bootstrap.BootstrapStoreFailure
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.sync.bootstrap.bindingA
import app.posato.feature.sync.testContext
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val plan = SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Focus", 0b0011111, 540, 600, true)

internal class FakeScheduleSyncLink(
    var captured: BootstrapStoreResult<EstablishedWorkspace?> = BootstrapStoreResult.Success(null),
) : ScheduleSyncLink {
    var syncRequests = 0

    override suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
        return captured
    }

    override fun syncNow() {
        syncRequests += 1
    }
}

class SyncScheduleStoreTest {
    private suspend fun withStores(
        name: String,
        link: FakeScheduleSyncLink,
        block: suspend (SyncScheduleStore, SqlScheduleStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val local = SqlScheduleStore(PosatoDatabase(driver), Dispatchers.Default)
            block(SyncScheduleStore(local, link), local)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun SqlScheduleStore.rows(): Int {
        return assertIs<ScheduleResult.Success<ScheduleSnapshot>>(read()).value.schedules.size
    }

    private suspend fun SqlScheduleStore.pending(): List<SequencedScheduleIntent> {
        return assertIs<ScheduleResult.Success<List<SequencedScheduleIntent>>>(readIntents()).value
    }

    @Test
    fun `given the workspace cannot be read when a plan is saved then nothing is written`() = runTest {
        val link = FakeScheduleSyncLink(BootstrapStoreResult.Failure(BootstrapStoreFailure.STORAGE_FAILURE))
        withStores("sync-schedules-unknown.db", link) { store, local ->
            assertEquals(ScheduleResult.Failure(ScheduleStoreFailure.WORKSPACE_UNKNOWN), store.save(plan, workspaceId = null))

            assertEquals(0, local.rows())
            assertEquals(emptyList(), local.pending())
            assertEquals(0, link.syncRequests)
        }
    }

    @Test
    fun `given a linked workspace when a plan is saved then one intent for it is recorded and an exchange is asked for`() = runTest {
        val link = FakeScheduleSyncLink(BootstrapStoreResult.Success(EstablishedWorkspace(testContext, bindingA)))
        withStores("sync-schedules-linked.db", link) { store, local ->
            assertIs<ScheduleResult.Success<SchedulePlan>>(store.save(plan, workspaceId = null))

            val intent = local.pending().single()
            assertTrue(intent.belongsTo(testContext.workspaceId.value.copyBytes()))
            assertEquals(ScheduleIntent.Put(plan), intent.intent)
            assertEquals(1, link.syncRequests)
        }
    }

    @Test
    fun `given no workspace when a plan is saved then it is stored without an intent or an exchange`() = runTest {
        val link = FakeScheduleSyncLink()
        withStores("sync-schedules-unlinked.db", link) { store, local ->
            assertIs<ScheduleResult.Success<SchedulePlan>>(store.save(plan, workspaceId = null))

            assertEquals(1, local.rows())
            assertEquals(emptyList(), local.pending())
            assertEquals(0, link.syncRequests)
        }
    }
}
