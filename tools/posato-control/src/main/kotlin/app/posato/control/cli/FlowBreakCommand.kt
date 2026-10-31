package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Takes or ends the break of the running scheduled pause on Session (`SCHEDULE-007`): `start` presses Take a break and
 * the length in its confirmation, then waits for End break; `end` presses End break, then waits for Take a break to
 * return. Each fails when Session does not show the action, so an unavailable break is reported, not pressed.
 */
class FlowBreakCommand : ControlCommand("break", "Take a break of 5, 10, or 15 minutes in the running scheduled pause, or end it.") {
    private val action by argument(help = "start | end").choice(START, END)
    private val minutes by option("--minutes", help = "Break length for start: 5, 10, or 15.").int().default(DEFAULT_MINUTES)

    override fun execute(session: Session): JsonElement {
        if (minutes !in LENGTHS) throw ControlException(ErrorCode.USAGE, "--minutes is 5, 10, or 15.")
        val backend = session.backend()
        val apple = session.target().isApple
        val takeBreak = FlowControls.byId("action_take_a_break")
        val endBreak = FlowControls.byId("action_end_break")
        val steps = if (action == START) {
            listOf(
                FlowControls.buttonId("session_title"),
                FlowSteps.reveal(takeBreak),
                FlowSteps.tap(takeBreak),
                FlowSteps.waitFor(Query(text = "Take a break?")),
                FlowControls.alertButton("action_break_$minutes", "$minutes minutes", apple),
                FlowSteps.waitFor(endBreak, timeoutSeconds = OUTCOME_SECONDS),
                FlowSteps.screenshot("break-started"),
            )
        } else {
            listOf(
                FlowControls.buttonId("session_title"),
                FlowSteps.reveal(endBreak),
                FlowSteps.tap(endBreak),
                FlowSteps.waitFor(takeBreak, timeoutSeconds = OUTCOME_SECONDS),
                FlowSteps.screenshot("break-ended"),
            )
        }
        FlowSteps.run(backend, steps)
        return buildJsonObject {
            put("action", action)
            if (action == START) put("minutes", minutes)
        }
    }

    private companion object {
        const val START = "start"
        const val END = "end"
        const val DEFAULT_MINUTES = 5
        val LENGTHS = setOf(5, 10, 15)
        const val OUTCOME_SECONDS = 30.0
    }
}
