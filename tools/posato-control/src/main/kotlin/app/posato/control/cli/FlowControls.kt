package app.posato.control.cli

import app.posato.control.model.Query
import app.posato.control.model.Step

/** Presses Posato controls by catalog key. The key is the accessibility identifier, not the visible label. */
internal object FlowControls {
    fun buttonId(
        id: String,
        optional: Boolean = false,
        timeoutSeconds: Double? = null,
    ): Step = FlowSteps.tap(byId(id), optional, timeoutSeconds)

    fun byId(id: String): Query = Query(id = id, role = FlowSteps.ROLE_BUTTON)

    /**
     * The in-page Back on Mac, Android, Linux, and Windows, and the iOS bar's back button. The iOS bar keeps its
     * spoken phrase, because that phrase and a machine identifier share one accessibility field.
     */
    fun pauseSetsBack(apple: Boolean): Query = if (apple) {
        Query(text = "Back to Pause sets", role = FlowSteps.ROLE_BUTTON)
    } else {
        byId("action_back_to_pause_sets")
    }

    /** Same split as [pauseSetsBack], for the Schedules screen. */
    fun schedulesBack(apple: Boolean): Query = if (apple) {
        Query(text = "Back to Schedules", role = FlowSteps.ROLE_BUTTON)
    } else {
        byId("action_back_to_schedules")
    }
}
