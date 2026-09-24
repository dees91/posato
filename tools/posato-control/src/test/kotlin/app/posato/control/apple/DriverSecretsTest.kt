package app.posato.control.apple

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Actions
import app.posato.control.model.Scenario
import app.posato.control.model.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriverSecretsTest {
    private val passcodeScenario = Scenario(steps = listOf(Step(action = Actions.PRESS_KEYS, secret = DriverSecrets.DEVICE_PASSCODE)))

    @Test
    fun `a pressKeys secret reaches the runner environment and never the encoded scenario`() {
        val environment = DriverSecrets.environment(passcodeScenario) { "314159" }
        val encoded = ControlJson.compact.encodeToString(Scenario.serializer(), passcodeScenario)

        assertEquals(mapOf("POSATO_SECRET_DEVICEPASSCODE" to "314159"), environment)
        assertFalse(encoded.contains("314159"))
    }

    @Test
    fun `a scenario without secret steps reads no secret`() {
        var reads = 0
        val environment = DriverSecrets.environment(Scenario(steps = listOf(Step(action = Actions.SNAPSHOT)))) {
            reads++
            "unused"
        }

        assertTrue(environment.isEmpty())
        assertEquals(0, reads)
    }

    @Test
    fun `an unknown secret name is an invalid scenario`() {
        val scenario = Scenario(steps = listOf(Step(action = Actions.PRESS_KEYS, secret = "somethingElse")))

        val failure = assertFailsWith<ControlException> { DriverSecrets.environment(scenario) { "x" } }

        assertEquals(ErrorCode.SCENARIO_INVALID, failure.code)
    }

    @Test
    fun `key taps are removed from the xcodebuild log and other lines stay`() {
        val log =
            """
            t =     1.20s Tap "Continue" Button
            t =     2.10s Tap "3" Key
            t =     2.40s     Find the "1" Key
            t =     2.60s Tap "4" Key
            ** TEST SUCCEEDED **
            """.trimIndent()

        assertEquals("t =     1.20s Tap \"Continue\" Button\n** TEST SUCCEEDED **", DriverSecrets.redactKeyTaps(log))
    }
}
