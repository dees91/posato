package app.posato.control.scenario

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.model.States
import app.posato.control.model.Step
import app.posato.control.model.StepError
import app.posato.control.model.StepResult
import java.nio.file.Path

interface NativeActions {
    fun prepareInteraction() {}

    fun scrollTo(
        query: Query,
        timeoutMs: Long
    ) {
        throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "This native driver does not implement scrolling.")
    }

    fun snapshot(maxDepth: Int?): SnapshotNode

    fun tap(node: SnapshotNode)

    fun type(
        node: SnapshotNode,
        text: String,
        clear: Boolean,
        submit: Boolean
    )

    fun press(
        key: String,
        modifiers: List<String>
    )

    fun screenshot(name: String): Path

    fun writeSnapshot(
        name: String,
        node: SnapshotNode
    ): Path

    fun relaunch()

    fun terminate()
}

private const val SETTLE_INTERVAL_MS = 500L
private const val POLL_INTERVAL_MS = 250L
private const val MILLIS_PER_SECOND = 1000.0

class ScenarioRunner(
    private val actions: NativeActions,
    private val relativize: (Path) -> String,
) {
    fun run(scenario: Scenario): RunResult {
        val results = mutableListOf<StepResult>()
        scenario.steps.forEachIndexed { index, step ->
            val result = execute(index, step, scenario)
            results.add(result)
            if (!result.ok && !scenario.continueOnFailure) return RunResult(false, results, result.error)
        }
        return RunResult(results.all { it.ok }, results, null)
    }

    private fun execute(
        index: Int,
        step: Step,
        scenario: Scenario
    ): StepResult {
        val started = System.nanoTime()
        val artifacts = mutableListOf<String>()
        val error = try {
            perform(index, step, scenario, artifacts)
            null
        } catch (exception: ControlException) {
            captureFailure(index, scenario, artifacts)
            StepError(exception.code.name, exception.message ?: exception.code.name)
        }
        val durationMs = (System.nanoTime() - started) / NANOS_PER_MILLI
        return StepResult(index, step.name, step.action, error == null, durationMs, artifacts, error)
    }

    private fun perform(
        index: Int,
        step: Step,
        scenario: Scenario,
        artifacts: MutableList<String>
    ) {
        when (step.action) {
            Actions.SCREENSHOT -> artifacts.add(relativize(actions.screenshot(artifactName(index, step, "screenshot"))))
            Actions.SNAPSHOT -> artifacts.add(relativize(snapshotArtifact(index, step)))
            Actions.SLEEP -> Thread.sleep(((step.seconds ?: 0.0) * MILLIS_PER_SECOND).toLong())
            Actions.TERMINATE -> actions.terminate()
            Actions.RELAUNCH -> actions.relaunch()
            else -> interact(step, scenario)
        }
    }

    private fun interact(
        step: Step,
        scenario: Scenario
    ) {
        val timeoutMs = ((step.timeoutSeconds ?: scenario.defaults.timeoutSeconds) * MILLIS_PER_SECOND).toLong()
        if (step.action in setOf(Actions.TYPE, Actions.PRESS, Actions.SCROLL_TO)) {
            actions.prepareInteraction()
        }
        when (step.action) {
            Actions.WAIT_FOR -> waitFor(step, timeoutMs)
            Actions.TAP -> actions.tap(locate(step))
            Actions.TYPE -> actions.type(locate(step), requireField(step.text, "type needs text"), step.clear, step.submit)
            Actions.PRESS -> actions.press(requireField(step.key, "press needs a key"), step.modifiers)
            Actions.ASSERT -> assertState(step)
            Actions.SCROLL_TO -> actions.scrollTo(step.query ?: throw invalid("scrollTo needs a query"), timeoutMs)
            else -> throw invalid("Unknown action '${step.action}'.")
        }
    }

    private fun snapshotArtifact(
        index: Int,
        step: Step
    ): Path {
        val root = actions.snapshot(step.maxDepth)
        val node = step.query?.let { query -> QueryMatcher.require(root, query) } ?: root
        return actions.writeSnapshot(artifactName(index, step, "snapshot"), node)
    }

    private fun waitFor(
        step: Step,
        timeoutMs: Long
    ) {
        val state = step.state ?: throw invalid("waitFor needs a state")
        val deadline = System.currentTimeMillis() + timeoutMs
        var previous: SnapshotNode? = null
        while (true) {
            val root = actions.snapshot(null)
            val satisfied = if (state == States.SETTLED) {
                (previous == root).also { previous = root }
            } else {
                stateHolds(root, state, step.query)
            }
            if (satisfied) return
            if (System.currentTimeMillis() >= deadline) {
                throw ControlException(
                    ErrorCode.WAIT_TIMEOUT,
                    "Timed out after $timeoutMs ms waiting for $state ${step.query?.describe().orEmpty()}.",
                )
            }
            Thread.sleep(if (state == States.SETTLED) SETTLE_INTERVAL_MS else POLL_INTERVAL_MS)
        }
    }

    private fun assertState(step: Step) {
        val state = step.state ?: throw invalid("assert needs a state")
        if (!stateHolds(actions.snapshot(null), state, step.query)) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "Expected $state for ${step.query?.describe().orEmpty()}.")
        }
    }

    private fun stateHolds(
        root: SnapshotNode,
        state: String,
        query: Query?
    ): Boolean {
        val node = query?.let { QueryMatcher.find(root, it) }
        return when (state) {
            States.EXISTS -> node != null
            States.ABSENT -> node == null
            States.ENABLED -> node?.enabled == true
            States.DISABLED -> node?.enabled == false
            else -> throw invalid("Unknown state '$state'.")
        }
    }

    private fun locate(step: Step): SnapshotNode {
        val query = step.query ?: throw invalid("${step.action} needs a query")
        return QueryMatcher.require(actions.snapshot(null), query)
    }

    private fun captureFailure(
        index: Int,
        scenario: Scenario,
        artifacts: MutableList<String>
    ) {
        if (scenario.onFailure.screenshot) {
            runCatchingControl { artifacts.add(relativize(actions.screenshot("failure-$index-screenshot"))) }
        }
        if (scenario.onFailure.snapshot) {
            runCatchingControl { artifacts.add(relativize(actions.writeSnapshot("failure-$index-snapshot", actions.snapshot(null)))) }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

private fun runCatchingControl(block: () -> Unit) {
    try {
        block()
    } catch (_: ControlException) {
        // Failure evidence is best-effort; the original step error is what matters.
    }
}

private fun artifactName(
    index: Int,
    step: Step,
    kind: String
): String = "$kind-$index-" + (step.name ?: kind)

private fun invalid(message: String): ControlException = ControlException(ErrorCode.SCENARIO_INVALID, message)

private fun requireField(
    value: String?,
    message: String
): String = value ?: throw invalid(message)
