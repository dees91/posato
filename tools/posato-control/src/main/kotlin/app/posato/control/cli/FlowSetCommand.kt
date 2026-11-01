package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import app.posato.control.model.Step
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Creates a pause set, or renames one with --rename-from, and with --make-default makes it the default, all from
 * Pause sets and its set menus. Renaming the unnamed first set uses its shown name, "My set". --window sets or
 * clears a website's window rule (TARGETS-013) through the website row's Open briefly… dialog.
 */
class FlowSetCommand : ControlCommand("set", "Create, rename, or make default a pause set, and set its window rules.") {
    private val name by option("--name", help = "Set name; the new name with --rename-from.").required()
    private val websites by option("--website", help = "A website to add to a new set; repeat for more.").multiple()
    private val renameFrom by option("--rename-from", help = "Rename the set shown with this name, such as \"My set\", to --name.")
    private val makeDefault by option("--make-default", help = "Make the set named --name the default.").flag()
    private val windows by option(
        "--window",
        help = "A website's window rule in the set, <website>=<period>/<length> (30, 60, or 120 and 5, 10, or 15) or <website>=off; repeat for more.",
    ).multiple()

    override fun execute(session: Session): JsonElement {
        if (renameFrom != null && websites.isNotEmpty()) {
            throw ControlException(ErrorCode.USAGE, "--website adds to a new set; it does not combine with --rename-from.")
        }
        val backend = session.backend()
        // The iOS bar keeps the spoken "Back to Pause sets". Other hosts press the in-page Back by its catalog key.
        val apple = session.target().isApple
        val rules = windowRules(apple)
        val back = FlowControls.pauseSetsBack(apple)
        openList(backend, back)
        val labels = FlowSteps.labels(backend)
        val from = renameFrom
        when {
            from != null -> rename(backend, from, labels, apple)
            hasSetNamed(labels, name) && editsOnly(rules) -> Unit
            else -> create(backend, back, labels, apple)
        }
        if (makeDefault && !isDefault(FlowSteps.labels(backend), name)) {
            FlowSteps.run(backend, listOf(FlowSteps.button(menuFor(name)), FlowSteps.button("Make default")))
            awaitList { labels -> isDefault(labels, name) }(backend, "Pause sets shows no default set named $name.")
        }
        if (rules.isNotEmpty()) {
            setWindows(backend, back, rules)
        }
        return buildJsonObject {
            put("name", name)
            renameFrom?.let { put("renamedFrom", it) }
            put("isDefault", isDefault(FlowSteps.labels(backend), name))
            putJsonArray("websites") { websites.forEach(::add) }
            putJsonArray("windows") { rules.forEach { add(it.toString()) } }
        }
    }

    private fun openList(
        backend: Backend,
        back: Query,
    ) {
        FlowSteps.run(
            backend,
            listOf(
                FlowControls.buttonId("nav_pause_sets"),
                FlowSteps.tap(back, optional = true, timeoutSeconds = SHORT_SECONDS),
                FlowSteps.waitFor(FlowControls.byId("action_new_set")),
            ),
        )
    }

    private fun rename(
        backend: Backend,
        from: String,
        labels: List<String>,
        apple: Boolean,
    ) {
        // A rerun after a rename that went through finds the new name and has nothing left to do.
        if (!hasSetNamed(labels, from) && hasSetNamed(labels, name)) return
        if (!hasSetNamed(labels, from)) {
            throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "Pause sets shows no set named $from.", "Check --rename-from.")
        }
        if (from != name && hasSetNamed(labels, name)) {
            throw ControlException(ErrorCode.ALREADY_EXISTS, "Pause sets already lists a set named $name.", "Choose another --name.")
        }
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.button(menuFor(from)),
                FlowSteps.button("Rename"),
                FlowSteps.typeInto(name, submit = false),
                FlowControls.alertButton("action_save_short", "Save", apple),
            ),
        )
        awaitList { current -> hasSetNamed(current, name) }(backend, "Pause sets shows no set named $name after the rename.")
    }

    private fun create(
        backend: Backend,
        back: Query,
        labels: List<String>,
        apple: Boolean,
    ) {
        // Posato accepts a second set with the same name, so a rerun would leave two rows the later steps cannot tell apart.
        if (hasSetNamed(labels, name)) {
            throw ControlException(
                ErrorCode.ALREADY_EXISTS,
                "Pause sets already lists a set named $name.",
                "Choose another --name, or remove the existing set first.",
            )
        }
        val steps = mutableListOf<Step>(
            FlowControls.buttonId("action_new_set"),
            FlowSteps.typeInto(name, submit = false),
            FlowControls.alertButton("action_save_short", "Save", apple),
            FlowSteps.waitFor(back),
        )
        if (websites.isNotEmpty()) {
            steps += FlowSteps.waitFor(Query(text = "Search", role = FlowSteps.ROLE_BUTTON))
            steps += FlowSteps.typeInto(websites.joinToString(", "), submit = true)
            steps += FlowControls.buttonId("sync_folder_code_done", optional = true, timeoutSeconds = SHORT_SECONDS)
        }
        steps += listOf(FlowSteps.tap(back), FlowSteps.screenshot("set-created"))
        FlowSteps.run(backend, steps)
        // The list redraws after Back; a screen read while it does shows the list before the save.
        awaitList { current -> hasSetNamed(current, name) }(backend, "Pause sets shows no set named $name.")
    }

    private fun windowRules(apple: Boolean): List<WindowRule> {
        val rules = windows.map(WindowRule::parse)
        if (rules.isNotEmpty() && apple) {
            throw ControlException(
                ErrorCode.USAGE,
                "--window drives the website row's menu, which iOS shows as a swipe; it runs on desktop and Android.",
            )
        }
        return rules
    }

    /** An existing set is only made default or given rules; nothing is created. */
    private fun editsOnly(rules: List<WindowRule>): Boolean = websites.isEmpty() && (makeDefault || rules.isNotEmpty())

    /** Opens the set, then each website's Open briefly… dialog, chooses the rule or Don't open, and returns to the list. */
    private fun setWindows(
        backend: Backend,
        back: Query,
        rules: List<WindowRule>,
    ) {
        val steps = mutableListOf(FlowSteps.tap(Query(textContains = "$name, ", role = FlowSteps.ROLE_BUTTON)), FlowSteps.waitFor(back))
        rules.forEach { rule ->
            steps += FlowSteps.tap(Query(text = "Actions for ${rule.website}", role = FlowSteps.ROLE_BUTTON))
            steps += FlowControls.buttonId("targets_open_briefly")
            if (rule.period == null || rule.length == null) {
                steps += FlowControls.buttonId("targets_window_clear")
            } else {
                steps += FlowControls.buttonId("targets_window_period_${rule.period}")
                steps += FlowControls.buttonId("targets_window_length_${rule.length}")
                steps += FlowControls.buttonId("action_save_short")
                steps += FlowSteps.waitFor(Query(textContains = rule.caption()))
            }
        }
        steps += listOf(FlowSteps.screenshot("set-windows"), FlowSteps.tap(back))
        FlowSteps.run(backend, steps)
        awaitList { current -> hasSetNamed(current, name) }(backend, "Pause sets shows no set named $name after its rules.")
    }

    /** One --window value: a website and its rule, or off when [period] and [length] are null. */
    private class WindowRule(
        val website: String,
        val period: Int?,
        val length: Int?,
    ) {
        /** The row's caption for the rule, as English shows it. */
        fun caption(): String {
            val every = when (period) {
                30 -> "every 30 min"
                60 -> "every hour"
                else -> "every 2 hours"
            }
            return "Opens $every for $length min"
        }

        override fun toString(): String = if (period == null) "$website=off" else "$website=$period/$length"

        companion object {
            private val periods = setOf(30, 60, 120)
            private val lengths = setOf(5, 10, 15)

            fun parse(value: String): WindowRule {
                val website = value.substringBefore('=', "").trim()
                val rule = value.substringAfter('=', "").trim()
                if (website.isEmpty()) throw usage(value)
                if (rule == "off") return WindowRule(website, null, null)
                val period = rule.substringBefore('/').toIntOrNull()?.takeIf { it in periods }
                val length = rule.substringAfter('/', "").toIntOrNull()?.takeIf { it in lengths }
                return if (period != null && length != null) WindowRule(website, period, length) else throw usage(value)
            }

            private fun usage(value: String): ControlException = ControlException(
                ErrorCode.USAGE,
                "--window $value is not <website>=<period>/<length> or <website>=off.",
                "Periods are 30, 60, or 120 minutes; lengths are 5, 10, or 15.",
            )
        }
    }

    private companion object {
        const val SHORT_SECONDS = 3.0
        const val LIST_SECONDS = 10L
        const val MILLIS_PER_SECOND = 1_000L
        const val POLL_MILLIS = 500L

        fun menuFor(set: String): String = "More actions for $set"

        /**
         * A row reads "<name>, [Default set, Default, ]<n> websites · …". Matching through the website count keeps
         * "Deep Work" and "Work, personal" from counting as a set named "Work".
         */
        fun hasSetNamed(
            labels: List<String>,
            set: String,
        ): Boolean {
            val row = Regex("^${Regex.escape(set)}, (Default set, )?(Default, )?\\d+ websites?\\b")
            return labels.any { label -> row.containsMatchIn(label) }
        }

        fun isDefault(
            labels: List<String>,
            set: String,
        ): Boolean {
            val row = Regex("^${Regex.escape(set)}, (Default set, |Default, )+\\d+ websites?\\b")
            return labels.any { label -> row.containsMatchIn(label) }
        }

        /** Polls the list until [shows] holds, since it redraws after a dialog or Back closes. */
        fun awaitList(shows: (List<String>) -> Boolean): (Backend, String) -> Unit = { backend, failure ->
            val deadline = System.currentTimeMillis() + LIST_SECONDS * MILLIS_PER_SECOND
            while (!shows(FlowSteps.labels(backend))) {
                if (System.currentTimeMillis() > deadline) throw ControlException(ErrorCode.ASSERTION_FAILED, failure)
                Thread.sleep(POLL_MILLIS)
            }
        }
    }
}
