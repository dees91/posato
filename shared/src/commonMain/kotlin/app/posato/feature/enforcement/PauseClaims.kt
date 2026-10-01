package app.posato.feature.enforcement

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One helper, two reasons to restrict: a manual session and the running scheduled occurrences. Each
 * holds a claim, and restrictions are cleared only when neither does, so a manual session that ends
 * inside a schedule, or a schedule that ends inside a session, never lifts the other's restrictions.
 * It remembers whose request the helper actually holds and that request's targets and end, so a claim
 * that only joined never touches it and every comparison is made against what the helper enforces.
 */
internal class PauseClaims(
    private val delegate: EnforcementPort,
) : ScheduleClaims {
    private enum class Holder { NONE, MANUAL, SCHEDULE }

    private val mutex = Mutex()
    private var manualRequest: EnforcementRequest? = null
    private var scheduleRequest: EnforcementRequest? = null
    private var holder = Holder.NONE
    private var held: EnforcementRequest? = null
    private var clearPending = false

    /** The view the session owner uses; it sees only its own claim. */
    val manual: EnforcementPort = ManualView()

    /**
     * Restricts for the running occurrences through the standing grant only. It joins applied manual
     * restrictions without touching the helper, because a failed apply clears what the helper holds;
     * when that manual session ends first, its clear applies this request again.
     */
    override suspend fun claimSchedule(request: EnforcementRequest): EnforcementApplyReport {
        return mutex.withLock {
            val manual = manualRequest
            val joined = manual != null && delegate.status() == EnforcementOutcome.APPLIED
            val combined = manual?.unionWith(request)
            val report = when {
                !joined || combined == null -> send(request.withGrantOnly(), Holder.SCHEDULE)
                held?.sameTargets(combined) == true -> EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
                else -> replace(combined.withGrantOnly(), Holder.MANUAL)
            }
            if (joined && report.outcome != EnforcementOutcome.APPLIED) {
                // A failed apply cleared the helper; the manual owner sees that through its status.
                manualRequest = null
            }
            scheduleRequest = request.takeIf { report.outcome == EnforcementOutcome.APPLIED }
            if (scheduleRequest != null) {
                clearPending = false
            }
            report
        }
    }

    /**
     * Keeps a held claim current: when the paused items or the combined pause's latest end changed, the
     * helper gets the new request through the grant. The helper refuses a new configuration while it
     * holds one, so the change is a clear and an apply back to back.
     * A joined manual session keeps its later end. A claim not held yet is claimed.
     */
    override suspend fun updateSchedule(request: EnforcementRequest): EnforcementApplyReport {
        val claimed = mutex.withLock { scheduleRequest }
        if (claimed == null) {
            return claimSchedule(request)
        }
        return mutex.withLock {
            val effective = manualRequest?.let(request::unionWith) ?: request
            // Compared with what the helper enforces, whoever applied it, so a joined manual session's
            // later end or changed paused items are never hidden behind an unchanged schedule request.
            if (held?.sameEffect(effective) == true) {
                scheduleRequest = request
                EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
            } else {
                val report = replace(effective.withGrantOnly(), Holder.SCHEDULE)
                scheduleRequest = request.takeIf { report.outcome == EnforcementOutcome.APPLIED }
                if (report.outcome != EnforcementOutcome.APPLIED) {
                    // A failed apply cleared the helper; the manual owner sees that through its status.
                    manualRequest = null
                }
                report
            }
        }
    }

    /**
     * Lifts the schedule's claim; nothing happens when it holds none and no clear is pending. The helper
     * is cleared only when no manual claim remains, and a clear that failed is retried on the next call.
     * A manual session that outlasts the schedule gets its own request and end again when the helper
     * held the schedule's.
     */
    override suspend fun releaseSchedule(): EnforcementOutcome {
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

                holder == Holder.SCHEDULE || held?.sameTargets(manual) == false -> {
                    val report = send(manual.withGrantOnly(), Holder.MANUAL)
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
    override suspend fun scheduleStatus(): EnforcementOutcome {
        return mutex.withLock {
            if (scheduleRequest == null) {
                EnforcementOutcome.CLEARED
            } else {
                delegate.status().also { status ->
                    if (status == EnforcementOutcome.CLEARED) {
                        scheduleRequest = null
                        holder = Holder.NONE
                        held = null
                    }
                }
            }
        }
    }

    /** Drops the claim after repeated unanswered reads without clearing, so the next attempt's apply replaces the helper's state. */
    override suspend fun forgetSchedule() {
        mutex.withLock { scheduleRequest = null }
    }

    private suspend fun clearHelper(): EnforcementOutcome {
        val outcome = delegate.clear()
        clearPending = outcome != EnforcementOutcome.CLEARED
        if (!clearPending) {
            holder = Holder.NONE
            held = null
        }
        return outcome
    }

    /** Applies [request] for [applying] and records what the helper then holds; a failed apply leaves it holding nothing. */
    private suspend fun send(
        request: EnforcementRequest,
        applying: Holder,
    ): EnforcementApplyReport {
        return delegate.apply(request).also { report ->
            val applied = report.outcome == EnforcementOutcome.APPLIED
            holder = if (applied) applying else Holder.NONE
            held = request.takeIf { applied }
        }
    }

    /**
     * The helper takes a new configuration only when idle, so it is cleared and applied again within the
     * lock the caller holds: nothing reads the moment between the two.
     */
    private suspend fun replace(
        request: EnforcementRequest,
        applying: Holder,
    ): EnforcementApplyReport {
        delegate.clear()
        return send(request, applying)
    }

    private inner class ManualView : EnforcementPort {
        override val reapplyRequiresPrompt: Boolean
            get() {
                return delegate.reapplyRequiresPrompt
            }

        /**
         * A manual start or Resume while the schedule's restrictions hold joins them. The helper is left
         * alone unless the manual session ends later or pauses other items than it enforces; then it takes
         * the manual request through the grant, so its deadline is the combined pause's latest end.
         */
        override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
            return mutex.withLock {
                val schedule = scheduleRequest
                val joined = schedule != null && delegate.status() == EnforcementOutcome.APPLIED
                val combined = schedule?.let(request::unionWith) ?: request
                val current = held
                val report = when {
                    !joined -> {
                        send(request, Holder.MANUAL)
                    }

                    current != null && current.sameTargets(combined) && current.sessionEndEpochMillis >= request.sessionEndEpochMillis -> {
                        EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
                    }

                    else -> {
                        replace(combined.withGrantOnly(), Holder.MANUAL)
                    }
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

                    holder == Holder.MANUAL || held?.sameTargets(schedule) == false -> {
                        val report = send(schedule.withGrantOnly(), Holder.SCHEDULE)
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

private fun EnforcementRequest.sameTargets(other: EnforcementRequest): Boolean {
    return domains.toSet() == other.domains.toSet() && mappingIds.toSet() == other.mappingIds.toSet()
}

/**
 * Both claims' items until the later end, named by this request, so the helper's held session stays the one
 * this request's owner reconciles. Items are sorted so the same union always compares equal.
 */
private fun EnforcementRequest.unionWith(other: EnforcementRequest): EnforcementRequest {
    return EnforcementRequest(
        (domains + other.domains).distinct().sorted(),
        (mappingIds + other.mappingIds).distinct().sorted(),
        sessionId,
        sessionStartEpochMillis,
        maxOf(sessionEndEpochMillis, other.sessionEndEpochMillis),
        grantOnly,
    )
}

private fun EnforcementRequest.sameEffect(other: EnforcementRequest): Boolean {
    return sameTargets(other) && sessionEndEpochMillis == other.sessionEndEpochMillis
}

private fun EnforcementRequest.withEnd(end: Long): EnforcementRequest {
    return EnforcementRequest(domains, mappingIds, sessionId, sessionStartEpochMillis, end, grantOnly)
}
