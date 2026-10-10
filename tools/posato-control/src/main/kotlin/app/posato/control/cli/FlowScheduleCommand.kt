package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.core.runsInVirtualMachine
import app.posato.control.model.Query
import app.posato.control.model.States
import app.posato.control.model.Step
import app.posato.control.vm.SCHEDULE_CONSENT_QUERY
import app.posato.control.vm.scheduleConsentGiven
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Adds a schedule through the editor as a person would: name, set, start and end, on or off. On the Mac the drawn
 * time wheels drop a tap now and then, so each time is set in rounds that read the editor's label and step only the
 * remaining difference, instead of a counted series of taps. On iOS each time is the system's compact time picker,
 * whose wheels are turned straight to the time and then read back.
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
        val target = session.target()
        val startTime = WheelTime.parse(start)
        val endTime = WheelTime.parse(end)
        val chosenDays = ScheduleDays.parse(days)
        FlowSteps.run(backend, openEditor(target))
        set?.let { chosen -> chooseSet(backend, target, chosen) }
        FlowSteps.run(backend, chooseDays(chosenDays))
        if (target == Target.DESKTOP) {
            setTime(backend, WheelKind.START, startTime)
            setTime(backend, WheelKind.END, endTime)
        } else {
            setSystemTime(backend, WheelKind.START, startTime)
            setSystemTime(backend, WheelKind.END, endTime)
        }
        // The list reloads after the editor closes, so the row is waited for rather than read from one snapshot. The list
        // is not lazy: a row below the fold is still in the tree.
        // iOS reports the tappable row as a button and the Mac as text, so the row's own words identify it.
        val row = Query(textContains = "$name, ${ScheduleDays.summary(chosenDays)} ")
        FlowSteps.run(backend, save() + listOf(FlowSteps.waitFor(row)))
        return buildJsonObject {
            put("name", name)
            put("days", ScheduleDays.summary(chosenDays))
            put("start", startTime.text)
            put("end", endTime.text)
            set?.let { put("set", it) }
            put("enabled", !off)
            if (target == Target.DESKTOP && runsInVirtualMachine()) {
                val consent = scheduleConsentGiven(
                    session.context.subprocess.run(listOf("/bin/sh", "-c", SCHEDULE_CONSENT_QUERY)).stdout,
                )
                put("scheduleConsent", consent)
                // Without the consent the saved plan never starts on its own, which once read as an update defect.
                if (!consent) put("warning", "This Mac has not allowed schedules to start on their own; the plan will not start. $CONSENT_HINT")
            }
        }
    }

    private fun openEditor(target: Target): List<Step> {
        // A leftover open editor is left the way each host offers: the Mac's in-page link or the iOS bar's back.
        val back = if (target == Target.DESKTOP) "Back to schedules" else "Back to Schedules"
        return listOf(
            FlowSteps.button(back, optional = true, timeoutSeconds = SHORT_SECONDS),
            FlowSteps.button("Schedules"),
            FlowSteps.reveal(Query(text = "Add schedule", role = FlowSteps.ROLE_BUTTON)),
            FlowSteps.button("Add schedule"),
            FlowSteps.waitFor(Query(role = FlowSteps.ROLE_TEXT_FIELD)),
            FlowSteps.typeInto(name, submit = true),
        )
    }

    private fun chooseDays(chosen: List<String>): List<Step> = ScheduleDays.toggles(chosen).flatMap { day ->
        val button = Query(text = day, role = FlowSteps.ROLE_BUTTON)
        listOf(FlowSteps.reveal(button), FlowSteps.tap(button))
    }

    private fun save(): List<Step> {
        val steps = mutableListOf<Step>()
        if (off) {
            // The Mac reads the row as a button and iOS as a switch, so the query names no role.
            val toggle = Query(textContains = "Schedule on,")
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

    /**
     * Opens the iOS compact time picker for [kind], turns its hour and minute wheels to [target], closes it, and reads
     * the picker back. The first picker is Starts and the second Ends; the wheels follow a 24-hour clock.
     */
    private fun setSystemTime(
        backend: Backend,
        kind: WheelKind,
        target: WheelTime,
    ) {
        val picker = Query(text = SYSTEM_PICKER, index = kind.ordinal)
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.reveal(picker),
                FlowSteps.tap(picker),
                FlowSteps.adjustWheels(listOf("%02d".format(target.hour), "%02d".format(target.minute))),
                FlowSteps.tap(Query(id = POPOVER_DISMISS)),
                FlowSteps.sleep(PICKER_SETTLE_SECONDS),
            ),
        )
        val shown = FlowSteps.nodes(backend).filter { node -> node.label == SYSTEM_PICKER }.getOrNull(kind.ordinal)?.value
        if (shown != target.text) {
            throw ControlException(ErrorCode.ASSERTION_FAILED, "${kind.label} shows $shown instead of ${target.text}.")
        }
    }

    private companion object {
        const val SYSTEM_PICKER = "Time Picker"
        const val POPOVER_DISMISS = "PopoverDismissRegion"
        const val PICKER_SETTLE_SECONDS = 1.0
        const val MAX_ROUNDS = 4
        const val SHORT_SECONDS = 3.0
    }
}

/**
 * Chooses [name] as the pause set. On the Mac a drawn "Pause set, <current>" button opens a menu, on iOS a system
 * pop-up button labelled "Pause set" opens the system menu; both menus' rows read "<name>, <detail>".
 */
internal fun chooseSet(
    backend: Backend,
    target: Target,
    name: String,
) {
    if (target == Target.DESKTOP) {
        val row = Query(textContains = "Pause set,", role = FlowSteps.ROLE_BUTTON)
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.reveal(row),
                FlowSteps.tap(row),
                // The drawn menu's rows read "<name>, <detail>" and the Mac reports them as text, so no role is named.
                FlowSteps.tap(Query(textContains = "$name, ")),
                FlowSteps.waitFor(Query(text = "Pause set, $name", role = FlowSteps.ROLE_BUTTON)),
            ),
        )
        return
    }
    val picker = Query(text = "Pause set", role = FlowSteps.ROLE_BUTTON)
    FlowSteps.run(
        backend,
        listOf(
            FlowSteps.reveal(picker),
            FlowSteps.tap(picker),
            FlowSteps.tap(Query(textContains = "$name, ", role = FlowSteps.ROLE_BUTTON)),
            FlowSteps.sleep(1.0),
        ),
    )
    val chosen = FlowSteps.nodes(backend).firstOrNull { node -> node.label == "Pause set" }?.value
    if (chosen != name) throw ControlException(ErrorCode.ASSERTION_FAILED, "Pause set shows $chosen instead of $name.")
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

private const val CONSENT_HINT = "Tap \"Allow schedules to start on this Mac\" on Schedules, or create the clone with `vm onboard --allow-schedules`."
