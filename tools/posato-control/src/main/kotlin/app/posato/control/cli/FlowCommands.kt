package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.model.Query
import app.posato.control.model.Step
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Common paths through the app as single commands, on the desktop (in a VM) and on iOS alike. */
class FlowCommand : CliktCommand(name = "flow") {
    override fun help(context: Context): String =
        "Drive a common path through Posato in one command: add a schedule, create a set, start a session, link or remove iCloud."

    override fun run() = Unit
}

class FlowSetCommand : ControlCommand("set", "Create a pause set with a name and optional websites.") {
    private val name by option("--name", help = "Set name.").required()
    private val websites by option("--website", help = "A website to add; repeat for more.").multiple()

    override fun execute(session: Session): JsonElement {
        val backend = session.backend()
        // The Mac's in-page Back reads the list in lower case; the iOS bar's back button names the screen by its title.
        val back = if (session.target() == Target.DESKTOP) "Back to pause sets" else "Back to Pause sets"
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.button("Pause sets"),
                FlowSteps.button(back, optional = true, timeoutSeconds = SHORT_SECONDS),
                FlowSteps.waitFor(Query(text = "New set", role = FlowSteps.ROLE_BUTTON)),
            ),
        )
        // Posato accepts a second set with the same name, so a rerun would leave two rows the later steps cannot tell apart.
        if (hasSetNamed(FlowSteps.labels(backend))) {
            throw ControlException(
                ErrorCode.ALREADY_EXISTS,
                "Pause sets already lists a set named $name.",
                "Choose another --name, or remove the existing set first.",
            )
        }
        val steps = mutableListOf(
            FlowSteps.button("New set"),
            FlowSteps.typeInto(name, submit = false),
            FlowSteps.button("Save"),
            FlowSteps.waitFor(Query(text = back, role = FlowSteps.ROLE_BUTTON)),
        )
        if (websites.isNotEmpty()) {
            steps += FlowSteps.waitFor(Query(text = "Search", role = FlowSteps.ROLE_BUTTON))
            steps += FlowSteps.typeInto(websites.joinToString(", "), submit = true)
            steps += FlowSteps.button("Done", optional = true, timeoutSeconds = SHORT_SECONDS)
        }
        steps += listOf(
            FlowSteps.button(back),
            FlowSteps.screenshot("set-created"),
        )
        FlowSteps.run(backend, steps)
        if (!hasSetNamed(FlowSteps.labels(backend))) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "Pause sets shows no set named $name.")
        }
        return buildJsonObject {
            put("name", name)
            putJsonArray("websites") { websites.forEach(::add) }
        }
    }

    /**
     * A row reads "<name>, [Default set, Default, ]<n> websites · …". Matching through the website count keeps
     * "Deep Work" and "Work, personal" from counting as a set named "Work".
     */
    private fun hasSetNamed(labels: List<String>): Boolean {
        val row = Regex("^${Regex.escape(name)}, (Default set, )?(Default, )?\\d+ websites?\\b")
        return labels.any { label -> row.containsMatchIn(label) }
    }

    private companion object {
        const val SHORT_SECONDS = 3.0
    }
}

/** Starts a manual session from Session setup: an optional set, then the minutes from the 25-minute default. */
class FlowSessionCommand : ControlCommand("session", "Start a manual session with an optional set and length in minutes.") {
    private val set by option("--set", help = "Pause set to choose; the default set when omitted.")
    private val minutes by option("--minutes", help = "Length, 5 to 60 minutes.").int().default(DEFAULT_MINUTES)

    override fun execute(session: Session): JsonElement {
        if (minutes !in MIN_MINUTES..MAX_MINUTES) throw ControlException(ErrorCode.USAGE, "--minutes is 5 to 59, or the 60-minute preset.")
        val backend = session.backend()
        val target = session.target()
        val review = Query(text = "Review session", role = FlowSteps.ROLE_BUTTON)
        val begin = Query(text = "Start this pause", role = FlowSteps.ROLE_BUTTON)
        FlowSteps.run(backend, listOf(FlowSteps.button("Session"), FlowSteps.button("Start a session"), FlowSteps.waitFor(review)))
        set?.let { chosen -> chooseSet(backend, target, chosen) }
        val steps = if (minutes in PRESETS) listOf(FlowSteps.button("$minutes min")) else minuteSteps(target, minutes - DEFAULT_MINUTES)
        FlowSteps.run(
            backend,
            steps + listOf(
                FlowSteps.reveal(review),
                FlowSteps.tap(review),
                // A dropped wheel tap would start a session of another length: Review must show the asked one.
                FlowSteps.waitFor(Query(textContains = "$minutes minutes ·")),
                FlowSteps.reveal(begin),
                FlowSteps.tap(begin),
                FlowSteps.waitFor(Query(text = "End session early", role = FlowSteps.ROLE_BUTTON), timeoutSeconds = START_SECONDS),
                FlowSteps.screenshot("session-started"),
            ),
        )
        return buildJsonObject {
            set?.let { put("set", it) }
            put("minutes", minutes)
        }
    }

    /** The Mac steps its drawn wheel with arrows; iOS turns the system countdown wheel straight to the minutes. */
    private fun minuteSteps(
        target: Target,
        delta: Int,
    ): List<Step> {
        if (delta == 0) return emptyList()
        if (target != Target.DESKTOP) return listOf(FlowSteps.adjustWheels(listOf("0", "${DEFAULT_MINUTES + delta}")))
        val arrow = Query(text = if (delta > 0) "Increase Minutes" else "Decrease Minutes")
        return listOf(FlowSteps.reveal(arrow)) +
            List(kotlin.math.abs(delta)) { listOf(FlowSteps.tap(arrow), FlowSteps.sleep(TAP_GAP_SECONDS)) }.flatten()
    }

    private companion object {
        const val DEFAULT_MINUTES = 25
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 60
        val PRESETS = setOf(25, 45, 60)
        const val START_SECONDS = 90.0
        const val TAP_GAP_SECONDS = 0.3
    }
}
