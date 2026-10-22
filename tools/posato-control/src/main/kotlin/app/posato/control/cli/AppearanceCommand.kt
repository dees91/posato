package app.posato.control.cli

import app.posato.control.android.AndroidDevice
import app.posato.control.apple.AppearanceReport
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.model.Actions
import app.posato.control.model.Appearances
import app.posato.control.model.LaunchConfiguration
import app.posato.control.model.Scenario
import app.posato.control.model.Step
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Switches the system appearance of a phone, so that captures and checks in Light or Dark Mode need no hand on the
 * device. The iOS targets use the XCUITest driver without starting Posato; Android uses `cmd uimode night`.
 */
class AppearanceCommand : ControlCommand("appearance", "Read or switch the iPhone, Simulator, or Android system appearance (light or dark).") {
    private val mode by argument("mode", help = "light or dark; leave it out to read the current mode.")
        .choice(*Appearances.all.toTypedArray())
        .optional()

    override fun execute(session: Session): JsonElement {
        val target = session.target()
        val (before, after, evidence) = when {
            target.isApple -> apple(session)

            target == Target.ANDROID -> android(session)

            else -> throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "appearance switches a phone; the ${target.id} target has no system appearance switch here.",
                "Use -t device, -t simulator, or -t android.",
            )
        }
        return buildJsonObject {
            put("target", target.id)
            put("requested", mode)
            put("before", before)
            put("mode", after)
            put("changed", before != after)
            if (evidence == null) put("evidence", JsonNull) else put("evidence", evidence)
        }
    }

    private fun apple(session: Session): Triple<String, String, String?> {
        val step = Step(action = Actions.APPEARANCE, appearance = mode)
        val result = failIfStepFailed(session.backend().runScenario(Scenario(launch = LaunchConfiguration(skip = true), steps = listOf(step))))
        val artifact = result.steps.firstOrNull()?.artifacts?.firstOrNull()
        val (before, after) = AppearanceReport.read(session.layout.root, result)
        requireReached(after)
        return Triple(before, after, artifact)
    }

    private fun android(session: Session): Triple<String, String, String?> {
        val device = AndroidDevice(session.context, session.options.udid)
        device.requireReady()
        val before = device.nightMode()
        mode?.takeIf { it != before }?.let { requested ->
            val output = device.shell("cmd uimode night ${if (requested == Appearances.DARK) "yes" else "no"}")
            if (output.exitCode != 0) {
                throw ControlException(ErrorCode.COMMAND_FAILED, "cmd uimode night failed: ${output.stderr.ifBlank { output.stdout }.trim()}")
            }
        }
        val after = device.nightMode()
        requireReached(after)
        return Triple(before, after, null)
    }

    private fun requireReached(after: String) {
        val requested = mode ?: return
        if (after != requested) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "The device reports $after after switching to $requested.")
        }
    }
}
