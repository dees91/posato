package app.posato.control.windows

import app.posato.control.backend.Interaction
import app.posato.control.backend.LaunchOptions
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.model.Query
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.scenario.NativeActions
import app.posato.control.scenario.QueryMatcher
import app.posato.control.scenario.ScenarioRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Element commands and scenarios in the Windows clone, through Posato's tree on the Java Access Bridge. A tap is the
 * element's accessible click action, with no pointer input, as on Linux. Compose refuses the bridge's text setter,
 * so typing focuses the field through the bridge and sends the text as Unicode keyboard input to Posato's window.
 */
class WindowsInteraction(
    private val context: RunContext,
    private val guest: WindowsGuest,
    private val lifecycle: WindowsLifecycle,
    private val evidence: WindowsEvidence,
) : Interaction {
    override fun snapshot(
        root: Query?,
        maxDepth: Int?,
    ): SnapshotNode {
        val full = read()
        return root?.let { QueryMatcher.require(full, it) } ?: full
    }

    private fun read(): SnapshotNode {
        guest.requireRunning()
        return parseJabDump(
            guest.tools.probe("dump").requireSuccess(ErrorCode.COMMAND_FAILED, "Reading Posato's tree on the Java Access Bridge").stdout,
        )
    }

    override fun runScenario(scenario: Scenario): RunResult {
        guest.requireRunning()
        if (!scenario.launch.skip && (scenario.launch.terminateExisting || lifecycle.pid() == null)) {
            lifecycle.launch(LaunchOptions(fresh = scenario.launch.fresh))
        }
        return ScenarioRunner(WindowsActions(), context.layout::relativize).run(scenario)
    }

    override fun typeFocused(
        text: String,
        clear: Boolean,
        submit: Boolean,
    ) {
        if (clear) {
            key("ctrl+a")
            key("backspace")
        }
        guest.tools.probe("type", text).requireSuccess(ErrorCode.COMMAND_FAILED, "Typing in ${WindowsGuest.CLONE}")
        if (submit) key("return")
    }

    private fun key(combo: String) {
        guest.tools.probe("key", combo).requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing $combo in ${WindowsGuest.CLONE}")
    }

    private inner class WindowsActions : NativeActions {
        /** Presses go by accessible action, which reaches an element anywhere on its page, so being in the tree is enough. */
        override fun scrollTo(
            query: Query,
            timeoutMs: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (QueryMatcher.find(read(), query) == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "No element matching ${query.describe()} is on the page.")
                }
                Thread.sleep(POLL_MILLIS)
            }
        }

        override fun snapshot(maxDepth: Int?): SnapshotNode = read()

        override fun tap(node: SnapshotNode) {
            guest.tools.probe("press", requirePath(node), node.label.orEmpty())
                .requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing ${node.label ?: node.role}")
        }

        override fun type(
            node: SnapshotNode,
            text: String,
            clear: Boolean,
            submit: Boolean,
        ) {
            guest.tools.probe("focus", requirePath(node)).requireSuccess(ErrorCode.COMMAND_FAILED, "Focusing ${node.label ?: node.role}")
            // Keys typed before the field shows its focus lose their first characters.
            Thread.sleep(FOCUS_MILLIS)
            typeFocused(text, clear, submit)
        }

        override fun press(
            key: String,
            modifiers: List<String>,
        ) {
            key((modifiers.map { it.lowercase() } + key.lowercase()).joinToString("+"))
        }

        override fun screenshot(name: String): Path = evidence.screenshot(name, null)

        override fun writeSnapshot(
            name: String,
            node: SnapshotNode,
        ): Path {
            val destination = context.artifactPath("snapshots", "$name.json")
            Files.writeString(destination, ControlJson.pretty.encodeToString(SnapshotNode.serializer(), node))
            return context.recordArtifact(destination)
        }

        override fun relaunch() {
            lifecycle.launch(LaunchOptions())
        }

        override fun terminate() {
            lifecycle.terminate()
        }

        private fun requirePath(node: SnapshotNode): String = node.path
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The matched element has no Java Access Bridge path.")
    }

    private companion object {
        const val POLL_MILLIS = 500L
        const val FOCUS_MILLIS = 400L
    }
}
