package app.posato.prototype.model

import app.posato.prototype.PrototypeViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrototypeLongListTest {
    @Test
    fun `given long list moment when selected then both platforms have deterministic editable items`() {
        for (platform in PrototypePlatform.entries) {
            val state = PrototypeMoment.LongList.state(platform)
            assertEquals(50, state.policy.domains.size)
            assertEquals(50, state.policy.domains.distinct().size)
            assertEquals("reading-01.example", state.policy.domains.first())
            assertEquals("reading-50.example", state.policy.domains.last())
            assertEquals(PrototypeFixtures.applications(platform), state.localApplications())
            assertEquals(54, state.effectiveItemCount())
            assertFalse(state.session.active)
            assertTrue(state.reviewIssues().isEmpty())
            val items = reducePrototype(state, ItemAction.OpenItems)
            val removed = reducePrototype(items, ItemAction.RemoveDomain("reading-50.example"))
            assertEquals(49, removed.policy.domains.size)
            assertEquals(50, state.policy.domains.size)
        }
    }

    @Test
    fun `given long list session when started and reset then the default ready fixture stays small`() {
        for (platform in PrototypePlatform.entries) {
            val viewModel = PrototypeViewModel(platform)
            viewModel.selectMoment(PrototypeMoment.LongList)
            viewModel.dispatch(SessionAction.OpenSetup)
            viewModel.reviewDuration("45")
            viewModel.dispatch(SessionAction.Start)
            assertTrue(viewModel.uiState.value.prototype.session.active)
            assertEquals(54, viewModel.uiState.value.prototype.effectiveItemCount())
            viewModel.selectMoment(PrototypeMoment.Ready)
            assertEquals(listOf(PrototypeFixtures.DOMAIN), viewModel.uiState.value.prototype.policy.domains)
            assertFalse(viewModel.uiState.value.prototype.session.active)
        }
    }
}
