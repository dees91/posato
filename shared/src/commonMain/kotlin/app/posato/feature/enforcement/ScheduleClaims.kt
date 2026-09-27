package app.posato.feature.enforcement

/**
 * What the schedule host needs from enforcement. On a Mac one helper is shared with manual sessions
 * through [PauseClaims]; on iPhone the schedule has a Managed Settings store of its own.
 */
internal interface ScheduleClaims {
    suspend fun claimSchedule(request: EnforcementRequest): EnforcementApplyReport

    /** Keeps a held claim on the current paused items and latest end without lifting it; claims one not held yet. */
    suspend fun updateSchedule(request: EnforcementRequest): EnforcementApplyReport

    suspend fun releaseSchedule(): EnforcementOutcome

    suspend fun scheduleStatus(): EnforcementOutcome

    suspend fun forgetSchedule()
}
