package app.posato.control.cli

import app.posato.control.android.AndroidDevice
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.path.readText

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
            ?: throw ControlException(ErrorCode.DRIVER_FAILED, "The driver attached no appearance report.")
        val report: JsonObject = ControlJson.lenient.parseToJsonElement(session.layout.root.resolve(artifact).readText()).jsonObject
        val before = report.getValue("before").jsonPrimitive.content
        val after = report.getValue("after").jsonPrimitive.content
        requireReached(after)
        return Triple(before, after, artifact)
    }

    private fun android(session: Session): Triple<String, String, String?> {
        val device = AndroidDevice(session.context, session.options.udid)
        device.requireReady()
        val before = nightMode(device)
        mode?.takeIf { it != before }?.let { requested ->
            val output = device.shell("cmd uimode night ${if (requested == Appearances.DARK) "yes" else "no"}")
            if (output.exitCode != 0) {
                throw ControlException(ErrorCode.COMMAND_FAILED, "cmd uimode night failed: ${output.stderr.ifBlank { output.stdout }.trim()}")
            }
        }
        val after = nightMode(device)
        requireReached(after)
        return Triple(before, after, null)
    }

    /** `light`, `dark`, or the raw `auto`/`custom` value `cmd uimode night` reports. */
    private fun nightMode(device: AndroidDevice): String {
        val output = device.shell("cmd uimode night")
        val value = output.stdout.substringAfter("Night mode:", "").trim()
        if (output.exitCode != 0 || value.isEmpty()) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "cmd uimode night did not report the night mode: ${output.stdout.trim()}")
        }
        return when (value) {
            "yes" -> Appearances.DARK
            "no" -> Appearances.LIGHT
            else -> value
        }
    }

    private fun requireReached(after: String) {
        val requested = mode ?: return
        if (after != requested) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "The device reports $after after switching to $requested.")
        }
    }
}
