package app.posato.prototype

import androidx.lifecycle.ViewModel
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.OutcomeTone
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeFixtures
import app.posato.prototype.model.PrototypeMoment
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeScenario
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.RecoveryAction
import app.posato.prototype.model.SessionAction
import app.posato.prototype.model.SetDuration
import app.posato.prototype.model.SetupAction
import app.posato.prototype.model.reducePrototype
import app.posato.prototype.model.runPrototypeFreePlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PrototypeUiState(
    val prototype: PrototypeState,
    val scenario: PrototypeScenario? = null,
    val progress: Int = 0,
    val resetKey: Int = 0
)

class PrototypeViewModel(
    platform: PrototypePlatform
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PrototypeUiState(PrototypeFixtures.ready(platform)))
    val uiState: StateFlow<PrototypeUiState> = mutableUiState.asStateFlow()

    fun control(control: PrototypeControl) {
        when (control) {
            is PrototypeControl.Moment -> selectMoment(control.moment)
            is PrototypeControl.Scenario -> selectScenario(control.scenario)
            is PrototypeControl.GuidedStep -> runGuidedStep(control.index)
            is PrototypeControl.FreePlay -> runFreePlay(control.action)
        }
    }

    fun dispatch(action: PrototypeAction) {
        mutableUiState.update { current ->
            val next = reducePrototype(current.prototype, action)
            val expected = current.scenario?.steps()?.getOrNull(current.progress)
            val completed = next.outcome.tone != OutcomeTone.Blocked && matchesGuidedAction(action, expected, current.prototype, next)
            current.copy(prototype = next, progress = current.progress + if (completed) 1 else 0)
        }
    }

    fun reviewDuration(input: String) {
        mutableUiState.update { current ->
            val selected = reducePrototype(current.prototype, SetDuration(input))
            val next = if (selected.outcome.tone == OutcomeTone.Blocked) selected else reducePrototype(selected, SessionAction.Review)
            val progress = if (next.outcome.tone == OutcomeTone.Blocked) current.progress else durationFormProgress(current)
            current.copy(prototype = next, progress = progress)
        }
    }

    fun selectMoment(moment: PrototypeMoment) {
        mutableUiState.update { current -> PrototypeUiState(moment.state(current.prototype.platform), resetKey = current.resetKey + 1) }
    }

    fun selectScenario(scenario: PrototypeScenario) {
        mutableUiState.update { current ->
            PrototypeUiState(scenario.start(current.prototype.platform), scenario, resetKey = current.resetKey + 1)
        }
    }

    fun runGuidedStep(index: Int) {
        mutableUiState.update { current ->
            val scenario = current.scenario
            val action = scenario?.steps()?.getOrNull(index)
            if (action == null || index != current.progress) return@update current
            val next = reducePrototype(current.prototype, action)
            val expectedResult = (next.outcome.tone == OutcomeTone.Blocked) == scenario.expectsBlocked(index)
            current.copy(prototype = next, progress = current.progress + if (expectedResult) 1 else 0)
        }
    }

    fun runFreePlay(action: PrototypeAction) {
        mutableUiState.update { current ->
            PrototypeUiState(runPrototypeFreePlay(current.prototype, action), resetKey = current.resetKey + 1)
        }
    }
}

private fun matchesGuidedAction(
    action: PrototypeAction,
    expected: PrototypeAction?,
    before: PrototypeState,
    after: PrototypeState
): Boolean {
    val submittedEditor = before.surface != after.surface

    return when {
        expected == null -> false

        action == expected -> true

        action is ItemAction.SaveDomain && expected == SetupAction.AddExampleDomain -> submittedEditor

        action is ItemAction.SaveApplications && expected in setOf(
            SetupAction.MapExampleApplication,
            RecoveryAction.RemapApplication,
        ) -> submittedEditor

        action is SetDuration && expected is SetDuration -> true

        else -> false
    }
}

private fun durationFormProgress(state: PrototypeUiState): Int {
    val steps = state.scenario?.steps().orEmpty()
    val afterDuration = state.progress + if (steps.getOrNull(state.progress) is SetDuration) 1 else 0

    return afterDuration + if (steps.getOrNull(afterDuration) == SessionAction.Review) 1 else 0
}
