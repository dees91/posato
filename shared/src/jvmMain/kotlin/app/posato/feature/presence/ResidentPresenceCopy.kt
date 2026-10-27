package app.posato.feature.presence

import app.posato.generated.resources.Res
import app.posato.generated.resources.presence_linux_first_close
import app.posato.generated.resources.presence_linux_first_close_no_icon
import app.posato.generated.resources.presence_linux_not_enforcing
import app.posato.generated.resources.presence_linux_quit_schedules
import org.jetbrains.compose.resources.getString

/**
 * The wording of a host that draws its own resident window (Linux, LINUX-003): no menu bar and no Mac, and a quit
 * that only scheduled pauses need to confirm.
 */
public data class ResidentPresenceCopy(
    val presence: PresenceCopy,
    val firstClose: String,
    val firstCloseNoIcon: String,
    val quitSchedules: String,
)

public suspend fun loadLinuxPresenceCopy(): ResidentPresenceCopy {
    val shared = loadPresenceCopy()
    return ResidentPresenceCopy(
        presence = shared.copy(notEnforcing = getString(Res.string.presence_linux_not_enforcing)),
        firstClose = getString(Res.string.presence_linux_first_close),
        firstCloseNoIcon = getString(Res.string.presence_linux_first_close_no_icon),
        quitSchedules = getString(Res.string.presence_linux_quit_schedules),
    )
}
