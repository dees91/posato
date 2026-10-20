package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.Target
import app.posato.control.model.Query

/**
 * Session's sync row and the folder section it opens. The row starts collapsed; it reads "iCloud" on an Apple host
 * before a folder is chosen and "Folder sync" after. Android reads only what is on screen, so a control below it is
 * scrolled to before the section is judged; a target that reads the whole page finds it at once.
 */
internal class FolderSection(
    private val backend: Backend,
) {
    /** Whether the section shows [label]. */
    fun shows(label: String): Boolean {
        FlowSteps.run(backend, listOf(FlowSteps.reveal(button(label)).copy(optional = true, timeoutSeconds = REVEAL_SECONDS)))
        return label in FlowSteps.labels(backend)
    }

    /** The labels an outcome is read from; on Android the linked row's Add a device may lie below the screen. */
    fun outcomeLabels(): List<String> {
        if (backend.target == Target.ANDROID) {
            val addDevice = button(FlowFolderCommand.ADD_DEVICE)
            FlowSteps.run(backend, listOf(FlowSteps.reveal(addDevice).copy(optional = true, timeoutSeconds = OUTCOME_REVEAL_SECONDS)))
        }
        return FlowSteps.labels(backend)
    }

    fun expand() {
        FlowSteps.run(backend, listOf(FlowSteps.button("Back to pause sets", optional = true, timeoutSeconds = 2.0), FlowSteps.button("Session")))
        if (FlowSteps.labels(backend).none { it in FlowFolderCommand.ACTIONS } && !expandedBelow()) {
            val title = if (FlowSteps.labels(backend).any { it.startsWith(FlowFolderCommand.FOLDER_ROW) }) {
                FlowFolderCommand.FOLDER_ROW
            } else {
                FlowFolderCommand.ICLOUD_ROW
            }
            val row = Query(textContains = title, role = FlowSteps.ROLE_BUTTON)
            FlowSteps.run(backend, listOf(FlowSteps.reveal(row), FlowSteps.tap(row), FlowSteps.sleep(FlowFolderCommand.DIALOG_SECONDS)))
        }
    }

    /** On Android the open section's controls can lie below the screen; its description right under the row shows it is open. */
    private fun expandedBelow(): Boolean {
        if (backend.target != Target.ANDROID) return false
        val description = Query(textContains = FOLDER_DESCRIPTION)
        FlowSteps.run(backend, listOf(FlowSteps.reveal(description).copy(optional = true, timeoutSeconds = REVEAL_SECONDS)))
        return FlowSteps.labels(backend).any { it.startsWith(FOLDER_DESCRIPTION) }
    }

    private fun button(text: String) = Query(text = text, role = FlowSteps.ROLE_BUTTON)

    private companion object {
        const val REVEAL_SECONDS = 8.0
        const val OUTCOME_REVEAL_SECONDS = 4.0
        const val FOLDER_DESCRIPTION = "Sync through a folder links your devices"
    }
}
