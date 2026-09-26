package app.posato.desktop

import app.posato.feature.presence.PresenceAction
import app.posato.feature.presence.PresenceCopy
import app.posato.feature.presence.PresenceMenu
import app.posato.feature.presence.PresenceState

internal data class PresenceMenuModel(
    val items: List<PresenceMenuItem>,
    val accessibilityDescription: String,
    val filled: Boolean,
)

internal enum class PresenceMenuActionId(
    val code: Int
) {
    START_SESSION(0),
    END_SESSION_EARLY(1),
    RESUME_RESTRICTIONS(2),
    OPEN_POSATO(3),
    QUIT(4),
    ;

    companion object {
        fun of(code: Int): PresenceMenuActionId? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

internal fun presenceMenuModel(
    menu: PresenceMenu,
    copy: PresenceCopy,
    nowEpochMillis: Long,
    formatTime: (Long) -> String,
): PresenceMenuModel {
    val status = mutableListOf<PresenceMenuItem>()
    val actions = mutableListOf<PresenceMenuItem>()
    val end = menu.sessionEndEpochMillis
    when (menu.state) {
        PresenceState.LOADING -> {}

        PresenceState.NO_SESSION -> {
            status += label(copy.noSession)
            actions += item(copy.startSession, PresenceMenuActionId.START_SESSION)
        }

        PresenceState.ENFORCING -> {
            end?.let { status += label(copy.activeUntil.format(formatTime(it))) }
            end?.let { status += label(remaining(copy, it - nowEpochMillis)) }
            actions += item(copy.endSessionEarly, PresenceMenuActionId.END_SESSION_EARLY)
        }

        PresenceState.NOT_ENFORCING -> {
            status += label(copy.notEnforcing)
            if (menu.primaryAction == PresenceAction.RESUME_RESTRICTIONS) {
                actions += item(copy.resumeRestrictions, PresenceMenuActionId.RESUME_RESTRICTIONS)
            }
            actions += item(copy.endSessionEarly, PresenceMenuActionId.END_SESSION_EARLY)
        }

        PresenceState.MAINTENANCE -> {
            status += label(copy.maintenance)
        }
    }
    actions += item(copy.open, PresenceMenuActionId.OPEN_POSATO)
    val items = buildList {
        addAll(status)
        if (status.isNotEmpty()) add(SEPARATOR)
        addAll(actions)
        add(SEPARATOR)
        add(item(copy.quit, PresenceMenuActionId.QUIT))
    }
    val description = when (menu.state) {
        PresenceState.ENFORCING -> copy.accessibilityEnforcing
        PresenceState.NOT_ENFORCING -> copy.accessibilityNotEnforcing
        else -> copy.accessibilityIdle
    }
    return PresenceMenuModel(items, description, filled = menu.state == PresenceState.ENFORCING)
}

private fun remaining(
    copy: PresenceCopy,
    millis: Long,
): String {
    val minutes = (millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS
    return if (minutes <= 1) copy.lessThanAMinute else copy.minutesLeft.format(minutes)
}

private fun label(title: String): PresenceMenuItem {
    return PresenceMenuItem(title, NO_ACTION, enabled = false)
}

private fun item(
    title: String,
    action: PresenceMenuActionId,
): PresenceMenuItem {
    return PresenceMenuItem(title, action.code)
}

private const val NO_ACTION: Int = -1
private const val MINUTE_MILLIS: Long = 60_000L
private val SEPARATOR = PresenceMenuItem("", NO_ACTION, enabled = false)
