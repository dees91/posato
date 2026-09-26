package app.posato.feature.presence

import app.posato.feature.enforcement.EnforcementActionKind
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.ui.EnforcementViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

public enum class PresenceState { LOADING, NO_SESSION, ENFORCING, NOT_ENFORCING, MAINTENANCE }

public enum class PresenceAction { START_SESSION, END_SESSION_EARLY, RESUME_RESTRICTIONS, OPEN_POSATO }

public enum class QuitPrompt { NONE, CONFIRM_ENFORCING, CONFIRM_NOT_ENFORCING }

public data class PresenceMenu(
    val state: PresenceState,
    val primaryAction: PresenceAction,
    val sessionEndEpochMillis: Long?,
)

internal fun presenceMenuOf(
    status: LocalSessionStatus?,
    view: EnforcementViewState,
    maintenanceClosed: Boolean?,
): PresenceMenu {
    val active = status as? LocalSessionStatus.Active
    val end = active?.record?.endEpochMillis
    val enforcement = view.state
    return when {
        maintenanceClosed == true -> PresenceMenu(PresenceState.MAINTENANCE, PresenceAction.OPEN_POSATO, end)
        status == null -> PresenceMenu(PresenceState.LOADING, PresenceAction.OPEN_POSATO, null)
        active == null -> PresenceMenu(PresenceState.NO_SESSION, PresenceAction.START_SESSION, null)
        enforcement is EnforcementState.Active -> PresenceMenu(PresenceState.ENFORCING, PresenceAction.END_SESSION_EARLY, end)
        enforcement.isResumable() -> PresenceMenu(PresenceState.NOT_ENFORCING, PresenceAction.RESUME_RESTRICTIONS, end)
        else -> PresenceMenu(PresenceState.NOT_ENFORCING, PresenceAction.OPEN_POSATO, end)
    }
}

private fun EnforcementState.isResumable(): Boolean {
    return this is EnforcementState.ActionRequired &&
        (kind == EnforcementActionKind.RESUME_REQUIRED || kind == EnforcementActionKind.APPLY_FAILED)
}

public fun quitPromptFor(
    menu: PresenceMenu,
    systemTerminationEpochMillis: Long?,
    nowEpochMillis: Long,
): QuitPrompt {
    val systemTerminating = systemTerminationEpochMillis != null &&
        nowEpochMillis - systemTerminationEpochMillis in 0..SYSTEM_TERMINATION_WINDOW_MILLIS
    return when {
        systemTerminating -> QuitPrompt.NONE
        menu.state == PresenceState.ENFORCING -> QuitPrompt.CONFIRM_ENFORCING
        menu.state == PresenceState.NOT_ENFORCING -> QuitPrompt.CONFIRM_NOT_ENFORCING
        else -> QuitPrompt.NONE
    }
}

internal suspend fun runPeriodicExchange(
    exchange: suspend () -> Unit,
    intervalMillis: Long,
) {
    while (currentCoroutineContext().isActive) {
        try {
            exchange()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
        }
        delay(intervalMillis)
    }
}

private const val SYSTEM_TERMINATION_WINDOW_MILLIS: Long = 60_000L
