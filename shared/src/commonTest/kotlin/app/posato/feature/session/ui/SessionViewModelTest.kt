package app.posato.feature.session.ui

import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionSetupFailure
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given uiState without a collector when ViewModel is created then storage is not read`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        SessionViewModel(store, policyStoreOf(), FakeSessionMappings(), FakeSessionIdGenerator(), FakeSessionClock(NOW), FakeSessionTimeFormat())
        scheduler.runCurrent()

        assertTrue(store.reads.isEmpty())
    }

    @Test
    fun `given an empty store when uiState is collected then the status is inactive`() = runTest(dispatcher) {
        val viewModel = collectedViewModel()

        assertIs<LocalSessionStatus.Inactive>(viewModel.uiState.value.status)
    }

    @Test
    fun `given a loading status when entering setup then setup stays closed`() = runTest(dispatcher) {
        val viewModel = SessionViewModel(
            FakeLocalSessionStore(),
            policyStoreOf(),
            FakeSessionMappings(),
            FakeSessionIdGenerator(),
            FakeSessionClock(NOW),
            FakeSessionTimeFormat(),
        )

        viewModel.setSetupVisible(true)

        assertFalse(viewModel.uiState.value.isSettingUp)
    }

    @Test
    fun `given an inactive status when entering setup then setup opens`() = runTest(dispatcher) {
        val viewModel = collectedViewModel()

        viewModel.setSetupVisible(true)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSettingUp)
    }

    @Test
    fun `given setup when adjusting beyond bounds then the duration clamps`() = runTest(dispatcher) {
        val viewModel = collectedViewModel()
        viewModel.setSetupVisible(true)

        viewModel.adjustDuration(-1_000)
        scheduler.runCurrent()
        assertEquals(5, viewModel.uiState.value.durationMinutes)
        viewModel.adjustDuration(2_000)
        scheduler.runCurrent()
        assertEquals(1_440, viewModel.uiState.value.durationMinutes)
    }

    @Test
    fun `given setup when submitting invalid minutes then the prior value stays with a failure`() = runTest(dispatcher) {
        val viewModel = collectedViewModel()
        viewModel.setSetupVisible(true)

        viewModel.submitDurationMinutes("abc")
        scheduler.runCurrent()
        assertEquals(25, viewModel.uiState.value.durationMinutes)
        assertEquals(SessionSetupFailure.TOO_SHORT, viewModel.uiState.value.setupFailure)
        viewModel.submitDurationMinutes("3")
        scheduler.runCurrent()
        assertEquals(25, viewModel.uiState.value.durationMinutes)
        assertEquals(SessionSetupFailure.TOO_SHORT, viewModel.uiState.value.setupFailure)
        viewModel.submitDurationMinutes("1500")
        scheduler.runCurrent()
        assertEquals(25, viewModel.uiState.value.durationMinutes)
        assertEquals(SessionSetupFailure.TOO_LONG, viewModel.uiState.value.setupFailure)
        viewModel.submitDurationMinutes("45")
        scheduler.runCurrent()
        assertEquals(45, viewModel.uiState.value.durationMinutes)
        assertNull(viewModel.uiState.value.setupFailure)
    }

    @Test
    fun `given unchosen mappings with domains when entering review then starting stays allowed`() = runTest(dispatcher) {
        val mappings = FakeSessionMappings(
            LocalApplicationMappingsLoadResult.Success(
                LocalApplicationMappingsSnapshot.empty(),
                LocalApplicationMappingsAccess.READY,
            ),
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), groupName = "Social feeds", mappings = mappings)
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()

        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertEquals(SessionActionRequired.MAPPINGS_NOT_CHOSEN, state.review.actionRequired)
        assertTrue(state.canStart())
    }

    @Test
    fun `given required access with domains when entering review then starting stays allowed`() = runTest(dispatcher) {
        val mappings = FakeSessionMappings(
            LocalApplicationMappingsLoadResult.Success(
                LocalApplicationMappingsSnapshot.empty(),
                LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED,
            ),
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), groupName = "Social feeds", mappings = mappings)
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()

        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertEquals(SessionActionRequired.ACCESS_REQUIRED, state.review.actionRequired)
        assertTrue(state.canStart())
    }

    @Test
    fun `given no effective items when entering review then starting is refused`() = runTest(dispatcher) {
        val viewModel = collectedViewModel()
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()

        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertTrue(state.isReviewing)
        assertEquals(SessionActionRequired.NO_EFFECTIVE_ITEMS, state.review.actionRequired)
        assertFalse(state.canStart())
    }

    @Test
    fun `given domains when starting then the session becomes active and setup closes`() = runTest(dispatcher) {
        val viewModel = collectedViewModel(domains = listOf("stable.example"))
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.canStart())
        viewModel.startSession()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertFalse(state.isSettingUp)
        assertFalse(state.isReviewing)
        assertEquals("formatted-${NOW + 25 * 60_000L}", state.formattedActiveEnd)
    }

    @Test
    fun `given changed mappings when starting a stale review then the blocked start is refused`() = runTest(dispatcher) {
        val mappings = FakeSessionMappings()
        val store = FakeLocalSessionStore()
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"), mappings = mappings)
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.canStart())

        mappings.result = LocalApplicationMappingsLoadResult.Failure(LocalApplicationMappingsLoadFailure.STORAGE)
        viewModel.startSession()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertEquals(0, store.startCalls)
        assertTrue(state.isReviewing)
        assertEquals(SessionActionRequired.MAPPINGS_LOAD_FAILED, state.review.actionRequired)
        assertFalse(state.canStart())
    }

    @Test
    fun `given a failing store when starting then the start failure stays visible in review`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.startFailure = LocalSessionFailure.STORAGE_FAILURE
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"))
        startThroughUi(viewModel)
        val state = viewModel.uiState.value

        assertEquals(1, store.startCalls)
        assertEquals(SessionOperationFailure.START_FAILED, state.operationFailure)
        assertTrue(state.isReviewing)
    }

    @Test
    fun `given a failing store when confirming early end then the end failure stays visible`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"))
        startThroughUi(viewModel)
        store.endFailure = LocalSessionFailure.STORAGE_FAILURE
        viewModel.setEarlyEndConfirmation(true)
        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertEquals(SessionOperationFailure.END_FAILED, state.operationFailure)
        assertIs<LocalSessionStatus.Active>(state.status)
    }

    @Test
    fun `given an active session when entering setup then setup stays closed`() = runTest(dispatcher) {
        val viewModel = startedViewModel()

        viewModel.setSetupVisible(true)

        assertFalse(viewModel.uiState.value.isSettingUp)
    }

    @Test
    fun `given an active session when requesting early end then confirmation opens`() = runTest(dispatcher) {
        val viewModel = startedViewModel()

        viewModel.setEarlyEndConfirmation(true)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.confirmingEarlyEnd)
    }

    @Test
    fun `given confirmation when cancelling then the session stays active`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"))
        startThroughUi(viewModel)
        viewModel.setEarlyEndConfirmation(true)

        viewModel.setEarlyEndConfirmation(false)
        scheduler.runCurrent()

        assertFalse(viewModel.uiState.value.confirmingEarlyEnd)
        assertIs<LocalSessionStatus.Active>(viewModel.uiState.value.status)
        assertEquals(0, store.endEarlyCalls)
    }

    @Test
    fun `given confirmation when confirming then the session ends early`() = runTest(dispatcher) {
        val viewModel = startedViewModel()
        viewModel.setEarlyEndConfirmation(true)

        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        val ended = assertIs<LocalSessionStatus.Ended>(state.status)
        assertEquals(SessionEndKind.ENDED_EARLY, ended.kind)
        assertFalse(state.confirmingEarlyEnd)
    }

    @Test
    fun `given an ended session when entering setup then setup opens again`() = runTest(dispatcher) {
        val viewModel = startedViewModel()
        viewModel.setEarlyEndConfirmation(true)
        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()

        viewModel.setSetupVisible(true)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSettingUp)
    }

    @Test
    fun `given an active session when the end passes then the status expires without a second start`() = runTest(dispatcher) {
        val clock = FakeSessionClock(NOW)
        val store = FakeLocalSessionStore()
        val viewModel = collectedViewModel(store = store, clock = clock, domains = listOf("stable.example"))
        startThroughUi(viewModel, 5)

        clock.nowEpochMillis = NOW + 5 * 60_000L
        advanceTimeBy(2_000)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        val ended = assertIs<LocalSessionStatus.Ended>(state.status)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
        assertEquals(1, store.startCalls)
        assertEquals("formatted-${NOW + 5 * 60_000L}", state.formattedActiveEnd)
    }

    private fun TestScope.collectedViewModel(
        store: FakeLocalSessionStore = FakeLocalSessionStore(),
        clock: FakeSessionClock = FakeSessionClock(NOW),
        domains: List<String> = emptyList(),
        groupName: String? = null,
        mappings: FakeSessionMappings = FakeSessionMappings(),
    ): SessionViewModel {
        val viewModel = SessionViewModel(
            store,
            policyStoreOf(domains, groupName),
            mappings,
            FakeSessionIdGenerator(),
            clock,
            FakeSessionTimeFormat(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.uiState.collect() }
        scheduler.runCurrent()

        return viewModel
    }

    private fun startThroughUi(
        viewModel: SessionViewModel,
        durationMinutes: Int = 30,
    ) {
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.submitDurationMinutes(durationMinutes.toString())
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        viewModel.startSession()
        scheduler.runCurrent()
    }

    private fun TestScope.startedViewModel(): SessionViewModel {
        val viewModel = collectedViewModel(domains = listOf("stable.example"))
        startThroughUi(viewModel)

        return viewModel
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L

        fun policyStoreOf(
            domains: List<String> = emptyList(),
            groupName: String? = null,
        ): FakeSessionPolicyStore {
            val result = TargetPolicy.fromStoredValues(domains, groupName)
            val policy = (result as TargetPolicyValidationResult.Success).policy

            return FakeSessionPolicyStore(LocalPolicyResult.Success(LocalTargetPolicyState(0, policy)))
        }
    }
}
