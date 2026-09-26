package app.posato.feature.session.ui

import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.sync.domain.SessionCandidate
import app.posato.feature.sync.domain.SessionReplicaSnapshot
import app.posato.feature.sync.domain.SyncReducer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull

internal class SessionTickPacer(
    private val status: StateFlow<LocalSessionStatus?>,
    private val clock: SessionClock,
    private val replica: suspend () -> SessionReplicaSnapshot?,
) {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    fun wake() {
        wakeups.trySend(Unit)
    }

    suspend fun pause(
        idleRecheckMillis: Long?,
        tickMillis: Long,
    ) {
        val idleWait = idleRecheckMillis?.takeUnless { status.value is LocalSessionStatus.Active }?.let { recheck ->
            idleWaitMillis(replica(), clock.currentEpochMillis(), recheck)
        }
        if (idleWait == null) {
            delay(tickMillis)
        } else {
            withTimeoutOrNull(idleWait) {
                merge(wakeups.receiveAsFlow(), status.filter { it is LocalSessionStatus.Active }.map {}).first()
            }
        }
    }
}

internal fun idleWaitMillis(
    snapshot: SessionReplicaSnapshot?,
    nowEpochMillis: Long,
    recheckMillis: Long,
): Long? {
    if (snapshot == null) return recheckMillis
    return when (val candidate = SyncReducer.describeSession(snapshot.projection, nowEpochMillis, snapshot.terminalExpiryFacts)) {
        is SessionCandidate.Current -> null
        is SessionCandidate.Future -> (candidate.start.startEpochMillis - nowEpochMillis).coerceIn(0L, recheckMillis)
        else -> recheckMillis
    }
}
