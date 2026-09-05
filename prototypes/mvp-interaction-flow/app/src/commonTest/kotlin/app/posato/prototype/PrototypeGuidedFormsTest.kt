package app.posato.prototype

import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.OutcomeTone
import app.posato.prototype.model.PrototypeFixtures
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeScenario
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.SessionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PrototypeGuidedFormsTest {
    @Test
    fun `given pending duration step when form is submitted then duration and review both advance`() {
        for (input in listOf("25", "90")) {
            val viewModel = PrototypeViewModel(PrototypePlatform.Mac)
            viewModel.selectScenario(PrototypeScenario.FirstSession)
            repeat(8) { viewModel.runGuidedStep(it) }
            viewModel.reviewDuration(input)
            assertEquals(10, viewModel.uiState.value.progress)
            assertEquals(PrototypeSurface.SessionReview, viewModel.uiState.value.prototype.surface)
            viewModel.runGuidedStep(10)
            assertEquals(PrototypeSurface.Active, viewModel.uiState.value.prototype.surface)
        }
    }

    @Test
    fun `given pending duration step when invalid input is submitted then progress stays unchanged`() {
        val viewModel = PrototypeViewModel(PrototypePlatform.Mac)
        viewModel.selectScenario(PrototypeScenario.FirstSession)
        repeat(8) { viewModel.runGuidedStep(it) }
        viewModel.reviewDuration("4")
        assertEquals(8, viewModel.uiState.value.progress)
        assertEquals(PrototypeSurface.SessionSetup, viewModel.uiState.value.prototype.surface)
    }

    @Test
    fun `given mapping repair step when picker is saved then walkthrough continues to review`() {
        val viewModel = PrototypeViewModel(PrototypePlatform.IPhone)
        viewModel.selectScenario(PrototypeScenario.ActionRequired)
        repeat(5) { viewModel.runGuidedStep(it) }
        viewModel.dispatch(ItemAction.OpenApplications)
        viewModel.dispatch(ItemAction.SaveApplications(PrototypeFixtures.applications(PrototypePlatform.IPhone)))
        assertEquals(6, viewModel.uiState.value.progress)
        viewModel.dispatch(SessionAction.Review)
        assertEquals(7, viewModel.uiState.value.progress)
        viewModel.runGuidedStep(7)
        assertNotEquals(OutcomeTone.Blocked, viewModel.uiState.value.prototype.outcome.tone)
    }
}
