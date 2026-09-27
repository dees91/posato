package app.posato.feature.enforcement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One helper, two reasons to restrict: a manual session and the running scheduled occurrences. Each
 * holds a claim, and restrictions are cleared only when neither does, so a manual session that ends
 * inside a schedule, or a schedule that ends inside a session, never lifts the other's restrictions.
 * It remembers whose request the helper actually holds, so a claim that only joined never touches it.
 */
internal class PauseClaims(
    private val delegate: EnforcementPort,
) {
    private enum class Holder { NONE, MANUAL, SCHEDULE }

    private val mutex = Mutex()
    private var manualRequest: EnforcementRequest? = null
    private var scheduleRequest: EnforcementRequest? = null
    private var holder = Holder.NONE
    private var clearPending = false

    /** The view the session owner uses; it sees only its own claim. */
    val manual: EnforcementPort = ManualView()

    /**
     * Restricts for the running occurrences through the standing grant only. It joins applied manual
     * restrictions without touching the helper, because a failed apply clears what the helper holds;
     * when that manual session ends first, its clear applies this request again.
     */
    suspend fun claimSchedule(request: EnforcementRequest): EnforcementApplyReport {
        return mutex.withLock {
            val joined = manualRequest != null && delegate.status() == EnforcementOutcome.APPLIED
            val report = if (joined) {
                EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
            } else {
                delegate.apply(request.withGrantOnly()).also { report -> holder = report.holderAfter(Holder.SCHEDULE) }
            }
            scheduleRequest = request.takeIf { report.outcome == EnforcementOutcome.APPLIED }
            if (scheduleRequest != null) {
                clearPending = false
            }
            report
        }
    }

    /**
     * Lifts the schedule's claim; nothing happens when it holds none and no clear is pending. The helper
     * is cleared only when no manual claim remains, and a clear that failed is retried on the next call.
     * A manual session that outlasts the schedule gets its own request and end again when the helper
     * held the schedule's.
     */
    suspend fun releaseSchedule(): EnforcementOutcome {
        return mutex.withLock {
            val released = scheduleRequest
            if (released == null && !clearPending) {
                return@withLock EnforcementOutcome.CLEARED
            }
            scheduleRequest = null
            val manual = manualRequest
            when {
                manual == null -> {
                    clearHelper()
                }

                holder == Holder.SCHEDULE -> {
                    val report = delegate.apply(manual.withGrantOnly())
                    holder = report.holderAfter(Holder.MANUAL)
                    if (report.outcome != EnforcementOutcome.APPLIED) {
                        manualRequest = null
                    }
                    EnforcementOutcome.CLEARED
                }

                else -> {
                    EnforcementOutcome.CLEARED
                }
            }
        }
    }

    /** Whether a failed clear still waits; the host retries it each minute. */
    val hasPendingClear: Boolean
        get() {
            return clearPending
        }

    /** Whether the schedule's restrictions still hold; a helper that lost them drops the claim so the next attempt applies again. */
    suspend fun scheduleStatus(): EnforcementOutcome {
        return mutex.withLock {
            if (scheduleRequest == null) {
                EnforcementOutcome.CLEARED
            } else {
                delegate.status().also { status -> if (status == EnforcementOutcome.CLEARED) scheduleRequest = null }
            }
        }
    }

    /** Drops the claim after repeated unanswered reads without clearing, so the next attempt's apply replaces the helper's state. */
    suspend fun forgetSchedule() {
        mutex.withLock { scheduleRequest = null }
    }

    private suspend fun clearHelper(): EnforcementOutcome {
        val outcome = delegate.clear()
        clearPending = outcome != EnforcementOutcome.CLEARED
        if (!clearPending) {
            holder = Holder.NONE
        }
        return outcome
    }

    private fun EnforcementApplyReport.holderAfter(applying: Holder): Holder {
        return if (outcome == EnforcementOutcome.APPLIED) applying else Holder.NONE
    }

    private inner class ManualView : EnforcementPort {
        override val reapplyRequiresPrompt: Boolean
            get() {
                return delegate.reapplyRequiresPrompt
            }

        /** A manual start or Resume while the schedule's restrictions hold joins them without a helper call. */
        override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
            return mutex.withLock {
                val joined = scheduleRequest != null && delegate.status() == EnforcementOutcome.APPLIED
                val report = if (joined) {
                    EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
                } else {
                    delegate.apply(request).also { report -> holder = report.holderAfter(Holder.MANUAL) }
                }
                manualRequest = request.takeIf { report.outcome == EnforcementOutcome.APPLIED }
                if (report.outcome != EnforcementOutcome.APPLIED) {
                    // A failed apply clears the helper, so the schedule's claim no longer holds either.
                    scheduleRequest = null
                }
                report
            }
        }

        /**
         * Ending a manual session inside a schedule hands the helper back to the schedule's own request
         * and end when it held the manual one; if that fails, it is cleared and the host applies again.
         */
        override suspend fun clear(): EnforcementOutcome {
            return mutex.withLock {
                manualRequest = null
                val schedule = scheduleRequest
                when {
                    schedule == null -> {
                        clearHelper()
                    }

                    holder == Holder.MANUAL -> {
                        val report = delegate.apply(schedule.withGrantOnly())
                        holder = report.holderAfter(Holder.SCHEDULE)
                        if (report.outcome != EnforcementOutcome.APPLIED) {
                            scheduleRequest = null
                            clearHelper()
                        }
                        EnforcementOutcome.CLEARED
                    }

                    else -> {
                        EnforcementOutcome.CLEARED
                    }
                }
            }
        }

        override suspend fun status(): EnforcementOutcome {
            return mutex.withLock {
                if (manualRequest == null && scheduleRequest != null) EnforcementOutcome.CLEARED else delegate.status()
            }
        }

        override suspend fun holdsSession(sessionId: String): Boolean {
            return delegate.holdsSession(sessionId)
        }

        override suspend fun peekSuspendedExpiry(sessionId: String): Boolean {
            return delegate.peekSuspendedExpiry(sessionId)
        }

        override suspend fun acknowledgeSuspendedExpiry(sessionId: String): Boolean {
            return delegate.acknowledgeSuspendedExpiry(sessionId)
        }

        override suspend fun displacedSuspendedExpiry(currentSessionId: String): ExpiryDisplacement {
            return delegate.displacedSuspendedExpiry(currentSessionId)
        }
    }
}
