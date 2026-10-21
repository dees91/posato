package app.posato.desktop.linux.status

import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.presenceMenuModel
import app.posato.feature.presence.PresenceAction
import app.posato.feature.presence.PresenceCopy
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusMenuEntriesTest {
    @Test
    fun `given no session then the panel shows the state line and Start a session, Open Posato, and Quit Posato`() {
        val entries = statusMenuEntries(model(PresenceMenu(PresenceState.NO_SESSION, PresenceAction.START_SESSION, null), now = 0))

        assertEquals(
            listOf("No session active" to false, "" to false, "Start a session…" to true, "Open Posato" to true, "" to false, "Quit Posato" to true),
            entries.map { it.label to it.enabled },
        )
        assertEquals(2, entries.count { it.separator })
    }

    @Test
    fun `given a running pause when the minutes change then every entry keeps its id so the panel updates in place`() {
        val menu = PresenceMenu(PresenceState.ENFORCING, PresenceAction.END_SESSION_EARLY, END)
        val earlier = statusMenuEntries(model(menu, now = END - 30 * MINUTE))
        val later = statusMenuEntries(model(menu, now = END - 29 * MINUTE))

        assertEquals(earlier.map { it.id }, later.map { it.id })
        assertTrue(earlier.map { it.label } != later.map { it.label })
    }

    @Test
    fun `given the same action in two states then it has one id, and every id is distinct and not the root`() {
        val enforcing = statusMenuEntries(model(PresenceMenu(PresenceState.ENFORCING, PresenceAction.END_SESSION_EARLY, END), now = 0))
        val notEnforcing = statusMenuEntries(model(PresenceMenu(PresenceState.NOT_ENFORCING, PresenceAction.RESUME_RESTRICTIONS, END), now = 0))
        val endEarly = { entries: List<StatusMenuEntry> -> entries.single { it.action == PresenceMenuActionId.END_SESSION_EARLY }.id }

        assertEquals(endEarly(enforcing), endEarly(notEnforcing))
        listOf(enforcing, notEnforcing).forEach { entries ->
            assertEquals(entries.size, entries.map { it.id }.distinct().size)
            assertTrue(entries.none { it.id == 0 })
        }
    }

    private fun model(
        menu: PresenceMenu,
        now: Long,
    ) = presenceMenuModel(menu, COPY, now) { "15:30" }

    private companion object {
        const val MINUTE = 60_000L
        const val END = 10 * 60 * MINUTE
        val COPY = PresenceCopy(
            noSession = "No session active",
            activeUntil = "Session active until %1\$s",
            minutesLeft = "%1\$d min left",
            lessThanAMinute = "Less than a minute left",
            notEnforcing = "Restrictions not active on this computer",
            maintenance = "Update in progress",
            startSession = "Start a session…",
            endSessionEarly = "End session early…",
            resumeRestrictions = "Resume restrictions…",
            open = "Open Posato",
            quit = "Quit Posato",
            closeWindow = "Close Window",
            accessibilityEnforcing = "Posato, restrictions active",
            accessibilityNotEnforcing = "Posato, restrictions not active",
            accessibilityIdle = "Posato",
            quitTitle = "Quit Posato?",
            quitEnforcing = "",
            quitNotEnforcing = "",
            quitSchedules = "",
            quitKeep = "Keep Posato open",
            quitConfirm = "Quit",
            firstCloseEnforcing = "",
            firstCloseNotEnforcing = "",
            firstCloseOk = "OK",
            loginItem = "Open Posato at login",
            loginItemSupporting = "",
        )
    }
}
