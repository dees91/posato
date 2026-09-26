package app.posato.feature.presence

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionEndKind
import app.posato.feature.session.domain.SessionOrigin
import app.posato.feature.session.domain.SessionRecord
import app.posato.feature.session.ui.EnforcementViewState
import app.posato.feature.sync.domain.SessionId
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceMenuTest {
    @Test
    fun `given each session and enforcement state when the menu is derived then only confirmed restrictions read as enforcing`() {
        val cases = listOf(
            Case(null, EnforcementState.Inactive, false, PresenceState.LOADING, PresenceAction.OPEN_POSATO),
            Case(LocalSessionStatus.Inactive, EnforcementState.Inactive, false, PresenceState.NO_SESSION, PresenceAction.START_SESSION),
            Case(ENDED, EnforcementState.Inactive, false, PresenceState.NO_SESSION, PresenceAction.START_SESSION),
            Case(ACTIVE, EnforcementState.Active(false), false, PresenceState.ENFORCING, PresenceAction.END_SESSION_EARLY),
            Case(ACTIVE, required(EnforcementActionKind.RESUME_REQUIRED), false, PresenceState.NOT_ENFORCING, PresenceAction.RESUME_RESTRICTIONS),
            Case(ACTIVE, required(EnforcementActionKind.APPLY_FAILED), false, PresenceState.NOT_ENFORCING, PresenceAction.RESUME_RESTRICTIONS),
            Case(ACTIVE, required(EnforcementActionKind.CLEAR_FAILED), false, PresenceState.NOT_ENFORCING, PresenceAction.OPEN_POSATO),
            Case(ACTIVE, EnforcementState.Inactive, false, PresenceState.NOT_ENFORCING, PresenceAction.OPEN_POSATO),
            Case(ACTIVE, EnforcementState.Active(false), true, PresenceState.MAINTENANCE, PresenceAction.OPEN_POSATO),
        )

        cases.forEach { case ->
            val menu = presenceMenuOf(case.status, EnforcementViewState(state = case.enforcement), case.maintenanceClosed)

            assertEquals(case.expectedState, menu.state, "state for $case")
            assertEquals(case.expectedAction, menu.primaryAction, "action for $case")
        }
    }

    @Test
    fun `given a system termination within its time box when quitting during enforcement then nothing is asked`() {
        val menu = PresenceMenu(PresenceState.ENFORCING, PresenceAction.END_SESSION_EARLY, END)

        assertEquals(QuitPrompt.NONE, quitPromptFor(menu, systemTerminationEpochMillis = NOW - 1_000, nowEpochMillis = NOW))
    }

    @Test
    fun `given a stale system termination from a cancelled logout when quitting during enforcement then the confirmation returns`() {
        val menu = PresenceMenu(PresenceState.ENFORCING, PresenceAction.END_SESSION_EARLY, END)

        assertEquals(QuitPrompt.CONFIRM_ENFORCING, quitPromptFor(menu, systemTerminationEpochMillis = NOW - 120_000, nowEpochMillis = NOW))
    }

    @Test
    fun `given a closed maintenance gate when quitting then an update relaunch is never delayed`() {
        val menu = PresenceMenu(PresenceState.MAINTENANCE, PresenceAction.OPEN_POSATO, END)

        assertEquals(QuitPrompt.NONE, quitPromptFor(menu, systemTerminationEpochMillis = null, nowEpochMillis = NOW))
    }

    @Test
    fun `given a resident process when time passes then it exchanges at start and every interval`() = runTest {
        var exchanges = 0
        val running = launch { runPeriodicExchange(exchange = { exchanges += 1 }, intervalMillis = INTERVAL) }
        runCurrent()
        assertEquals(1, exchanges)

        advanceTimeBy(INTERVAL - 1)
        runCurrent()
        assertEquals(1, exchanges)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, exchanges)
        running.cancel()
    }

    private data class Case(
        val status: LocalSessionStatus?,
        val enforcement: EnforcementState,
        val maintenanceClosed: Boolean,
        val expectedState: PresenceState,
        val expectedAction: PresenceAction,
    )

    private companion object {
        const val NOW: Long = 1_000_000_000_000L
        const val END: Long = NOW + 30 * 60_000L
        const val INTERVAL: Long = 30 * 60_000L
        val RECORD = SessionRecord(SessionId(testIdentifier(3)), NOW, END)
        val ACTIVE = LocalSessionStatus.Active(RECORD, END - NOW, null, SessionOrigin.LOCAL)
        val ENDED = LocalSessionStatus.Ended(RECORD, SessionEndKind.ENDED_EARLY, SessionOrigin.LOCAL)

        fun required(kind: EnforcementActionKind): EnforcementState {
            return EnforcementState.ActionRequired(kind, repeatsSystemPrompt = true)
        }
    }
}
