package app.posato.feature.session.ui

import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.schedules.domain.CentralEuropeanZone
import app.posato.feature.session.domain.FakeSessionClock
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.testIdentifier
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A session pauses the set it was started with: Review lists it, the session records it, and enforcement applies it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPauseSetTest {
    private val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val work = checkNotNull(PauseSetId.of(testIdentifier(80)))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a chosen set other than the first when started then the session records and pauses that set`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort()
        val viewModel = viewModelOf(store, enforcement, defaultSetId = null)

        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.choosePauseSet(work)
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        assertEquals(listOf("work.example"), viewModel.uiState.value.review.domains)
        viewModel.startSession()
        scheduler.runCurrent()

        assertIs<LocalSessionStatus.Active>(viewModel.uiState.value.status)
        assertEquals(work, store.record?.setId)
        assertEquals(listOf("work.example"), enforcement.lastRequest?.domains)
    }

    @Test
    fun `given a session running on a set other than the default when the screen reloads then it shows that set`() = runTest(dispatcher) {
        val viewModel = viewModelOf(FakeLocalSessionStore(), FakeEnforcementPort(), defaultSetId = null)
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.choosePauseSet(work)
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        viewModel.startSession()
        scheduler.runCurrent()

        viewModel.onScreenEntered()
        scheduler.runCurrent()

        assertEquals(listOf("work.example"), viewModel.uiState.value.review.domains)
    }

    @Test
    fun `given a running session whose set gained a website when recomposed then it is applied without clearing first`() = runTest(dispatcher) {
        val store = FakeLocalSessionStore()
        val enforcement = FakeEnforcementPort()
        val policyStore = FakeSessionPolicyStore(LocalPolicyResult.Success(LocalTargetPolicyState(0, setsWith("work"), null)))
        val mappings = FakeSessionMappings()
        val composition = SessionComposition(null, PauseLimits.IPHONE, { setId -> loadSessionTargets(policyStore, mappings, setId) })
        val owner =
            sessionOwnerOf(store, enforcement, FakeSessionClock(NOW), policyStore, mappings, dispatcher = dispatcher, composition = composition)
        backgroundScope.launch { owner.runWhileHosted() }
        val viewModel = SessionViewModel(
            policyStore,
            mappings,
            FakeSessionIdGenerator(),
            FakeSessionClock(NOW),
            FakeSessionTimeFormat(),
            owner,
            CentralEuropeanZone,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.uiState.collect {} }
        viewModel.onScreenEntered()
        scheduler.runCurrent()
        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.choosePauseSet(work)
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()
        viewModel.startSession()
        scheduler.runCurrent()
        policyStore.result = LocalPolicyResult.Success(LocalTargetPolicyState(1, setsWith("work", "added"), null))
        enforcement.calls.clear()

        owner.recompose(composition)
        scheduler.runCurrent()

        // On iPhone the manual store is the record of what the session holds, so it is never cleared first.
        assertEquals(listOf("apply"), enforcement.calls.filter { call -> call == "apply" || call == "clear" })
        assertEquals(listOf("added.example", "work.example"), enforcement.lastRequest?.domains)
    }

    private fun setsWith(vararg workLabels: String): PauseSets {
        return checkNotNull(
            PauseSets.of(
                listOf(LocalPauseSet(PauseSetId.FIRST, null, listOf(domain("first"))), LocalPauseSet(work, "Work", workLabels.map(::domain))),
                null,
            ),
        )
    }

    @Test
    fun `given no choice when reviewing then the default set is reviewed`() = runTest(dispatcher) {
        val viewModel = viewModelOf(FakeLocalSessionStore(), FakeEnforcementPort(), defaultSetId = work)

        viewModel.setSetupVisible(true)
        scheduler.runCurrent()
        viewModel.setReviewVisible(true)
        scheduler.runCurrent()

        assertEquals(listOf("work.example"), viewModel.uiState.value.review.domains)
    }

    private fun TestScope.viewModelOf(
        store: FakeLocalSessionStore,
        enforcement: FakeEnforcementPort,
        defaultSetId: PauseSetId?,
    ): SessionViewModel {
        val sets = checkNotNull(
            PauseSets.of(
                listOf(
                    LocalPauseSet(PauseSetId.FIRST, null, listOf(domain("first"))),
                    LocalPauseSet(work, "Work", listOf(domain("work"))),
                ),
                defaultSetId,
            ),
        )
        val policyStore = FakeSessionPolicyStore(LocalPolicyResult.Success(LocalTargetPolicyState(0, sets, null)))
        val mappings = FakeSessionMappings()
        val clock = FakeSessionClock(NOW)
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
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.uiState.collect {} }
        viewModel.onScreenEntered()
        scheduler.runCurrent()
        return viewModel
    }

    private fun domain(label: String): ExactDomain {
        return checkNotNull(ExactDomain.restore("$label.example"))
    }

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
    }
}
