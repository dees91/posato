package app.posato.control.model

import app.posato.control.core.ControlJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioFixturesTest {
    private val fixtures: Path = Path.of("fixtures", "scenarios")

    @Test
    fun `every fixture scenario decodes with known actions`() {
        val files = fixtures.listDirectoryEntries("*.json")
        assertTrue(files.isNotEmpty(), "No fixture scenarios under $fixtures")
        files.forEach { file ->
            val scenario = ControlJson.lenient.decodeFromString(Scenario.serializer(), Files.readString(file))
            assertEquals(1, scenario.version, file.toString())
            assertTrue(scenario.steps.isNotEmpty(), file.toString())
            scenario.steps.forEach { step -> assertTrue(step.action in Actions.all, "${file.fileName}: ${step.action}") }
        }
    }

    @Test
    fun `defaults are applied when keys are missing`() {
        val scenario = ControlJson.lenient.decodeFromString(Scenario.serializer(), """{"version":1,"steps":[{"action":"snapshot"}]}""")
        assertEquals(true, scenario.launch.terminateExisting)
        assertEquals(ScenarioDefaults.DEFAULT_TIMEOUT_SECONDS, scenario.defaults.timeoutSeconds)
        assertEquals(true, scenario.onFailure.screenshot)
        assertEquals(false, scenario.continueOnFailure)
    }

    @Test
    fun `run results round-trip through json`() {
        val error = StepError("WAIT_TIMEOUT", "timed out")
        val step = StepResult(0, "ready", Actions.WAIT_FOR, false, 12, listOf("a.png"), error)
        val result = RunResult(false, listOf(step), error)
        val text = ControlJson.compact.encodeToString(RunResult.serializer(), result)
        assertEquals(result, ControlJson.lenient.decodeFromString(RunResult.serializer(), text))
    }
}
