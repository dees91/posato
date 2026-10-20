package app.posato.feature.targets.ui

import app.posato.core.database.PosatoDatabase
import app.posato.feature.onboarding.data.LocalSetupResult
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.host.ScheduledPause
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.ui.FakeSessionMappings
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.choiceSet
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
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
import kotlin.test.assertTrue

/** Duplicate makes a new set with the same websites and this device's apps, within the limit of ten sets. */
@OptIn(ExperimentalCoroutinesApi::class)
class PauseSetDuplicateTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))
    private val copy = checkNotNull(PauseSetId.of(testIdentifier(81)))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a set with websites and apps when duplicated then the copy has its websites and this device's apps`() = runTest(dispatcher) {
        val mappings = FakeSessionMappings()
        withStore("duplicate-set.db", extraSets = 0) { store ->
            val viewModel = PauseSetsViewModel(inputsOf(store, mappings))
            advanceUntilIdle()

            viewModel.duplicate(work)
            advanceUntilIdle()

            val duplicated = store.sets().sets.single { set -> set.id == copy }
            assertEquals("Work (copy)", duplicated.name)
            assertEquals(listOf("news.example", "video.example"), duplicated.domains.map(ExactDomain::canonicalValue))
            assertEquals(PauseSetId.FIRST, store.sets().resolvedDefault())
            // Once before the set exists and once after, in case a merge between them dropped the first copy.
            assertEquals(List(2) { work.choiceSet() to copy.choiceSet() }, mappings.copies)
        }
    }

    @Test
    fun `given the first set without a name when duplicated then the copy is named after its shown name`() = runTest(dispatcher) {
        withStore("duplicate-first.db", extraSets = 0) { store ->
            val viewModel = PauseSetsViewModel(inputsOf(store, FakeSessionMappings()))
            advanceUntilIdle()

            viewModel.duplicate(PauseSetId.FIRST)
            advanceUntilIdle()

            assertEquals("My set (copy)", store.sets().sets.single { set -> set.id == copy }.name)
        }
    }

    @Test
    fun `given ten sets when duplicating then nothing is added and no apps are copied`() = runTest(dispatcher) {
        val mappings = FakeSessionMappings()
        withStore("duplicate-full.db", extraSets = 8) { store ->
            val viewModel = PauseSetsViewModel(inputsOf(store, mappings))
            advanceUntilIdle()

            viewModel.duplicate(work)
            advanceUntilIdle()

            assertEquals(10, store.sets().sets.size)
            assertTrue(mappings.copies.isEmpty())
        }
    }

    @Test
    fun `given a name that needs shortening when copied then whole characters are kept and the name stays valid`() {
        val name = "ab" + "\uD83D\uDE00".repeat(18)

        val copied = copyNameOf(name)

        assertTrue(copied.encodeToByteArray().size <= 80)
        assertTrue(copied.endsWith(" (copy)"))
        assertTrue(ScheduleWireRules.isValidName(copied))
    }

    private fun inputsOf(
        store: SqlLocalTargetPolicyStore,
        mappings: FakeSessionMappings,
    ): PauseSetsInputs {
        return PauseSetsInputs(
            store = store,
            applicationMappings = mappings,
            schedules = schedules,
            sessionStatus = MutableStateFlow<LocalSessionStatus?>(LocalSessionStatus.Inactive),
            scheduledPause = MutableStateFlow<ScheduledPause?>(null),
            setupStore = NoticeAlreadyShown,
            linked = { false },
            ids = { copy },
        )
    }

    private lateinit var schedules: SqlScheduleStore

    private suspend fun withStore(
        name: String,
        extraSets: Int,
        block: suspend (SqlLocalTargetPolicyStore) -> Unit,
    ) {
        val testDatabase = createLocalPolicyTestDatabase(name)
        val driver = testDatabase.openDriver()
        try {
            val database = PosatoDatabase(driver)
            val store = SqlLocalTargetPolicyStore(database, dispatcher)
            schedules = SqlScheduleStore(database, dispatcher)
            val revision = (store.read() as LocalPolicyResult.Success<LocalTargetPolicyState>).value.revision
            val domains = listOf("news.example", "video.example").map { domain -> checkNotNull(ExactDomain.restore(domain)) }
            val extras = (1..extraSets).map { index ->
                LocalPauseSet(checkNotNull(PauseSetId.of(testIdentifier(90 + index))), "Set $index", emptyList())
            }
            val sets = listOf(LocalPauseSet(PauseSetId.FIRST, null, emptyList()), LocalPauseSet(work, "Work", domains)) + extras
            store.replaceSets(revision, checkNotNull(PauseSets.of(sets, null)))
            block(store)
        } finally {
            driver.close()
            testDatabase.delete()
        }
    }

    private suspend fun SqlLocalTargetPolicyStore.sets(): PauseSets {
        return (read() as LocalPolicyResult.Success<LocalTargetPolicyState>).value.sets
    }
}

private object NoticeAlreadyShown : LocalSetupStore {
    override suspend fun read(): LocalSetupResult<SetupCompletion> = LocalSetupResult.Success(SetupCompletion.COMPLETE)

    override suspend fun markComplete(): LocalSetupResult<Unit> = LocalSetupResult.Success(Unit)

    override suspend fun readPauseSetNoticeShown(): LocalSetupResult<Boolean> = LocalSetupResult.Success(true)

    override suspend fun markPauseSetNoticeShown(): LocalSetupResult<Unit> = LocalSetupResult.Success(Unit)
}
