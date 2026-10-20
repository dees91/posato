package app.posato.control.linux

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
 * Element commands and scenarios in the Linux clone, through Posato's accessibility tree on AT-SPI. Compose reports
 * element bounds on Linux with offsets that do not match the screen, so a tap is the element's accessibility action,
 * as the Mac presses through its accessibility API, and typing focuses the field through AT-SPI before keys arrive.
 */
class LinuxInteraction(
    private val context: RunContext,
    private val guest: LinuxGuest,
    private val lifecycle: LinuxLifecycle,
    private val evidence: LinuxEvidence,
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
        return parseAtspiDump(
            guest.accessibility.helper("dump").requireSuccess(ErrorCode.COMMAND_FAILED, "Reading Posato's accessibility tree").stdout,
        )
    }

    override fun runScenario(scenario: Scenario): RunResult {
        guest.requireRunning()
        if (!scenario.launch.skip && (scenario.launch.terminateExisting || lifecycle.pid() == null)) {
            lifecycle.launch(LaunchOptions(fresh = scenario.launch.fresh))
        }
        return ScenarioRunner(LinuxActions(), context.layout::relativize).run(scenario)
    }

    override fun typeFocused(
        text: String,
        clear: Boolean,
        submit: Boolean,
    ) {
        if (clear) {
            guest.screen.key("ctrl+a")
            guest.screen.key("BackSpace")
        }
        guest.screen.type(text)
        if (submit) guest.screen.key("Return")
    }

    private inner class LinuxActions : NativeActions {
        /** Presses go by accessibility action, which reaches an element anywhere on its page, so being in the tree is enough. */
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
            guest.accessibility.helper("press", requirePath(node)).requireSuccess(ErrorCode.COMMAND_FAILED, "Pressing ${node.label ?: node.role}")
        }

        override fun type(
            node: SnapshotNode,
            text: String,
            clear: Boolean,
            submit: Boolean,
        ) {
            guest.accessibility.helper("focus", requirePath(node)).requireSuccess(ErrorCode.COMMAND_FAILED, "Focusing ${node.label ?: node.role}")
            // Keys typed before the field shows its focus lose their first characters.
            Thread.sleep(FOCUS_MILLIS)
            typeFocused(text, clear, submit)
        }

        override fun press(
            key: String,
            modifiers: List<String>,
        ) {
            val name = KEYS[key.lowercase()] ?: key
            guest.screen.key((modifiers.map { it.lowercase() } + name).joinToString("+"))
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
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The matched element has no accessibility path.")
    }

    private companion object {
        const val POLL_MILLIS = 500L
        const val FOCUS_MILLIS = 600L
        val KEYS = mapOf(
            "return" to "Return",
            "enter" to "Return",
            "escape" to "Escape",
            "tab" to "Tab",
            "delete" to "BackSpace",
            "space" to "space",
            "up" to "Up",
            "down" to "Down",
            "left" to "Left",
            "right" to "Right",
        )
    }
}
