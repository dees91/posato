package app.posato.desktop

import app.posato.desktop.resident.FirstCloseMarker
import app.posato.feature.presence.PresenceCopy
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.QuitPrompt

internal enum class FirstCloseChoice { HIDE, QUIT }

internal suspend fun confirmsQuit(
    copy: PresenceCopy,
    prompt: QuitPrompt,
): Boolean {
    val message = when (prompt) {
        QuitPrompt.NONE -> return true
        QuitPrompt.CONFIRM_ENFORCING -> copy.quitEnforcing
        QuitPrompt.CONFIRM_NOT_ENFORCING -> copy.quitNotEnforcing
        QuitPrompt.CONFIRM_SCHEDULES -> copy.quitSchedules
    }
    return MacPresenceEvents.presentAlert(copy.quitTitle, message, copy.quitKeep, copy.quitConfirm) == SECONDARY_CHOICE
}

/** The first close during a pause explains that Posato keeps running, once, and offers to quit instead. */
internal suspend fun firstCloseChoice(
    menu: PresenceMenu,
    copy: PresenceCopy,
    marker: FirstCloseMarker,
): FirstCloseChoice {
    val sessionRunning = menu.state == PresenceState.ENFORCING || menu.state == PresenceState.NOT_ENFORCING
    if (!sessionRunning || !marker.firstTime()) return FirstCloseChoice.HIDE
    val message = if (menu.state == PresenceState.ENFORCING) copy.firstCloseEnforcing else copy.firstCloseNotEnforcing
    val choice = MacPresenceEvents.presentAlert(message, "", copy.firstCloseOk, copy.quit)
    return if (choice == SECONDARY_CHOICE) FirstCloseChoice.QUIT else FirstCloseChoice.HIDE
}

private const val SECONDARY_CHOICE: Int = 1
