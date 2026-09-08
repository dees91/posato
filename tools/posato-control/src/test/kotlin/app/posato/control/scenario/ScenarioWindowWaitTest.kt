package app.posato.control.scenario

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.desktop.requireAddressableWindow
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.Roles
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.model.States
import app.posato.control.model.Step
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScenarioWindowWaitTest {
    private val readyTree = SnapshotNode(
        role = Roles.OTHER,
        children = listOf(
            SnapshotNode(
                role = Roles.WINDOW,
                children = listOf(SnapshotNode(role = Roles.BUTTON, label = "Paused items")),
            ),
        ),
    )

    private class ScriptedActions(
        private val script: ArrayDeque<SnapshotNode?>,
        private val failure: ControlException? = null,
    ) : NativeActions {
        private var last: SnapshotNode? = null

        override fun prepareInteraction() = Unit

        override fun scrollTo(
            query: Query,
            timeoutMs: Long
        ) = Unit

        override fun snapshot(maxDepth: Int?): SnapshotNode {
            failure?.let { throw it }
            val next = if (script.isNotEmpty()) script.removeFirst() else last
            last = next
            return requireAddressableWindow(next ?: SnapshotNode(role = Roles.OTHER))
        }

        override fun tap(node: SnapshotNode) = Unit

        override fun type(
            node: SnapshotNode,
            text: String,
            clear: Boolean,
            submit: Boolean
        ) = Unit

        override fun press(
            key: String,
            modifiers: List<String>
        ) = Unit

        override fun screenshot(name: String): Path = Path.of("screenshots", "$name.png")

        override fun writeSnapshot(
            name: String,
            node: SnapshotNode
        ): Path = Path.of("snapshots", "$name.json")

        override fun relaunch() = Unit

        override fun terminate() = Unit
    }

    private fun waitFor(
        actions: ScriptedActions,
        timeoutSeconds: Double,
    ) = ScenarioRunner(actions, Path::toString).run(
        Scenario(
            steps = listOf(
                Step(
                    action = Actions.WAIT_FOR,
                    state = States.EXISTS,
                    query = Query(text = "Paused items", role = Roles.BUTTON),
                    timeoutSeconds = timeoutSeconds,
                ),
            ),
        ),
    )

    @Test
    fun `given a transient empty tree when waiting then it still succeeds`() {
        val actions = ScriptedActions(ArrayDeque(listOf(null, null, readyTree)))
        val result = waitFor(actions, timeoutSeconds = 5.0)
        assertTrue(result.ok, result.toString())
    }

    @Test
    fun `given a persistent empty tree when waiting then it reports the named code`() {
        val actions = ScriptedActions(ArrayDeque(listOf(null)))
        val result = waitFor(actions, timeoutSeconds = 0.1)
        assertFalse(result.ok)
        assertEquals("DESKTOP_WINDOW_UNAVAILABLE", result.steps.single().error?.code)
        assertTrue(
            result.steps.single().error?.message.orEmpty().contains("Relaunch it through `posato-control launch -t desktop`"),
            result.steps.single().error?.message,
        )
    }

    @Test
    fun `given a foreign snapshot failure when waiting then it propagates unchanged`() {
        val foreign = ControlException(ErrorCode.APP_NOT_RUNNING, "No tracked desktop process is running.")
        val result = waitFor(ScriptedActions(ArrayDeque(listOf()), foreign), timeoutSeconds = 5.0)
        assertFalse(result.ok)
        assertEquals("APP_NOT_RUNNING", result.steps.single().error?.code)
    }

    @Test
    fun `given a healthy tree without the element when waiting then it still times out`() {
        val healthyWithoutElement = SnapshotNode(
            role = Roles.OTHER,
            children = listOf(SnapshotNode(role = Roles.WINDOW)),
        )
        val result = waitFor(ScriptedActions(ArrayDeque(listOf(healthyWithoutElement))), timeoutSeconds = 0.1)
        assertFalse(result.ok)
        assertEquals("WAIT_TIMEOUT", result.steps.single().error?.code)
    }
}
