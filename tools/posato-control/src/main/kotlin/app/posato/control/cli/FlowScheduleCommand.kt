package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import app.posato.control.model.States
import app.posato.control.model.Step
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Adds a schedule through the editor as a person would: name, set, start and end, on or off. The time wheels drop a
 * tap now and then, so each time is set in rounds that read the editor's label and step only the remaining
 * difference, instead of a counted series of taps.
 */
class FlowScheduleAddCommand : ControlCommand("schedule", "Add a schedule through the editor with a name, an optional set, and its times.") {
    private val name by option("--name", help = "Schedule name.").required()
    private val start by option("--start", help = "Start time, HH:MM.").required()
    private val end by option("--end", help = "End time, HH:MM.").required()
    private val set by option("--set", help = "Pause set to choose; the default set when omitted.")
    private val off by option("--off", help = "Save the schedule turned off, so it cannot start during the run.").flag()
    private val days by option(
        "--days",
        help = "Days to run on: every (the default, so a run on any day starts), weekdays, or mon,tue,...,sun.",
    ).default("every")

    override fun execute(session: Session): JsonElement {
        val backend = session.backend()
        val startTime = WheelTime.parse(start)
        val endTime = WheelTime.parse(end)
        val chosenDays = ScheduleDays.parse(days)
        FlowSteps.run(backend, openEditor() + chooseDays(chosenDays))
        setTime(backend, WheelKind.START, startTime)
        setTime(backend, WheelKind.END, endTime)
        // The list reloads after the editor closes and may hold the new row below the fold, so the row is revealed
        // and waited for rather than read from one snapshot.
        val row = Query(textContains = "$name, ${ScheduleDays.summary(chosenDays)} ", role = FlowSteps.ROLE_BUTTON)
        FlowSteps.run(backend, save() + listOf(FlowSteps.reveal(row), FlowSteps.waitFor(row)))
        return buildJsonObject {
            put("name", name)
            put("days", ScheduleDays.summary(chosenDays))
            put("start", startTime.text)
            put("end", endTime.text)
            set?.let { put("set", it) }
            put("enabled", !off)
        }
    }

    private fun openEditor(): List<Step> {
        val steps = mutableListOf(
            FlowSteps.button("Back to schedules", optional = true, timeoutSeconds = SHORT_SECONDS),
            FlowSteps.button("Schedules"),
            FlowSteps.reveal(Query(text = "Add schedule", role = FlowSteps.ROLE_BUTTON)),
            FlowSteps.button("Add schedule"),
            FlowSteps.waitFor(Query(role = FlowSteps.ROLE_TEXT_FIELD)),
            FlowSteps.typeInto(name, submit = true),
        )
        set?.let { chosen -> steps += chooseSet(chosen) }
        return steps
    }

    private fun chooseDays(chosen: List<String>): List<Step> = ScheduleDays.toggles(chosen).flatMap { day ->
        val button = Query(text = day, role = FlowSteps.ROLE_BUTTON)
        listOf(FlowSteps.reveal(button), FlowSteps.tap(button))
    }

    private fun save(): List<Step> {
        val steps = mutableListOf<Step>()
        if (off) {
            val toggle = Query(textContains = "Schedule on,", role = FlowSteps.ROLE_BUTTON)
            steps += listOf(FlowSteps.reveal(toggle), FlowSteps.tap(toggle))
        }
        val save = Query(text = "Save schedule", role = FlowSteps.ROLE_BUTTON)
        steps += listOf(FlowSteps.reveal(save), FlowSteps.tap(save), FlowSteps.waitFor(save, States.ABSENT), FlowSteps.screenshot("schedule-saved"))
        return steps
    }

    /** Steps one wheel toward [target] in rounds; each round opens the wheel, steps the read difference, and closes it. */
    private fun setTime(
        backend: Backend,
        kind: WheelKind,
        target: WheelTime,
    ) {
        repeat(MAX_ROUNDS) {
            // A small iPhone keeps the time buttons below the fold, where they carry no label.
            FlowSteps.run(backend, listOf(kind.reveal()))
            val current = kind.read(FlowSteps.labels(backend))
                ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The editor shows no ${kind.label} time.")
            if (current == target) return
            FlowSteps.run(backend, kind.round(current, target))
        }
        FlowSteps.run(backend, listOf(kind.reveal()))
        val reached = kind.read(FlowSteps.labels(backend))
        if (reached != target) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "${kind.label} stayed at ${reached?.text} instead of ${target.text}.")
        }
    }

    private companion object {
        const val MAX_ROUNDS = 4
        const val SHORT_SECONDS = 3.0
    }
}

/** Chooses [name] in the set dialog, whose rows read "<name>, <n> websites" or "<name> (default), <n> websites". */
internal fun chooseSet(name: String): List<Step> {
    val row = Query(textContains = "Pause set,", role = FlowSteps.ROLE_BUTTON)
    return listOf(
        FlowSteps.reveal(row),
        FlowSteps.tap(row),
        FlowSteps.tap(Query(textContains = "$name (default),", role = FlowSteps.ROLE_BUTTON), optional = true, timeoutSeconds = 3.0),
        FlowSteps.tap(Query(textContains = "$name,", role = FlowSteps.ROLE_BUTTON), optional = true, timeoutSeconds = 3.0),
        FlowSteps.waitFor(Query(text = "Pause set, $name", role = FlowSteps.ROLE_BUTTON)),
    )
}

internal data class WheelTime(
    val hour: Int,
    val minute: Int,
) {
    val text: String
        get() = "%02d:%02d".format(hour, minute)

    companion object {
        fun parse(value: String): WheelTime {
            val match = Regex("""(\d{1,2}):(\d{2})""").matchEntire(value.trim())
                ?: throw ControlException(ErrorCode.USAGE, "Times are HH:MM, such as 09:30; got '$value'.")
            val time = WheelTime(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            if (time.hour !in 0..LAST_HOUR || time.minute !in 0..LAST_MINUTE) throw ControlException(ErrorCode.USAGE, "No such time: '$value'.")
            return time
        }

        private const val LAST_HOUR = 23
        private const val LAST_MINUTE = 59
    }
}

/** The editor's two time buttons, "Starts 09:00" and "Ends 11:00" (with " next day" when it ends after midnight). */
internal enum class WheelKind(
    val label: String,
    private val prefix: String,
) {
    START("Start", "Starts"),
    END("End", "Ends"),
    ;

    private val button: Query
        get() = Query(textContains = "$prefix ", role = FlowSteps.ROLE_BUTTON)

    fun read(labels: List<String>): WheelTime? {
        val pattern = Regex("""^$prefix (\d{2}):(\d{2})""")
        return labels.firstNotNullOfOrNull { label -> pattern.find(label) }
            ?.let { match -> WheelTime(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }
    }

    fun reveal(): Step = FlowSteps.reveal(button)

    fun round(
        current: WheelTime,
        target: WheelTime,
    ): List<Step> {
        val steps = mutableListOf(FlowSteps.reveal(button), FlowSteps.tap(button), FlowSteps.sleep(WHEEL_SETTLE_SECONDS))
        steps += arrows("hours", target.hour - current.hour)
        steps += arrows("minutes", target.minute - current.minute)
        val done = Query(text = "Done", role = FlowSteps.ROLE_BUTTON)
        steps += listOf(FlowSteps.reveal(done), FlowSteps.tap(done), FlowSteps.sleep(WHEEL_SETTLE_SECONDS))
        return steps
    }

    private fun arrows(
        unit: String,
        delta: Int,
    ): List<Step> {
        if (delta == 0) return emptyList()
        val arrow = Query(text = (if (delta > 0) "Increase" else "Decrease") + " $label $unit")
        return listOf(FlowSteps.reveal(arrow)) +
            List(kotlin.math.abs(delta)) { listOf(FlowSteps.tap(arrow), FlowSteps.sleep(TAP_GAP_SECONDS)) }.flatten()
    }

    private companion object {
        const val WHEEL_SETTLE_SECONDS = 1.5
        const val TAP_GAP_SECONDS = 0.3
    }
}

/**
 * The days a verification schedule runs on. The editor starts a new plan on weekdays, so a run presses only the days
 * that differ, and the Schedules row then lists the chosen days by their short names.
 */
internal object ScheduleDays {
    val ALL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val EDITOR_DEFAULT = ALL.take(5)
    private const val SHORT_LENGTH = 3

    fun parse(value: String): List<String> {
        val chosen = when (value.trim().lowercase()) {
            "every" -> ALL

            "weekdays" -> EDITOR_DEFAULT

            else -> value.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { name ->
                ALL.firstOrNull { day -> day.take(SHORT_LENGTH).lowercase() == name }
                    ?: throw ControlException(ErrorCode.USAGE, "Unknown day '$name'; use every, weekdays, or mon,tue,...,sun.")
            }
        }
        if (chosen.isEmpty()) throw ControlException(ErrorCode.USAGE, "--days names no day; use every, weekdays, or mon,tue,...,sun.")
        return ALL.filter { day -> day in chosen }
    }

    fun toggles(target: List<String>): List<String> = ALL.filter { day -> (day in target) != (day in EDITOR_DEFAULT) }

    fun summary(target: List<String>): String = target.joinToString(", ") { day -> day.take(SHORT_LENGTH) }
}
