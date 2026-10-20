package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.Target
import app.posato.control.model.Query

/**
 * Session's sync row and the folder section it opens. The row starts collapsed; it reads "iCloud" on an Apple host
 * before a folder is chosen and "Folder sync" after. Android and iOS read only what is on screen, so a control below
 * it is scrolled to before the section is judged; the Mac and Linux read the whole page and find it at once.
 */
internal class FolderSection(
    private val backend: Backend,
) {
    /** Whether the section shows [label]. */
    fun shows(label: String): Boolean {
        FlowSteps.run(backend, listOf(FlowSteps.reveal(button(label)).copy(optional = true, timeoutSeconds = REVEAL_SECONDS)))
        return label in FlowSteps.labels(backend)
    }

    /**
     * The labels an outcome is read from. Android and iOS list only what is on screen, so the first of [reveal] that
     * the section shows is scrolled to first.
     */
    fun outcomeLabels(reveal: List<String>): List<String> {
        if (backend.target in VISIBLE_ONLY) {
            reveal.firstOrNull { label ->
                val query = button(label)
                FlowSteps.run(backend, listOf(FlowSteps.reveal(query).copy(optional = true, timeoutSeconds = OUTCOME_REVEAL_SECONDS)))
                label in FlowSteps.labels(backend)
            }
        }
        return FlowSteps.labels(backend)
    }

    /** Opens the section; a tap that turns out to have closed an open section the screen did not show is undone. */
    fun expand() {
        FlowSteps.run(backend, listOf(FlowSteps.button("Back to pause sets", optional = true, timeoutSeconds = 2.0), FlowSteps.button("Session")))
        if (open()) return
        val row = Query(textContains = rowTitle(), role = FlowSteps.ROLE_BUTTON)
        FlowSteps.run(backend, listOf(FlowSteps.reveal(row), FlowSteps.tap(row), FlowSteps.sleep(FlowFolderCommand.DIALOG_SECONDS)))
        if (!open()) FlowSteps.run(backend, listOf(FlowSteps.reveal(row), FlowSteps.tap(row), FlowSteps.sleep(FlowFolderCommand.DIALOG_SECONDS)))
    }

    /** Whether the section is open: one of its controls shows, after scrolling to it on a target that lists only the screen. */
    private fun open(): Boolean {
        if (FlowSteps.labels(backend).any { it in FlowFolderCommand.ACTIONS }) return true
        if (backend.target !in VISIBLE_ONLY) return false
        return outcomeLabels(FlowFolderCommand.ACTIONS.toList()).any { it in FlowFolderCommand.ACTIONS }
    }

    /**
     * The row's title. iOS and Android list only what is on screen, and the row can lie below it, so a row that
     * shows neither title is scrolled to as Folder sync first.
     */
    private fun rowTitle(): String {
        val labels = FlowSteps.labels(backend)
        if (labels.any { it.startsWith(FlowFolderCommand.FOLDER_ROW) }) return FlowFolderCommand.FOLDER_ROW
        if (labels.any { it.startsWith(FlowFolderCommand.ICLOUD_ROW) }) return FlowFolderCommand.ICLOUD_ROW
        val folderRow = Query(textContains = FlowFolderCommand.FOLDER_ROW, role = FlowSteps.ROLE_BUTTON)
        FlowSteps.run(backend, listOf(FlowSteps.reveal(folderRow).copy(optional = true, timeoutSeconds = REVEAL_SECONDS)))
        val folder = FlowSteps.labels(backend).any { it.startsWith(FlowFolderCommand.FOLDER_ROW) }
        return if (folder) FlowFolderCommand.FOLDER_ROW else FlowFolderCommand.ICLOUD_ROW
    }

    private fun button(text: String) = Query(text = text, role = FlowSteps.ROLE_BUTTON)

    private companion object {
        const val REVEAL_SECONDS = 8.0
        const val OUTCOME_REVEAL_SECONDS = 3.0
        val VISIBLE_ONLY = setOf(Target.ANDROID, Target.SIMULATOR, Target.DEVICE)
    }
}
