package app.posato.feature.session.ui

import app.posato.feature.enforcement.EnforcedSet
import app.posato.feature.enforcement.EnforcementState
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.session.domain.SessionClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    /** Whether [SessionTag] is still the active pause the owner wants; a recovery writes nothing otherwise. */
    private val isCurrent: suspend (SessionTag) -> Boolean,
) {
    private class Running(
        val tag: SessionTag,
        val job: Job,
    )

    private val running = MutableStateFlow<Running?>(null)
    private val started = mutableMapOf<SessionTag, MutableList<Long>>()

    /** Shows [notice] for [tag], or a quiet recovery whose attempts run [reconcile] with the grant. */
    fun askOrRecover(
        tag: SessionTag,
        notice: EnforcementState.ActionRequired,
        shown: EnforcedSet?,
        reconcile: suspend () -> Unit,
    ) {
        val current = running.value
        if (current != null && current.tag == tag && current.job.isActive) return
        val job = scope.launch(start = CoroutineStart.LAZY) { run(tag, notice, reconcile) }
        // Two triggers for one loss can arrive on different threads; only one of them starts a recovery.
        val recovering = gate != null && running.compareAndSet(current, Running(tag, job)) && claim(tag)
        view.update { it.copy(state = notice, enforced = shown ?: it.enforced, recovering = recovering) }
        if (recovering) {
            current?.job?.cancel()
            job.start()
        } else {
            job.cancel()
        }
    }

    /** The person acted, as with Retry: the recovery stops and its caption goes with it. */
    fun cancel() {
        running.value?.job?.cancel()
        view.update { it.copy(recovering = false) }
    }

    private suspend fun run(
        tag: SessionTag,
        notice: EnforcementState.ActionRequired,
        reconcile: suspend () -> Unit,
    ) {
        var applied = false
        try {
            applied = attempts(checkNotNull(gate), tag, notice, reconcile)
        } finally {
            withContext(NonCancellable) {
                // A pause that ended or was replaced keeps the state its own transition gave it.
                val restore = !applied && isCurrent(tag)
                val latest = running.value?.tag == tag
                view.update { current ->
                    val state = if (restore && current.state is EnforcementState.ActionRequired) notice else current.state
                    current.copy(state = state, recovering = current.recovering && !latest)
                }
            }
        }
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
        tag: SessionTag,
        notice: EnforcementState.ActionRequired,
        reconcile: suspend () -> Unit,
    ): Boolean {
        var applied = false
        var stopped = false
        val waits = ATTEMPT_DELAYS_MILLIS.iterator()
        while (!applied && !stopped && waits.hasNext()) {
            delay(waits.next())
            val answer = if (isCurrent(tag)) gate.check() else StartGate.SETUP_REQUIRED
            stopped = answer == StartGate.SETUP_REQUIRED || answer == StartGate.OTHER_ACCOUNT
            applied = answer == StartGate.READY && withContext(NonCancellable) { attempt(tag, notice, reconcile) }
        }
        return applied
    }

    /** One attempt; it writes the notice back only while its pause is still the one the owner wants. */
    private suspend fun attempt(
        tag: SessionTag,
        notice: EnforcementState.ActionRequired,
        reconcile: suspend () -> Unit,
    ): Boolean {
        view.update { it.copy(busy = true) }
        try {
            reconcile()
        } catch (expectedCancellation: CancellationException) {
            throw expectedCancellation
        } catch (_: Exception) {
            // The state below is checked against the owner's row before the notice is written back.
        } finally {
            view.update { it.copy(busy = false) }
        }
        if (view.value.state is EnforcementState.Active) return true
        if (isCurrent(tag)) view.update { if (it.state is EnforcementState.Active) it else it.copy(state = notice) }
        return false
    }

    private companion object {
        val ATTEMPT_DELAYS_MILLIS = listOf(0L, 15_000L, 30_000L)
        const val MAX_RECOVERIES = 3
        const val RECOVERY_WINDOW_MILLIS = 10 * 60_000L
    }
}
