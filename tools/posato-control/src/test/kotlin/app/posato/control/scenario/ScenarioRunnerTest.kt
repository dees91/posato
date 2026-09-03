package app.posato.control.scenario

import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.Scenario
import app.posato.control.model.ScenarioDefaults
import app.posato.control.model.SnapshotNode
import app.posato.control.model.States
import app.posato.control.model.Step
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioRunnerTest {
    private val tree: SnapshotNode = ControlJson.lenient.decodeFromString(
        SnapshotNode.serializer(),
        checkNotNull(javaClass.getResource("/snapshots/targets-desktop.json")).readText(),
    )

    private class RecordingActions(
        private val tree: SnapshotNode
    ) : NativeActions {
        val events = mutableListOf<String>()

        override fun snapshot(maxDepth: Int?): SnapshotNode = tree

        override fun tap(node: SnapshotNode) {
            events.add("tap ${node.path}")
        }

        override fun type(
            node: SnapshotNode,
            text: String,
            clear: Boolean,
            submit: Boolean
        ) {
            events.add("type ${node.path} $text clear=$clear submit=$submit")
        }

        override fun press(
            key: String,
            modifiers: List<String>
        ) {
            events.add("press $key ${modifiers.joinToString("+")}")
        }

        override fun screenshot(name: String): Path = Path.of("screenshots", "$name.png").also { events.add("screenshot $name") }

        override fun writeSnapshot(
            name: String,
            node: SnapshotNode
        ): Path = Path.of("snapshots", "$name.json").also { events.add("snapshot $name") }

        override fun relaunch() {
            events.add("relaunch")
        }

        override fun terminate() {
            events.add("terminate")
        }
    }

    @Test
    fun `runs every step in order and reports artifacts`() {
        val actions = RecordingActions(tree)
        val scenario = Scenario(
            steps = listOf(
                Step(action = Actions.WAIT_FOR, state = States.EXISTS, query = Query(text = "Add website")),
                Step(
                    action = Actions.TYPE,
                    query = Query(role = "textField", near = Query(text = "Add website")),
                    text = "example.com",
                    clear = true,
                ),
                Step(action = Actions.TAP, query = Query(text = "Add website")),
                Step(action = Actions.ASSERT, state = States.EXISTS, query = Query(text = "example.com")),
                Step(action = Actions.SCREENSHOT, name = "after-add"),
                Step(action = Actions.SNAPSHOT, name = "after-add", query = Query(role = "window")),
                Step(action = Actions.PRESS, key = "return"),
            ),
        )
        val result = ScenarioRunner(actions, Path::toString).run(scenario)
        assertTrue(result.ok, result.toString())
        assertEquals(
            listOf(
                "type 0/0/0/3/0 example.com clear=true submit=false",
                "tap 0/0/0/3/2",
                "screenshot screenshot-4-after-add",
                "snapshot snapshot-5-after-add",
                "press return ",
            ),
            actions.events,
        )
        assertEquals(listOf("screenshots/screenshot-4-after-add.png"), result.steps[4].artifacts)
    }

    @Test
    fun `a failing step stops the run and captures failure evidence`() {
        val actions = RecordingActions(tree)
        val scenario = Scenario(
            defaults = ScenarioDefaults(timeoutSeconds = 0.3),
            steps = listOf(
                Step(action = Actions.WAIT_FOR, state = States.EXISTS, query = Query(text = "Missing")),
                Step(action = Actions.TAP, query = Query(text = "Add website")),
            ),
        )
        val result = ScenarioRunner(actions, Path::toString).run(scenario)
        assertEquals(false, result.ok)
        assertEquals(1, result.steps.size)
        assertEquals(ErrorCode.WAIT_TIMEOUT.name, result.steps.single().error?.code)
        assertEquals(listOf("screenshot failure-0-screenshot", "snapshot failure-0-snapshot"), actions.events)
    }

    @Test
    fun `continueOnFailure keeps going`() {
        val actions = RecordingActions(tree)
        val scenario = Scenario(
            continueOnFailure = true,
            steps = listOf(
                Step(action = Actions.ASSERT, state = States.ABSENT, query = Query(text = "Add website")),
                Step(action = Actions.TAP, query = Query(text = "Add website")),
            ),
        )
        val result = ScenarioRunner(actions, Path::toString).run(scenario)
        assertEquals(false, result.ok)
        assertEquals(listOf(false, true), result.steps.map { it.ok })
        assertEquals(ErrorCode.ASSERTION_FAILED.name, result.steps.first().error?.code)
    }

    @Test
    fun `unknown actions are scenario errors`() {
        val result = ScenarioRunner(RecordingActions(tree), Path::toString).run(Scenario(steps = listOf(Step(action = "fly"))))
        assertEquals(ErrorCode.SCENARIO_INVALID.name, result.error?.code)
    }
}
