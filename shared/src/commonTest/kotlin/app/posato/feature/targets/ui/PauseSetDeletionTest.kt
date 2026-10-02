package app.posato.feature.targets.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.onboarding.data.LocalSetupResult
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.data.ScheduleResult
import app.posato.feature.schedules.data.ScheduleStoreFailure
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.domain.ScheduleId
import app.posato.feature.schedules.domain.SchedulePlan
import app.posato.feature.schedules.host.ScheduledPause
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.ui.FakeSessionMappings
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncWrite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deleting a set never leaves a schedule or a running pause without one: schedules move first, a failed move
 * keeps the set, and a set a pause uses now stays until that pause ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PauseSetDeletionTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val focus = SchedulePlan(ScheduleId("000000000000400080000000000000a1"), "Focus", 1, 540, 600, false, work)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a disabled schedule using the set when deleted with a move then the schedule moves and the set goes`() = runTest(dispatcher) {
        withStores("delete-move.db") { policies, schedules ->
            val viewModel = PauseSetsViewModel(inputsOf(policies, schedules))
            advanceUntilIdle()

            viewModel.delete(work, moveTo = PauseSetId.FIRST)
            advanceUntilIdle()

            assertEquals(listOf(PauseSetId.FIRST), policies.setIds())
            assertEquals(PauseSetId.FIRST, schedules.planOf(focus.id)?.setId)
        }
    }

    @Test
    fun `given a schedule move that fails when deleting then the set stays`() = runTest(dispatcher) {
        withStores("delete-move-fails.db") { policies, schedules ->
            val viewModel = PauseSetsViewModel(inputsOf(policies, FailingSaves(schedules)))
            advanceUntilIdle()

            viewModel.delete(work, moveTo = PauseSetId.FIRST)
            advanceUntilIdle()

            assertEquals(listOf(PauseSetId.FIRST, work), policies.setIds())
            assertEquals(PauseSetsFailure.SAVE_FAILED, viewModel.uiState.value.failure)
        }
    }

    @Test
    fun `given the running session uses the set when deleted then the set stays until the pause ends`() = runTest(dispatcher) {
        withStores("delete-in-use.db") { policies, schedules ->
            val running = LocalSessionStatus.Active(
                SessionRecord(SessionId(testIdentifier(21)), 0, 600_000, work),
                600_000,
                null,
                SessionOrigin.LOCAL,
            )
            val viewModel = PauseSetsViewModel(inputsOf(policies, schedules, MutableStateFlow(running)))
            advanceUntilIdle()

            viewModel.delete(work, moveTo = PauseSetId.FIRST)
            advanceUntilIdle()

            assertEquals(listOf(PauseSetId.FIRST, work), policies.setIds())
            assertEquals(PauseSetsFailure.IN_USE, viewModel.uiState.value.failure)
        }
    }

    @Test
    fun `given the policy cannot be read after a deletion then no set's app choices are deleted`() = runTest(dispatcher) {
        withStores("delete-read-fails.db") { policies, schedules ->
            schedules.remove(focus.id, workspaceId = null)
            val mappings = FakeSessionMappings()
            val store = UnreadableAfterWrite(policies)
            val viewModel = PauseSetsViewModel(inputsOf(store, schedules, mappings = mappings))
            advanceUntilIdle()

            viewModel.delete(work, moveTo = null)
            advanceUntilIdle()

            assertEquals(listOf(PauseSetId.FIRST), policies.setIds())
            assertEquals(emptyList(), mappings.retained)
        }
    }

    private fun inputsOf(
        policies: LocalTargetPolicyStore,
        schedules: LocalScheduleStore,
        session: MutableStateFlow<LocalSessionStatus?> = MutableStateFlow(LocalSessionStatus.Inactive),
        mappings: FakeSessionMappings = FakeSessionMappings(),
    ): PauseSetsInputs {
        return PauseSetsInputs(
            store = policies,
            applicationMappings = mappings,
            schedules = schedules,
            sessionStatus = session,
            scheduledPause = MutableStateFlow<ScheduledPause?>(null),
            setupStore = NoticeShown,
            linked = { false },
        )
    }

    private suspend fun withStores(
        name: String,
        block: suspend (SqlLocalTargetPolicyStore, SqlScheduleStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val policies = SqlLocalTargetPolicyStore(database, dispatcher)
            val schedules = SqlScheduleStore(database, dispatcher)
            val revision = (policies.read() as LocalPolicyResult.Success<LocalTargetPolicyState>).value.revision
            val sets =
                checkNotNull(PauseSets.of(listOf(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", emptyList())), null))
            policies.replaceSets(revision, sets)
            schedules.save(focus, workspaceId = null)
            block(policies, schedules)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun SqlLocalTargetPolicyStore.setIds(): List<PauseSetId> {
        return (read() as LocalPolicyResult.Success<LocalTargetPolicyState>).value.sets.sets.map(LocalPauseSet::id)
    }

    private suspend fun LocalScheduleStore.planOf(id: ScheduleId): SchedulePlan? {
        return (read() as ScheduleResult.Success).value.schedules.firstOrNull { stored -> stored.plan.id == id }?.plan
    }
}

private class FailingSaves(
    private val delegate: LocalScheduleStore,
) : LocalScheduleStore by delegate {
    override suspend fun save(
        plan: SchedulePlan,
        workspaceId: ByteArray?,
    ): ScheduleResult<SchedulePlan> {
        return ScheduleResult.Failure(ScheduleStoreFailure.STORAGE_FAILURE)
    }
}

private object NoticeShown : LocalSetupStore {
    override suspend fun read(): LocalSetupResult<SetupCompletion> {
        return LocalSetupResult.Success(SetupCompletion.COMPLETE)
    }

    override suspend fun markComplete(): LocalSetupResult<Unit> {
        return LocalSetupResult.Success(Unit)
    }

    override suspend fun readPauseSetNoticeShown(): LocalSetupResult<Boolean> {
        return LocalSetupResult.Success(true)
    }

    override suspend fun markPauseSetNoticeShown(): LocalSetupResult<Unit> {
        return LocalSetupResult.Success(Unit)
    }
}

/** Reads fail once a set write has succeeded, as a storage hiccup right after a deletion would. */
private class UnreadableAfterWrite(
    private val inner: LocalTargetPolicyStore,
) : LocalTargetPolicyStore by inner {
    private var wrote = false

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        return if (wrote) LocalPolicyResult.Failure(LocalPolicyFailure.STORAGE_FAILURE) else inner.read()
    }

    override suspend fun replaceSets(
        expectedRevision: Long,
        sets: PauseSets,
        syncWrite: PolicySyncWrite?,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return inner.replaceSets(expectedRevision, sets, syncWrite).also { result -> wrote = result is LocalPolicyResult.Success }
    }
}
