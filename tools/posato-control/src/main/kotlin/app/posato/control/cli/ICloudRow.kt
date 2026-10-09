package app.posato.control.cli

import app.posato.control.backend.Backend

/** Reads Session's iCloud row for `flow icloud`: whether it is linked, whether it has settled, and its state. */
internal object ICloudRow {
    /** The labels on screen and the buttons among them that can be pressed, read from one snapshot. */
    data class Labels(
        val texts: List<String>,
        val enabledButtons: Set<String>,
    )

    fun read(backend: Backend): Labels {
        val texts = mutableListOf<String>()
        val enabledButtons = mutableSetOf<String>()
        backend.snapshot(null, null).walk { node ->
            node.label?.let { label ->
                texts.add(label)
                if (node.role == FlowSteps.ROLE_BUTTON && node.enabled) enabledButtons.add(label)
            }
        }
        return Labels(texts, enabledButtons)
    }

    /** Linked: the row offers Sync now or Remove workspace, whatever the latest attempt reported. */
    fun linked(labels: Labels): Boolean {
        val texts = labels.texts
        return texts.any { it.contains(COMPLETED) } || SYNC_NOW in texts || REMOVE_WORKSPACE in texts
    }

    /**
     * Settled: nothing runs and Remove workspace can be pressed. The row disables its buttons while a sync runs or the
     * key is checked, and says Syncing or Checking meanwhile; both signals count, since the accessibility tree does
     * not report every disabled button.
     */
    fun settled(labels: Labels): Boolean = REMOVE_WORKSPACE in labels.enabledButtons && !running(labels) && !checking(labels)

    /** The row's state for an envelope: what runs, or else the latest attempt, so a timeout says where it stopped. */
    fun state(labels: Labels): String? {
        val texts = labels.texts
        return when {
            running(labels) -> "syncing"
            checking(labels) -> "checking key"
            SYNC_WITH_ICLOUD in texts -> "not linked"
            texts.any { it.contains(DID_NOT_FINISH) } -> "sync did not finish"
            texts.any { it.contains(COMPLETED) } -> "sync completed"
            REMOVE_WORKSPACE in texts -> if (REMOVE_WORKSPACE in labels.enabledButtons) "linked" else "linked, buttons disabled"
            else -> null
        }
    }

    private fun running(labels: Labels): Boolean = labels.texts.any { it.contains(SYNCING) || it.contains(RUNNING) }

    private fun checking(labels: Labels): Boolean = labels.texts.any { it.startsWith(CHECKING) }

    const val SYNC_WITH_ICLOUD = "Sync with iCloud"
    const val SYNC_NOW = "Sync now"
    const val REMOVE_WORKSPACE = "Remove workspace"
    private const val COMPLETED = "completed its latest sync attempt"
    private const val SYNCING = "Syncing"
    private const val RUNNING = "Sync with iCloud is running"
    private const val CHECKING = "Checking for the workspace key"
    private const val DID_NOT_FINISH = "Sync didn"
}
