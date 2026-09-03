package app.posato.control.apple

import app.posato.control.backend.Interaction
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Actions
import app.posato.control.model.LaunchConfiguration
import app.posato.control.model.Query
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.model.Step
import app.posato.control.scenario.QueryMatcher

class IosInteraction(
    private val runner: IosDriverRunner,
    private val resolveUdid: () -> String,
    private val bundleId: String,
    private val resetState: () -> Unit,
) : Interaction {
    override fun snapshot(
        root: Query?,
        maxDepth: Int?
    ): SnapshotNode {
        val scenario = Scenario(
            launch = LaunchConfiguration(terminateExisting = false),
            steps = listOf(Step(action = Actions.SNAPSHOT, name = "tree", maxDepth = maxDepth)),
        )
        val run = runner.run(scenario, resolveUdid(), bundleId)
        val step = run.result.steps.firstOrNull()
        if (step == null || !step.ok) {
            val error = step?.error ?: run.result.error
            throw ControlException(ErrorCode.DRIVER_FAILED, error?.message ?: "The driver returned no snapshot.")
        }
        val node = run.snapshot("snapshot-0-tree")
            ?: throw ControlException(ErrorCode.DRIVER_FAILED, "The driver did not attach the snapshot file.")
        return root?.let { QueryMatcher.require(node, it) } ?: node
    }

    override fun runScenario(scenario: Scenario): RunResult {
        if (scenario.launch.fresh) resetState()
        return runner.run(scenario, resolveUdid(), bundleId).result
    }

    fun screenshotArtifact(name: String): String {
        val scenario = Scenario(
            launch = LaunchConfiguration(terminateExisting = false),
            steps = listOf(Step(action = Actions.SCREENSHOT, name = name)),
        )
        val run = runner.run(scenario, resolveUdid(), bundleId)
        val step = run.result.steps.firstOrNull()
        return step?.artifacts?.firstOrNull()?.takeIf { step.ok }
            ?: throw ControlException(
                ErrorCode.DRIVER_FAILED,
                step?.error?.message ?: run.result.error?.message ?: "The driver captured no screenshot.",
            )
    }
}
