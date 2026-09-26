package app.posato.feature.presence

import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.update.MaintenanceAdmission
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
public class DesktopPresence internal constructor(
    private val owner: SessionTransitionOwner,
    private val sync: AppleSync,
    private val maintenance: MaintenanceAdmission,
) {
    private val requests = Channel<SessionWindowRequest>(Channel.CONFLATED)

    public val menu: Flow<PresenceMenu> = combine(owner.status, owner.view, maintenance.closed) { status, view, closed ->
        presenceMenuOf(status, view, closed)
    }.distinctUntilChanged()

    public val windowRequests: Flow<SessionWindowRequest> = requests.receiveAsFlow()

    public suspend fun runWhileResident() {
        maintenance.closedGate()
        coroutineScope {
            launch { owner.runWhileHosted(idleRecheckMillis = IDLE_RECHECK_MILLIS) }
            launch { runPeriodicExchange(exchange = sync::onForeground, intervalMillis = EXCHANGE_INTERVAL_MILLIS) }
        }
    }

    public suspend fun onMenuOpened() {
        owner.onForeground()
        sync.onForeground()
    }

    public fun request(request: SessionWindowRequest) {
        requests.trySend(request)
    }
}

private const val IDLE_RECHECK_MILLIS: Long = 60_000L
private const val EXCHANGE_INTERVAL_MILLIS: Long = 30 * 60_000L
