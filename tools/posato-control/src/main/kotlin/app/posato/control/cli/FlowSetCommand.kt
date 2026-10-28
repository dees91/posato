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
 * Pause sets and its set menus. Renaming the unnamed first set uses its shown name, "My set".
 */
class FlowSetCommand : ControlCommand("set", "Create, rename, or make default a pause set.") {
    private val name by option("--name", help = "Set name; the new name with --rename-from.").required()
    private val websites by option("--website", help = "A website to add to a new set; repeat for more.").multiple()
    private val renameFrom by option("--rename-from", help = "Rename the set shown with this name, such as \"My set\", to --name.")
    private val makeDefault by option("--make-default", help = "Make the set named --name the default.").flag()

    override fun execute(session: Session): JsonElement {
        if (renameFrom != null && websites.isNotEmpty()) {
            throw ControlException(ErrorCode.USAGE, "--website adds to a new set; it does not combine with --rename-from.")
        }
        val backend = session.backend()
        // The iOS bar keeps the spoken "Back to Pause sets". Other hosts press the in-page Back by its catalog key.
        val back = FlowControls.pauseSetsBack(session.target().isApple)
        openList(backend, back)
        val labels = FlowSteps.labels(backend)
        val from = renameFrom
        when {
            from != null -> rename(backend, from, labels)
            hasSetNamed(labels, name) && makeDefault && websites.isEmpty() -> Unit
            else -> create(backend, back, labels)
        }
        if (makeDefault && !isDefault(FlowSteps.labels(backend), name)) {
            FlowSteps.run(backend, listOf(FlowSteps.button(menuFor(name)), FlowSteps.button("Make default")))
            awaitList { labels -> isDefault(labels, name) }(backend, "Pause sets shows no default set named $name.")
        }
        return buildJsonObject {
            put("name", name)
            renameFrom?.let { put("renamedFrom", it) }
            put("isDefault", isDefault(FlowSteps.labels(backend), name))
            putJsonArray("websites") { websites.forEach(::add) }
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
                FlowControls.buttonId("action_save_short"),
            ),
        )
        awaitList { current -> hasSetNamed(current, name) }(backend, "Pause sets shows no set named $name after the rename.")
    }

    private fun create(
        backend: Backend,
        back: Query,
        labels: List<String>,
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
            FlowControls.buttonId("action_save_short"),
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
