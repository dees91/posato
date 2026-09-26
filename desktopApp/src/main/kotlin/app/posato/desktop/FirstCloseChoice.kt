package app.posato.desktop

import app.posato.feature.presence.PresenceCopy
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState
import app.posato.feature.presence.QuitPrompt
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class FirstCloseChoice { HIDE, QUIT }

internal suspend fun confirmsQuit(
    copy: PresenceCopy,
    prompt: QuitPrompt,
): Boolean {
    val message = when (prompt) {
        QuitPrompt.NONE -> return true
        QuitPrompt.CONFIRM_ENFORCING -> copy.quitEnforcing
        QuitPrompt.CONFIRM_NOT_ENFORCING -> copy.quitNotEnforcing
    }
    return MacPresenceEvents.presentAlert(copy.quitTitle, message, copy.quitKeep, copy.quitConfirm) == SECONDARY_CHOICE
}

internal suspend fun firstCloseChoice(
    menu: PresenceMenu,
    copy: PresenceCopy,
): FirstCloseChoice {
    val sessionRunning = menu.state == PresenceState.ENFORCING || menu.state == PresenceState.NOT_ENFORCING
    val marker = firstCloseMarker()
    if (!sessionRunning || marker.exists()) return FirstCloseChoice.HIDE
    val message = if (menu.state == PresenceState.ENFORCING) copy.firstCloseEnforcing else copy.firstCloseNotEnforcing
    val choice = MacPresenceEvents.presentAlert(message, "", copy.firstCloseOk, copy.quit)
    runCatching {
        marker.parentFile?.mkdirs()
        marker.createNewFile()
    }
    return if (choice == SECONDARY_CHOICE) FirstCloseChoice.QUIT else FirstCloseChoice.HIDE
}

internal fun formatClockTime(epochMillis: Long): String {
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

private fun firstCloseMarker(): File {
    return File(System.getProperty("user.home"), "Library/Application Support/Posato/presence-first-close")
}

private const val SECONDARY_CHOICE: Int = 1
