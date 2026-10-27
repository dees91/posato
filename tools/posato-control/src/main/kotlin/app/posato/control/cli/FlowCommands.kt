package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.model.Query
import app.posato.control.model.Step
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Common paths through the app as single commands, on the desktop (in a VM), iOS, Android, and Linux alike. */
class FlowCommand : CliktCommand(name = "flow") {
    override fun help(context: Context): String =
        "Drive a common path through Posato in one command: onboard, add a schedule, create a set, start a session, link a folder or iCloud."

    override fun run() = Unit
}

/**
 * Starts a manual session from Session setup: an optional set, then a quick choice, the minutes from the 25-minute
 * default, or the choice that ends the pause at midnight.
 */
class FlowSessionCommand : ControlCommand("session", "Start a manual session with an optional set and length in minutes.") {
    private val set by option("--set", help = "Pause set to choose; the default set when omitted.")
    private val minutes by option("--minutes", help = "Length: 5 to 59 minutes, or a quick choice of 60, 120, 240, or 480.")
        .int().default(DEFAULT_MINUTES)
    private val untilEndOfDay by option("--until-end-of-day", help = "End the pause at the next midnight instead of after --minutes.").flag()
    private val endEarlyAfter by option(
        "--end-early-after",
        help = "After the pause starts, wait this many seconds, then end it early with End session early and End session.",
    ).int()

    override fun execute(session: Session): JsonElement {
        validate()
        val backend = session.backend()
        val target = session.target()
        val review = Query(text = "Review session", role = FlowSteps.ROLE_BUTTON)
        val begin = Query(text = "Start this pause", role = FlowSteps.ROLE_BUTTON)
        val firstChoice = Query(text = choiceLabel(target, DEFAULT_MINUTES), role = FlowSteps.ROLE_BUTTON)
        FlowSteps.run(backend, listOf(FlowSteps.button("Session"), FlowSteps.button("Start a session"), FlowSteps.waitFor(firstChoice)))
        set?.let { chosen -> chooseSet(backend, target, chosen) }
        val endOfDay = Query(textContains = END_OF_DAY, role = FlowSteps.ROLE_BUTTON)
        val steps = when {
            untilEndOfDay -> listOf(FlowSteps.tap(endOfDay))
            minutes in PRESETS -> listOf(FlowSteps.button(choiceLabel(target, minutes)))
            else -> minuteSteps(target, minutes - DEFAULT_MINUTES)
        }
        val chosen = if (untilEndOfDay) END_OF_DAY else lengthText(minutes)
        FlowSteps.run(
            backend,
            // Revealing Review swipes the screen and can turn the length wheel under it, so it comes before the choice.
            listOf(FlowSteps.reveal(review)) + steps + listOf(
                FlowSteps.tap(review),
                // A dropped wheel tap would start a session of another length: Review must show the asked one.
                FlowSteps.waitFor(Query(textContains = "$chosen ·")),
                FlowSteps.reveal(begin),
                FlowSteps.tap(begin),
                FlowSteps.waitFor(Query(text = "End session early", role = FlowSteps.ROLE_BUTTON), timeoutSeconds = START_SECONDS),
                FlowSteps.screenshot("session-started"),
            ),
        )
        endEarlyAfter?.let { seconds -> endEarly(backend, seconds) }
        return buildJsonObject {
            set?.let { put("set", it) }
            if (untilEndOfDay) put("untilEndOfDay", true) else put("minutes", minutes)
            endEarlyAfter?.let { put("endedEarlyAfterSeconds", it) }
        }
    }

    private fun validate() {
        if (endEarlyAfter?.let { it < 0 } == true) throw ControlException(ErrorCode.USAGE, "--end-early-after takes 0 or more seconds.")
        if (!untilEndOfDay && minutes !in MIN_MINUTES..MAX_WHEEL_MINUTES && minutes !in PRESETS) {
            throw ControlException(ErrorCode.USAGE, "--minutes is 5 to 59, or a quick choice of 60, 120, 240, or 480.")
        }
    }

    /** Ends the running pause the way a person does: End session early, then End session in the confirmation. */
    private fun endEarly(
        backend: Backend,
        seconds: Int,
    ) {
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.sleep(seconds.toDouble()),
                FlowSteps.button("End session early"),
                FlowSteps.waitFor(Query(text = "Ready to return?")),
                FlowSteps.button("End session"),
                FlowSteps.waitFor(Query(text = "Start a session", role = FlowSteps.ROLE_BUTTON), timeoutSeconds = START_SECONDS),
                FlowSteps.screenshot("session-ended-early"),
            ),
        )
    }

    /** A quick choice's label: the Mac shows and exposes "25 min" or "2 h"; iOS exposes the spoken "25 minutes" or "2 hours". */
    private fun choiceLabel(
        target: Target,
        minutes: Int,
    ): String {
        if (target.isApple) return lengthText(minutes)
        return if (minutes < MINUTES_PER_HOUR) "$minutes min" else "${minutes / MINUTES_PER_HOUR} h"
    }

    /** The length as Session names it on Review and to VoiceOver on iOS, such as "25 minutes" or "2 hours". */
    private fun lengthText(minutes: Int): String {
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        val hourText = if (hours == 1) "1 hour" else "$hours hours"
        return when {
            hours == 0 -> "$rest minutes"
            rest == 0 -> hourText
            else -> "$hourText $rest minutes"
        }
    }

    /** The Mac steps its drawn wheel with arrows; iOS turns the system countdown wheel straight to the minutes. */
    private fun minuteSteps(
        target: Target,
        delta: Int,
    ): List<Step> {
        if (delta == 0) return emptyList()
        if (target.isApple) return listOf(FlowSteps.adjustWheels(listOf("0", "${DEFAULT_MINUTES + delta}")))
        val arrow = Query(text = if (delta > 0) "Increase Minutes" else "Decrease Minutes")
        return listOf(FlowSteps.reveal(arrow), FlowSteps.tap(arrow).copy(repeat = kotlin.math.abs(delta), seconds = TAP_GAP_SECONDS))
    }

    private companion object {
        const val DEFAULT_MINUTES = 25
        const val MIN_MINUTES = 5
        const val MAX_WHEEL_MINUTES = 59
        const val MINUTES_PER_HOUR = 60
        const val END_OF_DAY = "Until end of day"
        val PRESETS = setOf(25, 45, 60, 120, 240, 480)
        const val START_SECONDS = 90.0
        const val TAP_GAP_SECONDS = 0.3
    }
}
