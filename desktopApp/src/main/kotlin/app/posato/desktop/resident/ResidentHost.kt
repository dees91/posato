package app.posato.desktop.resident

import androidx.compose.ui.graphics.painter.Painter
import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.PresenceMenuModel
import app.posato.di.DesktopApplicationComponents
import app.posato.feature.about.ApplicationResidence
import app.posato.feature.presence.ResidentPresenceCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Files
import java.nio.file.Path

/**
 * A host's status icon and the requests that reach a running Posato: the icon's menu actions, and show requests from
 * the icon or a second launch. A host whose icon cannot tell when its menu opens leaves [menuOpened] empty; its menu
 * is then refreshed on every state change only.
 */
internal interface StatusSurface {
    /** Whether the icon is on screen right now, so a closed window can be shown again from it. */
    val iconShown: StateFlow<Boolean>
    val showRequests: Flow<Unit>
    val actions: Flow<PresenceMenuActionId>
    val menuOpened: Flow<Unit> get() = emptyFlow()

    fun update(model: PresenceMenuModel)
}

/** **Open Posato at login** as the host provides it: an autostart entry, a registry value, or a login item. */
internal interface LoginLaunch {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

/** Open at login and Quit for About Posato; quitting goes through the resident application's confirmation. */
internal class DesktopResidence(
    private val loginLaunch: LoginLaunch,
) : ApplicationResidence {
    private val enabled = MutableStateFlow(loginLaunch.isEnabled())

    @Volatile var onQuit: () -> Unit = {}

    override val opensAtLogin: StateFlow<Boolean> = enabled

    override fun setOpensAtLogin(enabled: Boolean) {
        runCatching { loginLaunch.setEnabled(enabled) }
        this.enabled.value = loginLaunch.isEnabled()
    }

    override fun quit() = onQuit()
}

/** The one-time notice after the first close that keeps Posato running, remembered in a file the host names. */
internal class FirstCloseMarker(
    private val marker: Path,
) {
    fun firstTime(): Boolean {
        if (Files.exists(marker)) return false
        runCatching {
            marker.parent?.let(Files::createDirectories)
            Files.createFile(marker)
        }
        return true
    }
}

/**
 * What a host that draws its own window hands to [ResidentPosato]. [status] is null when nothing could show a hidden
 * Posato again (Linux without a session bus): there is no icon, and closing the window quits. [windowIcon] is the
 * title bar's icon where the system does not take it from the installed application (Windows); null keeps the default.
 */
internal class ResidentHost(
    val graph: DesktopApplicationComponents,
    val copy: ResidentPresenceCopy,
    val status: StatusSurface?,
    val residence: DesktopResidence,
    val firstClose: FirstCloseMarker,
    val startHidden: Boolean,
    val windowIcon: Painter? = null,
)
