package app.posato.desktop.linux

import app.posato.desktop.PresenceMenuActionId
import app.posato.desktop.PresenceMenuModel
import app.posato.desktop.linux.status.LinuxStatusArea
import app.posato.desktop.linux.status.StatusAreaState
import app.posato.desktop.linux.status.statusMenuEntries
import app.posato.desktop.resident.StatusSurface
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

/** The Linux status icon on the session bus, with the requests that reach it, as the resident core sees them. */
internal class LinuxStatusSurface(
    private val area: LinuxStatusArea,
    events: LinuxHostEvents,
) : StatusSurface {
    override val iconShown: StateFlow<Boolean> = area.watcherPresent
    override val showRequests: Flow<Unit> = events.showRequests
    override val actions: Flow<PresenceMenuActionId> = events.actions
    override val menuOpened: Flow<Unit> = events.menuOpened

    override fun update(model: PresenceMenuModel) {
        area.update(StatusAreaState(statusMenuEntries(model), "Posato", model.accessibilityDescription, model.filled))
    }
}
