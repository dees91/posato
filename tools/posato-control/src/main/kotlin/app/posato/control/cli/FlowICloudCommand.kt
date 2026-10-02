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
 * Links this device to the iCloud workspace or removes it, and waits for the outcome. A link that waits for the
 * workspace key presses Check again; a removal that did not finish is pressed again, as a person would. The state is
 * read from the row's buttons, never from its sentence, since "Sync with iCloud is running." also names the action.
 */
class FlowICloudCommand : ControlCommand("icloud", "Link this device to the iCloud workspace, or remove the workspace, and wait for the outcome.") {
    private val action by argument(help = "link | remove").choice(LINK, REMOVE)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for the outcome.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        // Verified only in Tart guests: on a compact iPhone the expanded row's actions can sit off screen.
        requireDesktopInVirtualMachine(session)
        val backend = session.backend()
        val deadline = System.currentTimeMillis() + timeoutSeconds * MILLIS_PER_SECOND
        val presses = if (action == LINK) link(backend, deadline) else remove(backend, deadline)
        return buildJsonObject {
            put("action", action)
            put("presses", presses)
        }
    }

    private fun link(
        backend: Backend,
        deadline: Long,
    ): Int {
        expand(backend)
        var presses = 0
        while (System.currentTimeMillis() < deadline) {
            val labels = FlowSteps.labels(backend)
            if (linked(labels)) return presses
            // A join already pending, also from a run that timed out, offers Check again instead of Sync with iCloud.
            val next = listOf(CHECK_AGAIN, SYNC_WITH_ICLOUD).firstOrNull { it in labels }
            if (next != null) {
                FlowSteps.run(backend, listOf(FlowSteps.reveal(button(next)), FlowSteps.tap(button(next))))
                presses++
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw timedOut("link")
    }

    private fun remove(
        backend: Backend,
        deadline: Long,
    ): Int {
        expand(backend)
        var presses = 0
        while (System.currentTimeMillis() < deadline) {
            val labels = FlowSteps.labels(backend)
            if (SYNC_WITH_ICLOUD in labels) return presses
            if (REMOVE_WORKSPACE in labels && labels.none { it.contains(SYNCING) }) {
                confirmRemoval(backend)
                presses++
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw timedOut("removal")
    }

    /** Opens the confirmation and presses its Remove workspace, which is the match that is not the row's own button. */
    private fun confirmRemoval(backend: Backend) {
        val row = backend.snapshot(button(REMOVE_WORKSPACE), null).path
        FlowSteps.run(
            backend,
            listOf(FlowSteps.reveal(button(REMOVE_WORKSPACE)), FlowSteps.tap(button(REMOVE_WORKSPACE)), FlowSteps.sleep(DIALOG_SECONDS)),
        )
        val matches = mutableListOf<String>()
        backend.snapshot(null, null).walk { node ->
            if (node.label == REMOVE_WORKSPACE &&
                node.role == FlowSteps.ROLE_BUTTON
            ) {
                node.path?.let(matches::add)
            }
        }
        val confirm = matches.firstOrNull { it != row }
        val query = if (confirm != null) Query(path = confirm) else button(REMOVE_WORKSPACE)
        FlowSteps.run(backend, listOf(FlowSteps.tap(query)))
    }

    /** Session's iCloud row starts collapsed; its actions appear once it is opened. */
    private fun expand(backend: Backend) {
        FlowSteps.run(backend, listOf(FlowSteps.button("Back to pause sets", optional = true, timeoutSeconds = 2.0), FlowSteps.button("Session")))
        val labels = FlowSteps.labels(backend)
        if (labels.none { label -> label in ACTIONS }) {
            val row = Query(textContains = "iCloud,", role = FlowSteps.ROLE_BUTTON)
            FlowSteps.run(backend, listOf(FlowSteps.reveal(row), FlowSteps.tap(row), FlowSteps.sleep(DIALOG_SECONDS)))
        }
    }

    /** Linked: the row offers Sync now or Remove workspace, whatever the latest attempt reported. */
    private fun linked(labels: List<String>): Boolean = labels.any { it.contains(COMPLETED) } || SYNC_NOW in labels || REMOVE_WORKSPACE in labels

    private fun button(text: String) = Query(text = text, role = FlowSteps.ROLE_BUTTON)

    private fun timedOut(what: String) = ControlException(
        ErrorCode.WAIT_TIMEOUT,
        "The iCloud $what did not finish within $timeoutSeconds s.",
        "On a Tart guest, check `vm icloud --line <line>`; a paused iCloud Keychain never delivers the workspace key.",
    )

    private companion object {
        const val LINK = "link"
        const val REMOVE = "remove"
        const val SYNC_WITH_ICLOUD = "Sync with iCloud"
        const val SYNC_NOW = "Sync now"
        const val CHECK_AGAIN = "Check again"
        const val REMOVE_WORKSPACE = "Remove workspace"
        const val COMPLETED = "completed its latest sync attempt"
        const val SYNCING = "Syncing"
        const val DEFAULT_TIMEOUT_SECONDS = 300L
        const val POLL_MILLIS = 15_000L
        const val DIALOG_SECONDS = 2.0
        const val MILLIS_PER_SECOND = 1_000L
        val ACTIONS = setOf(SYNC_WITH_ICLOUD, SYNC_NOW, CHECK_AGAIN, REMOVE_WORKSPACE)
    }
}
