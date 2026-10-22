package app.posato.desktop.linux

import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.linux.status.LinuxStatusArea
import app.posato.di.DesktopApplicationComponents
import app.posato.feature.presence.LinuxPresenceCopy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * What the status icon and a second launch ask of the resident application. Created before the bus is
 * connected, so a request that arrives while Posato is still starting waits for the window instead of being lost.
 */
internal class LinuxHostEvents {
    private val shows = Channel<Unit>(Channel.CONFLATED)
    private val menuOpens = Channel<Unit>(Channel.CONFLATED)
    private val menuActions = Channel<PresenceMenuActionId>(Channel.BUFFERED)

    val showRequests: Flow<Unit> = shows.receiveAsFlow()
    val menuOpened: Flow<Unit> = menuOpens.receiveAsFlow()
    val actions: Flow<PresenceMenuActionId> = menuActions.receiveAsFlow()

    fun show() {
        shows.trySend(Unit)
    }

    fun openMenu() {
        menuOpens.trySend(Unit)
    }

    fun act(action: PresenceMenuActionId) {
        menuActions.trySend(action)
    }
}

/** What a Linux launch hands to the resident application. */
internal class LinuxResidentHost(
    val graph: DesktopApplicationComponents,
    val copy: LinuxPresenceCopy,
    val events: LinuxHostEvents,
    val statusArea: LinuxStatusArea?,
    val residence: LinuxResidence,
    val firstClose: LinuxFirstCloseMarker,
    val startHidden: Boolean,
)
