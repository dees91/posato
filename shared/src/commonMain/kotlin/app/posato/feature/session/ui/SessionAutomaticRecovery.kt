package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.session.domain.SessionClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MACOS-029 (ADR 0004 amendment): where the Mac would ask the person to apply an active pause again, it
 * first applies it by itself through the standing grant, under the same gate as a scheduled start. The
 * notice the person would have seen stays the internal state, so the transition drain stays converged,
 * while Session shows a quiet caption; it comes back unchanged when the recovery stops without applying.
 *
 * One loss gets at most three attempts spaced over about a minute, and one pause at most three recoveries
 * in ten minutes, so a helper that keeps failing still reaches the notice. One recovery runs at a time,
 * and an attempt that has begun is never cancelled halfway through an apply. Without a gate, as on hosts
 * that re-apply without a prompt, the notice shows at once.
 */
internal class SessionAutomaticRecovery(
    private val scope: CoroutineScope,
    private val gate: ScheduleStartGate?,
    private val clock: SessionClock,
    private val view: MutableStateFlow<EnforcementViewState>,
) {
    private var job: Job? = null
    private var jobTag: SessionTag? = null
    private val started = mutableMapOf<SessionTag, MutableList<Long>>()

    /** Shows [notice] for [tag], or a quiet recovery whose attempts run [reconcile] with the grant. */
    fun askOrRecover(
        tag: SessionTag,
        notice: EnforcementState.ActionRequired,
        shown: EnforcedSet?,
        reconcile: suspend () -> Unit,
    ) {
        if (jobTag == tag && job?.isActive == true) return
        val recovering = gate != null && claim(tag)
        view.update { it.copy(state = notice, enforced = shown ?: it.enforced, recovering = recovering) }
        if (!recovering) return
        job?.cancel()
        jobTag = tag
        job = scope.launch {
            var applied = false
            try {
                applied = attempts(checkNotNull(gate), notice, reconcile)
            } finally {
                view.update { current ->
                    val restore = !applied && current.state is EnforcementState.ActionRequired
                    current.copy(state = if (restore) notice else current.state, recovering = false)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun claim(tag: SessionTag): Boolean {
        val now = clock.currentEpochMillis()
        val recent = started.getOrPut(tag) { mutableListOf() }
        recent.removeAll { at -> now - at >= RECOVERY_WINDOW_MILLIS }
        if (recent.size >= MAX_RECOVERIES) return false
        recent += now
        return true
    }

    private suspend fun attempts(
        gate: ScheduleStartGate,
        notice: EnforcementState.ActionRequired,
        reconcile: suspend () -> Unit,
    ): Boolean {
        var stop = false
        for (wait in ATTEMPT_DELAYS_MILLIS) {
            delay(wait)
            when (gate.check()) {
                StartGate.READY -> if (withContext(NonCancellable) { attempt(notice, reconcile) }) return true
                StartGate.TRANSIENT -> Unit
                StartGate.SETUP_REQUIRED, StartGate.OTHER_ACCOUNT -> stop = true
            }
            if (stop || view.value.state !is EnforcementState.ActionRequired) break
        }
        return false
    }

    /** One attempt; a pause that ended or was replaced meanwhile leaves its own state and stops the loop. */
    private suspend fun attempt(
        notice: EnforcementState.ActionRequired,
        reconcile: suspend () -> Unit,
    ): Boolean {
        view.update { it.copy(busy = true) }
        try {
            reconcile()
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            view.update { it.copy(state = notice) }
        } finally {
            view.update { it.copy(busy = false) }
        }
        val state = view.value.state
        if (state is EnforcementState.Active) return true
        if (state != notice) view.update { if (it.state is EnforcementState.Inactive) it else it.copy(state = notice) }
        return false
    }

    private companion object {
        val ATTEMPT_DELAYS_MILLIS = listOf(0L, 15_000L, 30_000L)
        const val MAX_RECOVERIES = 3
        const val RECOVERY_WINDOW_MILLIS = 10 * 60_000L
    }
}
