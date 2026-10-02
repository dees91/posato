package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.model.Actions
import app.posato.control.model.LaunchConfiguration
import app.posato.control.model.Query
import app.posato.control.model.Scenario
import app.posato.control.model.ScenarioDefaults
import app.posato.control.model.SnapshotNode
import app.posato.control.model.States
import app.posato.control.model.Step

/** Small step builders the flow commands share, so each flow reads as the person's path through the app. */
internal object FlowSteps {
    fun tap(
        query: Query,
        optional: Boolean = false,
        timeoutSeconds: Double? = null,
    ): Step = Step(action = Actions.TAP, query = query, optional = optional, timeoutSeconds = timeoutSeconds)

    fun button(
        text: String,
        optional: Boolean = false,
        timeoutSeconds: Double? = null,
    ): Step = tap(Query(text = text, role = ROLE_BUTTON), optional, timeoutSeconds)

    fun reveal(query: Query): Step = Step(action = Actions.SCROLL_TO, query = query)

    fun waitFor(
        query: Query,
        state: String = States.EXISTS,
        timeoutSeconds: Double? = null,
    ): Step = Step(action = Actions.WAIT_FOR, state = state, query = query, timeoutSeconds = timeoutSeconds)

    fun typeInto(
        text: String,
        submit: Boolean,
    ): Step = Step(action = Actions.TYPE, query = Query(role = ROLE_TEXT_FIELD), text = text, clear = true, submit = submit)

    fun sleep(seconds: Double): Step = Step(action = Actions.SLEEP, seconds = seconds)

    fun screenshot(name: String): Step = Step(action = Actions.SCREENSHOT, name = name)

    /** Runs [steps] against the running application without relaunching it, failing on the first failed step. */
    fun run(
        backend: Backend,
        steps: List<Step>,
    ) {
        val scenario = Scenario(
            launch = LaunchConfiguration(terminateExisting = false),
            defaults = ScenarioDefaults(timeoutSeconds = DEFAULT_TIMEOUT_SECONDS),
            steps = steps,
        )
        failIfStepFailed(backend.runScenario(scenario))
    }

    /** Every accessibility label on screen, for reading a state such as "Starts 09:00" between rounds. */
    fun labels(backend: Backend): List<String> {
        val labels = mutableListOf<String>()
        backend.snapshot(null, null).walk { node: SnapshotNode -> node.label?.let(labels::add) }
        return labels
    }

    const val ROLE_BUTTON = "button"
    const val ROLE_TEXT_FIELD = "textField"
    private const val DEFAULT_TIMEOUT_SECONDS = 30.0
}
