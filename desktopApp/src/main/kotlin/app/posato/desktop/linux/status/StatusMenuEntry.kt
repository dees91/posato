package app.posato.desktop.linux.status

import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.PresenceMenuModel

/** One line of the status icon's menu, with an id that stays the same while the line's role does. */
internal data class StatusMenuEntry(
    val id: Int,
    val label: String,
    val enabled: Boolean,
    val separator: Boolean,
    val action: PresenceMenuActionId?,
)

/**
 * The Linux status menu from the same [PresenceMenuModel] as the Mac menu. An action keeps one id in every state,
 * and a state line or separator keeps the id of its position, so a panel updates the open menu in place.
 */
internal fun statusMenuEntries(model: PresenceMenuModel): List<StatusMenuEntry> {
    var lines = 0
    var separators = 0
    return model.items.map { item ->
        val action = PresenceMenuActionId.of(item.action)
        when {
            action != null -> StatusMenuEntry(ACTION_IDS + action.ordinal, item.title, item.enabled, separator = false, action = action)
            item.title.isEmpty() -> StatusMenuEntry(SEPARATOR_IDS + separators++, "", enabled = false, separator = true, action = null)
            else -> StatusMenuEntry(LINE_IDS + lines++, item.title, enabled = false, separator = false, action = null)
        }
    }
}

private const val LINE_IDS = 1
private const val SEPARATOR_IDS = 100
private const val ACTION_IDS = 200
