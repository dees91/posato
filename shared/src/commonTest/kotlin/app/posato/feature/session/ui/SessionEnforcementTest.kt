package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.enforcement.reconciliationId
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.FrozenStartSet
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
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
import kotlin.test.assertTrue

class SessionEnforcementTest {
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
    fun `given a failed apply when starting then the session stays active with action required`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(
            applyReport = EnforcementApplyReport(EnforcementOutcome.FAILED, false, true),
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        val action = assertIs<EnforcementState.ActionRequired>(state.enforcement)
        assertEquals(EnforcementActionKind.APPLY_FAILED, action.kind)
        assertTrue(action.repeatsSystemPrompt)
        assertTrue(state.canRequestEarlyEnd())
        assertEquals(listOf("stable.example"), state.displayDomains())
        assertTrue(state.nothingIsRestricted())
    }

    @Test
    fun `given an unknown apply when starting then the session stays active with action required`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(
            applyReport = EnforcementApplyReport(EnforcementOutcome.UNKNOWN, false, true),
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertIs<EnforcementState.ActionRequired>(state.enforcement)
    }

    @Test
    fun `given a failed apply when retrying then clear runs before re-apply`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(
            applyReport = EnforcementApplyReport(EnforcementOutcome.FAILED, false, false),
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        enforcement.applyReport = EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)

        viewModel.retryEnforcement()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertEquals(listOf("apply", "clear", "apply"), enforcement.calls)
        assertEquals(EnforcementState.Active(false), state.enforcement)
    }

    @Test
    fun `given a failed apply when ending early then the end is clean`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(
            applyReport = EnforcementApplyReport(EnforcementOutcome.FAILED, false, false),
            clearOutcome = EnforcementOutcome.UNAVAILABLE,
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        viewModel.setEarlyEndConfirmation(true)
        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Ended>(state.status)
        assertEquals(EnforcementState.Inactive, state.enforcement)
    }

    @Test
    fun `given a failed clear when ending early then the end stays action required`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(clearOutcome = EnforcementOutcome.FAILED)
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        viewModel.setEarlyEndConfirmation(true)
        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Ended>(state.status)
        val action = assertIs<EnforcementState.ActionRequired>(state.enforcement)
        assertEquals(EnforcementActionKind.CLEAR_FAILED, action.kind)
    }

    @Test
    fun `given an active enforced session when the end passes then expiry is marked and enforcement is cleared`() = runTest(dispatcher) {
        val clock = FakeSessionClock(NOW)
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort()
        val viewModel = collectedViewModel(store = store, clock = clock, domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel, 5)

        clock.nowEpochMillis = NOW + 5 * 60_000L
        advanceTimeBy(2_000)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        val ended = assertIs<LocalSessionStatus.Ended>(state.status)
        assertEquals(SessionEndKind.EXPIRED, ended.kind)
        assertTrue(store.expiryMarked)
        assertTrue(enforcement.calls.contains("clear"))
        assertEquals(EnforcementState.Inactive, state.enforcement)
    }

    @Test
    fun `given a below minimum schedule when starting then the active state names the limit`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(
            applyReport = EnforcementApplyReport(EnforcementOutcome.APPLIED, true, false),
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        val state = viewModel.uiState.value

        assertEquals(EnforcementState.Active(true), state.enforcement)
    }

    @Test
    fun `given a cleared status on relaunch when prompts are required then resume is required`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.record = SessionRecord(SessionId(testIdentifier(7)), NOW - 600_000L, NOW + 1_200_000L)
        val enforcement = FakeEnforcementPort(
            statusOutcome = EnforcementOutcome.CLEARED,
            reapplyRequiresPrompt = true,
        )
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"), enforcement = enforcement)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        val action = assertIs<EnforcementState.ActionRequired>(state.enforcement)
        assertEquals(EnforcementActionKind.RESUME_REQUIRED, action.kind)

        viewModel.retryEnforcement()
        scheduler.runCurrent()

        assertEquals(EnforcementState.Active(false), viewModel.uiState.value.enforcement)
    }

    @Test
    fun `given a cleared status on relaunch when prompts are not required then enforcement silently re-applies`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.record = SessionRecord(SessionId(testIdentifier(11)), NOW - 600_000L, NOW + 1_200_000L)
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"), enforcement = enforcement)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(listOf("poll", "status", "clear", "apply"), enforcement.calls)
        assertEquals(EnforcementState.Active(false), state.enforcement)
    }

    @Test
    fun `given lost enforcement when the status poll runs then enforcement silently re-applies`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort()
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        enforcement.statusOutcome = EnforcementOutcome.CLEARED

        advanceTimeBy(15_000)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertEquals(listOf("apply", "status", "clear", "apply"), enforcement.calls)
        assertEquals(EnforcementState.Active(false), state.enforcement)
    }

    @Test
    fun `given transient unknown polls when the limit is not reached then the session stays active`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(
            statusSequence = ArrayDeque(
                listOf(
                    EnforcementOutcome.UNKNOWN,
                    EnforcementOutcome.UNKNOWN,
                    EnforcementOutcome.UNKNOWN,
                ),
            ),
            reapplyRequiresPrompt = true,
        )
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)

        advanceTimeBy(15_000)
        scheduler.runCurrent()
        assertEquals(EnforcementState.Active(false), viewModel.uiState.value.enforcement)

        advanceTimeBy(15_000)
        scheduler.runCurrent()
        assertEquals(EnforcementState.Active(false), viewModel.uiState.value.enforcement)

        advanceTimeBy(15_000)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        val action = assertIs<EnforcementState.ActionRequired>(state.enforcement)
        assertEquals(EnforcementActionKind.APPLY_FAILED, action.kind)
    }

    @Test
    fun `given a pending failed clear when starting then the previous restrictions are cleared first`() = runTest(dispatcher) {
        val enforcement = FakeEnforcementPort(clearOutcome = EnforcementOutcome.FAILED)
        val viewModel = collectedViewModel(domains = listOf("stable.example"), enforcement = enforcement)
        startThroughUi(viewModel)
        viewModel.setEarlyEndConfirmation(true)
        viewModel.confirmEarlyEnd()
        scheduler.runCurrent()
        assertIs<EnforcementState.ActionRequired>(viewModel.uiState.value.enforcement)

        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.submitDurationMinutes(30.toString())
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        viewModel.startSession()
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(EnforcementState.Active(false), state.enforcement)
        assertEquals(listOf("apply", "clear", "clear", "apply"), enforcement.calls)
    }

    @Test
    fun `given an expired reconciliation when entering then enforcement is cleared`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val sessionId = SessionId(testIdentifier(9))
        store.record = SessionRecord(sessionId, NOW - 600_000L, NOW + 1_200_000L)
        val enforcement = FakeEnforcementPort(expiredSessionIds = setOf(sessionId.reconciliationId()))
        val viewModel = collectedViewModel(store = store, domains = listOf("stable.example"), enforcement = enforcement)
        scheduler.runCurrent()

        assertTrue(enforcement.calls.contains("clear"))
    }

    @Test
    fun `given a persisted frozen set when relaunching with edited policy then the summary keeps the frozen set`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.record = SessionRecord(SessionId(testIdentifier(21)), NOW - 600_000L, NOW + 1_200_000L)
        store.frozenStartSet = FrozenStartSet(persistentListOf("frozen.example"), 1)
        val viewModel = collectedViewModel(store = store, domains = listOf("edited.example"))
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(listOf("frozen.example"), state.displayDomains())
        assertEquals(1, state.displayApplicationCount())
        assertTrue(state.showsFrozenSet())
        assertTrue(state.showsPersistedStartSet())
    }

    @Test
    fun `given a persisted frozen set when relaunching with cleared enforcement then reapply uses the current policy`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.record = SessionRecord(SessionId(testIdentifier(22)), NOW - 600_000L, NOW + 1_200_000L)
        store.frozenStartSet = FrozenStartSet(persistentListOf("frozen.example"), 1)
        val enforcement = FakeEnforcementPort(statusOutcome = EnforcementOutcome.CLEARED)
        val viewModel = collectedViewModel(
            store = store,
            domains = listOf("edited.example"),
            enforcement = enforcement,
        )
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(listOf("frozen.example"), state.displayDomains())
        assertEquals(listOf("edited.example"), enforcement.lastRequest?.domains)
        assertEquals(EnforcementState.Active(false), state.enforcement)
    }

    @Test
    fun `given no persisted set when relaunching then the summary falls back to the live policy`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.record = SessionRecord(SessionId(testIdentifier(23)), NOW - 600_000L, NOW + 1_200_000L)
        val viewModel = collectedViewModel(store = store, domains = listOf("edited.example"))
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(listOf("edited.example"), state.displayDomains())
        assertTrue(state.showsFrozenSet())
        assertFalse(state.showsPersistedStartSet())
    }

    @Test
    fun `given a persisted set when reconciliation fails then the summary still shows the frozen set`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        store.record = SessionRecord(SessionId(testIdentifier(24)), NOW - 600_000L, NOW + 1_200_000L)
        store.frozenStartSet = FrozenStartSet(persistentListOf("frozen.example"), 1)
        val enforcement = FakeEnforcementPort(statusError = IllegalStateException("status lost"))
        val viewModel = collectedViewModel(store = store, domains = listOf("edited.example"), enforcement = enforcement)
        scheduler.runCurrent()
        val state = viewModel.uiState.value

        assertIs<LocalSessionStatus.Active>(state.status)
        assertEquals(listOf("frozen.example"), state.displayDomains())
        assertEquals(1, state.displayApplicationCount())
        assertTrue(state.showsPersistedStartSet())
        val action = assertIs<EnforcementState.ActionRequired>(state.enforcement)
        assertEquals(EnforcementActionKind.APPLY_FAILED, action.kind)
    }

    private fun TestScope.collectedViewModel(
        store: FakeLocalSessionStore = FakeLocalSessionStore(),
        clock: FakeSessionClock = FakeSessionClock(NOW),
        domains: List<String> = emptyList(),
        enforcement: FakeEnforcementPort = FakeEnforcementPort(),
    ): SessionViewModel {
        val policyStore = policyStoreOf(domains)
        val mappings = FakeSessionMappings()
        val owner = sessionOwnerOf(store, enforcement, clock, policyStore, mappings, dispatcher = dispatcher)
        val viewModel = SessionViewModel(
            policyStore,
            mappings,
            FakeSessionIdGenerator(),
            clock,
            FakeSessionTimeFormat(),
            owner,
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

    private companion object {
        const val NOW: Long = 1_000_000_000_000L

        fun policyStoreOf(domains: List<String>): FakeSessionPolicyStore {
            val result = TargetPolicy.fromStoredValues(domains, null)
            val policy = (result as TargetPolicyValidationResult.Success).policy

            return FakeSessionPolicyStore(LocalPolicyResult.Success(LocalTargetPolicyState(0, policy)))
        }
    }
}
