package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Drives the folder workspace of ADR 0010 on Session's sync row: `link` chooses the folder and links (creating a
 * workspace, or ending in "waiting for a code" when the folder already holds one), `offer` shows a pairing code and
 * returns it with its dialog left open, `done` closes that dialog and withdraws the offer, `join` enters a code, and
 * `remove` removes the workspace. Each waits for the row to show the outcome.
 */
class FlowFolderCommand : ControlCommand("folder", "Link a folder workspace, show or enter a pairing code, or remove the workspace.") {
    private val action by argument(help = "link | offer | done | join | remove").choice(LINK, OFFER, DONE_ACTION, JOIN, REMOVE)
    private val path by option("--path", help = "The folder to link, as the device sees it (link).")
    private val code by option("--code", help = "The pairing code from another device (join).")
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for the outcome.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val backend = session.backend()
        val deadline = System.currentTimeMillis() + timeoutSeconds * MILLIS_PER_SECOND
        if (action == DONE_ACTION) {
            FlowSteps.run(backend, listOf(FlowSteps.button(DONE, optional = true, timeoutSeconds = 2.0)))
            return buildJsonObject { put("action", action) }
        }
        expand(backend)
        return when (action) {
            LINK -> link(backend, requireOption(path, "--path"), deadline)
            OFFER -> offer(backend)
            JOIN -> join(backend, requireOption(code, "--code"), deadline)
            else -> remove(backend, deadline)
        }
    }

    private fun link(
        backend: Backend,
        folder: String,
        deadline: Long,
    ): JsonElement {
        // An unlinked device chooses its folder again, as a person would, so no earlier choice carries over.
        if (USE_ANOTHER in FlowSteps.labels(backend)) {
            FlowSteps.run(backend, listOf(FlowSteps.reveal(button(USE_ANOTHER)), FlowSteps.tap(button(USE_ANOTHER))))
        }
        if (USE_FOLDER in FlowSteps.labels(backend)) {
            FlowSteps.run(
                backend,
                listOf(FlowSteps.reveal(button(USE_FOLDER)), FlowSteps.typeInto(folder, submit = false), FlowSteps.tap(button(USE_FOLDER))),
            )
        }
        FlowSteps.run(backend, listOf(FlowSteps.reveal(button(SYNC_WITH_FOLDER)), FlowSteps.tap(button(SYNC_WITH_FOLDER))))
        val outcome = waitFor(backend, deadline, "link") { labels ->
            when {
                ADD_DEVICE in labels -> "linked"
                JOIN_LABEL in labels -> "waitingForCode"
                else -> null
            }
        }
        return buildJsonObject {
            put("action", action)
            put("outcome", outcome)
        }
    }

    private fun offer(backend: Backend): JsonElement {
        FlowSteps.run(backend, listOf(FlowSteps.reveal(button(ADD_DEVICE)), FlowSteps.tap(button(ADD_DEVICE)), FlowSteps.sleep(DIALOG_SECONDS)))
        val shown = FlowSteps.nodes(backend).flatMap { listOfNotNull(it.label, it.value) }.firstNotNullOfOrNull { CODE_PATTERN.find(it)?.value }
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "No pairing code was shown after Add a device.")
        // The offer stays in the folder while its dialog is open; `flow folder done` (or Done) withdraws it.
        return buildJsonObject {
            put("action", action)
            put("code", shown.replace(" ", ""))
        }
    }

    private fun join(
        backend: Backend,
        pairingCode: String,
        deadline: Long,
    ): JsonElement {
        FlowSteps.run(
            backend,
            listOf(FlowSteps.reveal(button(JOIN_LABEL)), FlowSteps.typeInto(pairingCode, submit = false), FlowSteps.tap(button(JOIN_LABEL))),
        )
        // An offer that has not reached this device's folder yet reads "No offer for this code"; a person presses Join again.
        waitFor(backend, deadline, "join") { labels ->
            when {
                ADD_DEVICE in labels -> "linked"

                labels.any {
                    it.startsWith(NOT_YET)
                } && JOIN_LABEL in labels -> null.also { FlowSteps.run(backend, listOf(FlowSteps.tap(button(JOIN_LABEL)))) }

                else -> null
            }
        }
        return buildJsonObject {
            put("action", action)
            put("outcome", "linked")
        }
    }

    private fun remove(
        backend: Backend,
        deadline: Long,
    ): JsonElement {
        var presses = 0
        waitFor(backend, deadline, "removal") { labels ->
            when {
                USE_ANOTHER in labels || USE_FOLDER in labels -> "removed"

                REMOVE_WORKSPACE in labels -> null.also {
                    confirmRemoval(backend)
                    presses++
                }

                else -> null
            }
        }
        return buildJsonObject {
            put("action", action)
            put("presses", presses)
        }
    }

    /** Presses the row's Remove workspace, then the confirmation's, which is the match that is not the row's own button. */
    private fun confirmRemoval(backend: Backend) {
        val row = backend.snapshot(button(REMOVE_WORKSPACE), null).path
        FlowSteps.run(
            backend,
            listOf(FlowSteps.reveal(button(REMOVE_WORKSPACE)), FlowSteps.tap(button(REMOVE_WORKSPACE)), FlowSteps.sleep(DIALOG_SECONDS)),
        )
        val confirm = FlowSteps.nodes(backend)
            .filter { it.label == REMOVE_WORKSPACE && it.role == FlowSteps.ROLE_BUTTON }
            .mapNotNull { it.path }
            .firstOrNull { it != row }
        FlowSteps.run(backend, listOf(FlowSteps.tap(if (confirm != null) Query(path = confirm) else button(REMOVE_WORKSPACE))))
    }

    /** The sync row starts collapsed; it reads "iCloud" on an Apple host before a folder is chosen and "Folder sync" after. */
    private fun expand(backend: Backend) {
        FlowSteps.run(backend, listOf(FlowSteps.button("Back to pause sets", optional = true, timeoutSeconds = 2.0), FlowSteps.button("Session")))
        if (FlowSteps.labels(backend).none { it in ACTIONS }) {
            val title = if (FlowSteps.labels(backend).any { it.startsWith(FOLDER_ROW) }) FOLDER_ROW else ICLOUD_ROW
            val row = Query(textContains = title, role = FlowSteps.ROLE_BUTTON)
            FlowSteps.run(backend, listOf(FlowSteps.reveal(row), FlowSteps.tap(row), FlowSteps.sleep(DIALOG_SECONDS)))
        }
    }

    private fun waitFor(
        backend: Backend,
        deadline: Long,
        what: String,
        outcome: (List<String>) -> String?,
    ): String {
        var labels = FlowSteps.labels(backend)
        while (System.currentTimeMillis() < deadline) {
            outcome(labels)?.let { return it }
            Thread.sleep(POLL_MILLIS)
            labels = FlowSteps.labels(backend)
        }
        val row = labels.firstOrNull { it.startsWith(FOLDER_ROW) || it.startsWith(ICLOUD_ROW) }
        throw ControlException(ErrorCode.WAIT_TIMEOUT, "The folder $what did not finish within $timeoutSeconds s; the row reads ${row ?: "unknown"}.")
    }

    private fun requireOption(
        value: String?,
        name: String,
    ): String {
        return value ?: throw ControlException(ErrorCode.USAGE, "`flow folder $action` needs $name.")
    }

    private fun button(text: String) = Query(text = text, role = FlowSteps.ROLE_BUTTON)

    private companion object {
        const val LINK = "link"
        const val OFFER = "offer"
        const val JOIN = "join"
        const val REMOVE = "remove"
        const val DONE_ACTION = "done"
        const val USE_FOLDER = "Use this folder"
        const val SYNC_WITH_FOLDER = "Sync with this folder"
        const val ADD_DEVICE = "Add a device"
        const val JOIN_LABEL = "Join"
        const val DONE = "Done"
        const val NOT_YET = "No offer for this code"
        const val USE_ANOTHER = "Use another folder"
        const val REMOVE_WORKSPACE = "Remove workspace"
        const val FOLDER_ROW = "Folder sync,"
        const val ICLOUD_ROW = "iCloud,"
        const val POLL_MILLIS = 3_000L
        const val DIALOG_SECONDS = 1.5
        const val MILLIS_PER_SECOND = 1_000L
        const val DEFAULT_TIMEOUT_SECONDS = 180L
        val CODE_PATTERN = Regex("(?:[A-Z2-7]{3} ){8}[A-Z2-7]{3}")
        val ACTIONS = setOf(USE_FOLDER, SYNC_WITH_FOLDER, ADD_DEVICE, JOIN_LABEL, USE_ANOTHER, REMOVE_WORKSPACE, "Sync with iCloud", "Sync now")
    }
}
