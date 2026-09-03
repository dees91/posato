package app.posato.control.desktop

import app.posato.control.backend.Interaction
import app.posato.control.backend.LaunchOptions
import app.posato.control.backend.Lifecycle
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.RunStateStore
import app.posato.control.model.Query
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.scenario.NativeActions
import app.posato.control.scenario.QueryMatcher
import app.posato.control.scenario.ScenarioRunner
import java.nio.file.Files
import java.nio.file.Path

class DesktopInteraction(
    private val context: RunContext,
    private val bridge: AxBridge,
    private val processes: DesktopProcesses,
    private val stateStore: RunStateStore,
    private val lifecycle: Lifecycle,
    private val evidence: DesktopEvidence,
) : Interaction {
    override fun snapshot(
        root: Query?,
        maxDepth: Int?
    ): SnapshotNode {
        val full = bridge.snapshot(runningPid(), maxDepth)
        return root?.let { QueryMatcher.require(full, it) } ?: full
    }

    override fun runScenario(scenario: Scenario): RunResult {
        val running = processes.isTracked(stateStore.load().desktop)
        if (scenario.launch.terminateExisting || !running) {
            lifecycle.launch(
                LaunchOptions(fresh = scenario.launch.fresh, arguments = scenario.launch.arguments, environment = scenario.launch.environment),
            )
        }
        return ScenarioRunner(DesktopActions(scenario), context.layout::relativize).run(scenario)
    }

    private fun runningPid(): Long = processes.trackedPid(stateStore.load().desktop)
        ?: throw ControlException(
            ErrorCode.APP_NOT_RUNNING,
            "No tracked desktop process is running.",
            "Run `posato-control launch -t desktop` first.",
        )

    private inner class DesktopActions(
        private val scenario: Scenario
    ) : NativeActions {
        override fun snapshot(maxDepth: Int?): SnapshotNode = bridge.snapshot(runningPid(), maxDepth)

        override fun tap(node: SnapshotNode) {
            bridge.press(runningPid(), requirePath(node))
        }

        override fun type(
            node: SnapshotNode,
            text: String,
            clear: Boolean,
            submit: Boolean
        ) {
            bridge.type(runningPid(), requirePath(node), text, clear, submit)
        }

        override fun press(
            key: String,
            modifiers: List<String>
        ) {
            bridge.key(runningPid(), key, modifiers)
        }

        override fun screenshot(name: String): Path = evidence.screenshot(name, null)

        override fun writeSnapshot(
            name: String,
            node: SnapshotNode
        ): Path {
            val destination = context.artifactPath("snapshots", "$name.json")
            Files.writeString(destination, ControlJson.pretty.encodeToString(SnapshotNode.serializer(), node))
            return context.recordArtifact(destination)
        }

        override fun relaunch() {
            lifecycle.launch(LaunchOptions(arguments = scenario.launch.arguments, environment = scenario.launch.environment))
        }

        override fun terminate() {
            lifecycle.terminate()
        }

        private fun requirePath(node: SnapshotNode): String = node.path
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The matched element has no accessibility path.")
    }
}
