package app.posato.feature.session.ui

import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.schedules.domain.ScheduleDate
import app.posato.feature.schedules.domain.utc
import app.posato.feature.session.data.LocalSessionFailure
import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionActionRequired
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionSetupFailure
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationMappingsLoadResult
import app.posato.feature.targets.data.LocalApplicationMappingsSnapshot
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult
import kotlinx.collections.immutable.persistentListOf
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
        val policyStore = policyStoreOf()
        val mappings = FakeSessionMappings()
        val clock = FakeSessionClock(NOW)
        val enforcement = FakeEnforcementPort()
        SessionViewModel(
            policyStore,
            mappings,
            FakeSessionIdGenerator(),
            clock,
            FakeSessionTimeFormat(),
            sessionOwnerOf(store, enforcement, clock, policyStore, mappings, dispatcher = dispatcher),
            CentralEuropeanZone,
        )
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
        val policyStore = policyStoreOf()
        val mappings = FakeSessionMappings()
        val viewModel = SessionViewModel(
            policyStore,
            mappings,
            FakeSessionIdGenerator(),
            FakeSessionClock(NOW),
            FakeSessionTimeFormat(),
            sessionOwnerOf(FakeLocalSessionStore(), FakeEnforcementPort(), FakeSessionClock(NOW), policyStore, mappings, dispatcher = dispatcher),
            CentralEuropeanZone,
        )

        viewModel.setSetupVisible(true)

        assertFalse(viewModel.uiState.value.isSettingUp)
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
    fun `given a replaced session when confirming then the wrong session never ends`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"))
        startThroughUi(viewModel)
        val shown = assertIs<LocalSessionStatus.Active>(viewModel.uiState.value.status).record.sessionId
        viewModel.setEarlyEndConfirmation(true)
        scheduler.runCurrent()

        // The row is replaced while the dialog stays open.
        val replacement = SessionId(testIdentifier(21))
        assertIs<LocalSessionResult.Success<LocalSessionStatus>>(
            store.adopt(
                replacement,
                NOW,
                NOW + 30 * 60_000L,
                NOW,
                FrozenStartSet(persistentListOf("stable.example"), null),
            ),
        )
        viewModel.retry()
        scheduler.runCurrent()
        assertEquals(
            replacement,
            assertIs<LocalSessionStatus.Active>(viewModel.uiState.value.status).record.sessionId,
        )
        assertTrue(shown != replacement)

        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertFalse(state.confirmingEarlyEnd)
        assertEquals(0, store.endEarlyCalls)
        val current = assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(replacement, current.record.sessionId)
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

    @Test
    fun `given domains when starting then the frozen start set is persisted with the session`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"))
        startThroughUi(viewModel)

        assertIs<LocalSessionStatus.Active>(viewModel.uiState.value.status)
        assertEquals(FrozenStartSet(persistentListOf("stable.example"), null), store.frozenStartSet)
    }

    @Test
    fun `given a policy signal during setup when observed then targets refresh and the draft survives`() = runTest(dispatcher) {
        val policy = policyStoreOf(listOf("old.example"))
        val mappings = FakeSessionMappings()
        val clock = FakeSessionClock(NOW)
        val viewModel = SessionViewModel(
            policy,
            mappings,
            FakeSessionIdGenerator(),
            clock,
            FakeSessionTimeFormat(),
            sessionOwnerOf(FakeLocalSessionStore(), FakeEnforcementPort(), clock, policy, mappings, dispatcher = dispatcher),
            CentralEuropeanZone,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.uiState.collect() }
        viewModel.onScreenEntered()
        scheduler.runCurrent()
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()

        val refreshed = TargetPolicy.fromStoredValues(listOf("old.example", "new.example"), null)
        policy.result = LocalPolicyResult.Success(
            LocalTargetPolicyState(1, (refreshed as TargetPolicyValidationResult.Success).policy),
        )
        policy.changes.tryEmit(Unit)
        scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isSettingUp)
        assertEquals(listOf("new.example", "old.example"), viewModel.uiState.value.displayDomains())
    }

    @Test
    fun `given the end of day chosen when review comes after it is no longer offered then review refuses and continues with 25 minutes`() = runTest(dispatcher) {
        val clock = FakeSessionClock(BEFORE_MIDNIGHT)
        val viewModel = endOfDayChosen(clock)

        clock.nowEpochMillis = MIDNIGHT - 4 * MINUTE
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        val refused = viewModel.uiState.value
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()

        assertFalse(refused.isReviewing)
        assertEquals(SessionSetupFailure.TOO_SHORT, refused.setupFailure)
        assertEquals("formatted-${clock.nowEpochMillis + 25 * MINUTE}", viewModel.uiState.value.formattedReviewEnd)
    }

    @Test
    fun `given the end of day chosen when setup stays open past midnight then review does not move to the next midnight`() = runTest(dispatcher) {
        val clock = FakeSessionClock(BEFORE_MIDNIGHT)
        val viewModel = endOfDayChosen(clock)

        clock.nowEpochMillis = MIDNIGHT + 2 * MINUTE
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()

        assertEquals("formatted-${clock.nowEpochMillis + 25 * MINUTE}", viewModel.uiState.value.formattedReviewEnd)
    }

    @Test
    fun `given a reviewed end of day when start comes too late then no pause starts and setup continues with 25 minutes`() = runTest(dispatcher) {
        val clock = FakeSessionClock(BEFORE_MIDNIGHT)
        val viewModel = endOfDayChosen(clock)
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()

        clock.nowEpochMillis = MIDNIGHT - 4 * MINUTE
        viewModel.startSession()
        scheduler.runCurrent()
        val refused = viewModel.uiState.value
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()

        assertIs<LocalSessionStatus.Inactive>(refused.status)
        assertEquals(SessionSetupFailure.TOO_SHORT, refused.setupFailure)
        assertEquals("formatted-${clock.nowEpochMillis + 25 * MINUTE}", viewModel.uiState.value.formattedReviewEnd)
    }

    private fun TestScope.endOfDayChosen(clock: FakeSessionClock): SessionViewModel {
        val viewModel = collectedViewModel(clock = clock, domains = listOf("stable.example"))
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.chooseDuration(SessionDurationChoice.EndOfDay)
        scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.untilEndOfDay)

        return viewModel
    }

    private fun TestScope.collectedViewModel(
        store: FakeLocalSessionStore = FakeLocalSessionStore(),
        clock: FakeSessionClock = FakeSessionClock(NOW),
        domains: List<String> = emptyList(),
        groupName: String? = null,
        mappings: FakeSessionMappings = FakeSessionMappings(),
        enforcement: FakeEnforcementPort = FakeEnforcementPort(),
    ): SessionViewModel {
        val policyStore = policyStoreOf(domains, groupName)
        val owner = sessionOwnerOf(store, enforcement, clock, policyStore, mappings, dispatcher = dispatcher)
        backgroundScope.launch { owner.runWhileHosted() }
        val viewModel = SessionViewModel(
            policyStore,
            mappings,
            FakeSessionIdGenerator(),
            clock,
            FakeSessionTimeFormat(),
            owner,
            CentralEuropeanZone,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.uiState.collect() }
        viewModel.onScreenEntered()
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
        const val MINUTE: Long = 60_000L
        val MIDNIGHT: Long = utc(ScheduleDate(2026, 10, 9), 22 * 60)
        val BEFORE_MIDNIGHT: Long = MIDNIGHT - 8 * MINUTE

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
