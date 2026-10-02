package app.posato.feature.presence

import app.posato.feature.notifications.SessionNotifier
import app.posato.feature.schedules.host.ScheduleHost
import app.posato.feature.session.ui.SessionComposition
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.recompose
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.update.MaintenanceAdmission
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
public class DesktopPresence internal constructor(
    private val owner: SessionTransitionOwner,
    private val sync: AppleSync,
    private val maintenance: MaintenanceAdmission,
    private val notifier: SessionNotifier,
    private val schedules: ScheduleHost,
    private val composition: SessionComposition,
    private val policies: LocalTargetPolicyStore,
    private val applicationMappings: LocalApplicationMappings,
) {
    private val requests = Channel<SessionWindowRequest>(Channel.CONFLATED)

    public val menu: Flow<PresenceMenu> = combine(
        owner.status,
        owner.view,
        maintenance.closed,
        schedules.pause,
        schedules.anyEnabled,
    ) { status, view, closed, scheduled, enabled ->
        presenceMenuOf(status, view, closed, scheduled, enabled)
    }.distinctUntilChanged()

    public val windowRequests: Flow<SessionWindowRequest> = requests.receiveAsFlow()

    public suspend fun runWhileResident() {
        maintenance.closedGate()
        coroutineScope {
            launch { owner.runWhileHosted(idleRecheckMillis = IDLE_RECHECK_MILLIS) }
            // An edit to a set the running session uses, here or received, pauses its additions at once.
            launch { merge(policies.policyChanges, applicationMappings.invalidations).collect { owner.recompose(composition) } }
            launch { notifier.run() }
            launch { schedules.run() }
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
