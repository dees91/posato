package app.posato.control.android

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
import app.posato.control.scenario.PageScroller
import app.posato.control.scenario.QueryMatcher
import app.posato.control.scenario.ScenarioRunner
import app.posato.control.vm.shellQuote
import java.nio.file.Files
import java.nio.file.Path

/**
 * Element commands and scenarios on Android. The tree covers the whole screen, so a system dialog (the VPN request,
 * a permission, the battery exemption) is addressed like Posato's own controls.
 */
class AndroidInteraction(
    private val context: RunContext,
    private val device: AndroidDevice,
    private val lifecycle: AndroidLifecycle,
    private val evidence: AndroidEvidence,
) : Interaction {
    override fun snapshot(
        root: Query?,
        maxDepth: Int?,
    ): SnapshotNode {
        device.requireReady()
        val full = device.snapshot()
        return root?.let { QueryMatcher.require(full, it) } ?: full
    }

    override fun runScenario(scenario: Scenario): RunResult {
        device.requireReady()
        if (!scenario.launch.skip && (scenario.launch.terminateExisting || device.app.pid() == null)) {
            lifecycle.launch(LaunchOptions(fresh = scenario.launch.fresh))
        }
        return ScenarioRunner(AndroidActions(), context.layout::relativize).run(scenario)
    }

    override fun typeFocused(
        text: String,
        clear: Boolean,
        submit: Boolean,
    ) {
        if (clear) clearFocused()
        device.input.type(text)
        if (submit) device.input.key(ENTER)
    }

    private fun clearFocused() {
        device.input.key(CTRL, KEY_A)
        device.input.key(DELETE)
    }

    private inner class AndroidActions : NativeActions {
        override fun scrollTo(
            query: Query,
            timeoutMs: Long,
        ) {
            // UIAutomator clips each element's bounds to what its parents show, so any element with area is in view.
            PageScroller(snapshot = { device.snapshot() }, scroll = ::swipe, readableShare = 1.0).scrollTo(query, timeoutMs)
        }

        private fun swipe(forward: Boolean) {
            // A swipe over the keyboard types (gesture typing) instead of scrolling, so the keyboard goes first, with Back.
            if (device.input.keyboardShown()) {
                device.input.key("KEYCODE_BACK")
                Thread.sleep(FOCUS_MILLIS)
            }
            val frame = device.snapshot().frame
            val x = (frame.x + frame.w / 2).toInt()
            val low = (frame.y + frame.h * SWIPE_LOW).toInt()
            val high = (frame.y + frame.h * SWIPE_HIGH).toInt()
            if (forward) device.input.swipe(x, low, x, high) else device.input.swipe(x, high, x, low)
        }

        override fun openUrl(url: String) {
            device.shell("am start -a android.intent.action.VIEW -d ${shellQuote(url)}").requireSuccess(ErrorCode.COMMAND_FAILED, "Opening $url")
        }

        override fun snapshot(maxDepth: Int?): SnapshotNode = device.snapshot()

        override fun tap(node: SnapshotNode) {
            if (node.frame.w <= 0 || node.frame.h <= 0) {
                throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The matched element has no area on screen.")
            }
            device.input.tap(node.frame.x + node.frame.w / 2, node.frame.y + node.frame.h / 2)
        }

        override fun type(
            node: SnapshotNode,
            text: String,
            clear: Boolean,
            submit: Boolean,
        ) {
            tap(node)
            Thread.sleep(FOCUS_MILLIS)
            typeFocused(text, clear, submit)
        }

        override fun press(
            key: String,
            modifiers: List<String>,
        ) {
            val code = KEYS[key.lowercase()]
                ?: throw ControlException(ErrorCode.USAGE, "Unknown key '$key' on android.", "Use one of ${KEYS.keys.sorted().joinToString()}.")
            device.input.key(code)
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
    }

    private companion object {
        const val ENTER = "KEYCODE_ENTER"
        const val DELETE = "KEYCODE_DEL"
        const val CTRL = "KEYCODE_CTRL_LEFT"
        const val KEY_A = "KEYCODE_A"
        const val FOCUS_MILLIS = 400L
        const val SWIPE_LOW = 0.7
        const val SWIPE_HIGH = 0.35
        val KEYS = mapOf(
            "return" to ENTER,
            "enter" to ENTER,
            "escape" to "KEYCODE_ESCAPE",
            "back" to "KEYCODE_BACK",
            "home" to "KEYCODE_HOME",
            "tab" to "KEYCODE_TAB",
            "delete" to DELETE,
            "space" to "KEYCODE_SPACE",
            "up" to "KEYCODE_DPAD_UP",
            "down" to "KEYCODE_DPAD_DOWN",
            "left" to "KEYCODE_DPAD_LEFT",
            "right" to "KEYCODE_DPAD_RIGHT",
        )
    }
}
