package app.posato.prototype.model

import app.posato.prototype.PrototypeViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PrototypeWorkbenchTest {
    @Test
    fun `given each guided scenario when run on either platform then only intentional steps are blocked`() {
        for (platform in PrototypePlatform.entries) {
            for (scenario in PrototypeScenario.entries) {
                var state = scenario.start(platform)
                scenario.steps().forEachIndexed { index, action ->
                    state = reducePrototype(state, action)
                    assertEquals(scenario.expectsBlocked(index), state.outcome.tone == OutcomeTone.Blocked, "$platform $scenario $index $action")
                }
                assertEquals(platform, state.platform)
                assertNotEquals(OutcomeTone.Blocked, state.outcome.tone)
            }
        }
    }

    @Test
    fun `given reset when any listed Free play action is run then prerequisites are prepared`() {
        for (platform in PrototypePlatform.entries) {
            for (action in prototypeFreePlayGroups.flatMap { it.actions }) {
                val state = runPrototypeFreePlay(PrototypeState(platform), action)
                assertNotEquals(OutcomeTone.Blocked, state.outcome.tone, "$platform $action")
                assertEquals(platform, state.platform)
            }
        }
    }

    @Test
    fun `given waiting workspace when finish is attempted then no parallel workspace is created`() {
        val before = PrototypeFixtures.waiting(PrototypePlatform.IPhone)
        val after = reducePrototype(before, SetupAction.FinishOnboarding)
        assertEquals(before.workspace, after.workspace)
        assertEquals(before.policy, after.policy)
        assertEquals(OutcomeTone.Blocked, after.outcome.tone)
    }

    @Test
    fun `given failed sync after early end when retry succeeds then pending stop stays local`() {
        val active = PrototypeFixtures.active(PrototypePlatform.Mac)
        val ended = reducePrototype(reducePrototype(active, SessionAction.RequestEarlyEnd), SessionAction.ConfirmEarlyEnd)
        val failed = reducePrototype(reducePrototype(ended, SyncAction.Start), SyncAction.Fail)
        assertEquals(ended.policy, failed.policy)
        assertFalse(failed.session.active)
        assertTrue(failed.sync.pendingWork)
        val completed = reducePrototype(reducePrototype(failed, SyncAction.Retry), SyncAction.Succeed)
        assertFalse(completed.sync.pendingWork)
        assertFalse(completed.session.active)
        assertEquals(PrototypeClock.NOW, completed.sync.lastCompletedOnDevice)
    }

    @Test
    fun `given walkthrough progress when changing scenario then model and progress reset`() {
        val viewModel = PrototypeViewModel(PrototypePlatform.Mac)
        viewModel.selectScenario(PrototypeScenario.FirstSession)
        viewModel.runGuidedStep(0)
        assertEquals(1, viewModel.uiState.value.progress)
        viewModel.runGuidedStep(3)
        assertEquals(1, viewModel.uiState.value.progress)
        viewModel.selectScenario(PrototypeScenario.EarlyEnd)
        assertEquals(0, viewModel.uiState.value.progress)
        assertEquals(PrototypeSurface.Active, viewModel.uiState.value.prototype.surface)
        assertEquals(2, viewModel.uiState.value.resetKey)
        viewModel.selectMoment(PrototypeMoment.Ready)
        assertEquals(null, viewModel.uiState.value.scenario)
        assertEquals(PrototypeSurface.Home, viewModel.uiState.value.prototype.surface)
    }

    @Test
    fun `given strict user actions when setup is incomplete then session and item changes are blocked`() {
        val before = PrototypeState(PrototypePlatform.Mac)
        for (action in listOf(
            SessionAction.Start,
            SessionAction.OpenSetup,
            ItemAction.OpenItems,
            ItemAction.OpenApplications,
            ItemAction.SaveDomain("reading.example"),
            SyncAction.Succeed,
        )) {
            val after = reducePrototype(before, action)
            assertEquals(OutcomeTone.Blocked, after.outcome.tone)
            assertEquals(before.policy, after.policy)
            assertEquals(before.session, after.session)
        }
    }
}
