package app.posato.feature.sync.domain

import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain

internal enum class SyncAuditOutcome {
    AUTHOR_REGISTERED,
    APPLIED,
    NO_OP,
    DOMAIN_CAPACITY,
    SEQUENCE_GAP,
    SESSION_CONFLICT,
    SCHEDULE_CAPACITY,
    SET_CAPACITY,
}

internal enum class PauseSetStatus {
    LIVE,
    REMOVED,
    REFUSED,
    UNKNOWN,
}

/** A live set: its synchronized name, absent for the first set until someone names it, and its domains. */
internal data class SynchronizedPauseSet(
    val setId: PauseSetId,
    val name: String?,
    val domains: List<ExactDomain>,
) {
    override fun toString(): String {
        return "SynchronizedPauseSet(redacted)"
    }
}

/** The effective schedule for one identifier: its greatest total-order put. */
internal data class SynchronizedSchedule(
    val scheduleId: ScheduleSyncId,
    val name: String,
    val weekdays: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean,
    val setId: PauseSetId = PauseSetId.FIRST,
) {
    override fun toString(): String {
        return "SynchronizedSchedule(redacted)"
    }
}

internal data class SyncAuditEntry(
    val operationId: BundleId,
    val outcome: SyncAuditOutcome,
)

internal data class SynchronizedSessionStart(
    val operationId: BundleId,
    val sessionId: SessionId,
    val startEpochMillis: Long,
    val mandatoryEndEpochMillis: Long,
    val order: OperationOrder,
    val isEnded: Boolean,
    val setId: PauseSetId = PauseSetId.FIRST,
) {
    override fun toString(): String {
        return "SynchronizedSessionStart(redacted)"
    }
}

internal data class SyncProjection(
    val applicationPolicyName: ApplicationPolicyName?,
    val eligibleSessionStarts: List<SynchronizedSessionStart>,
    val conflictedSessionIds: Set<SessionId>,
    val audit: List<SyncAuditEntry>,
    val schedules: List<SynchronizedSchedule> = emptyList(),
    val removedScheduleIds: Set<ScheduleSyncId> = emptySet(),
    val scheduleSkips: Set<ScheduleOccurrenceRef> = emptySet(),
    val scheduleEnds: Set<ScheduleOccurrenceRef> = emptySet(),
    val refusedSchedules: List<SynchronizedSchedule> = emptyList(),
    val pauseSets: List<SynchronizedPauseSet> = emptyList(),
    val removedPauseSetIds: Set<PauseSetId> = emptySet(),
    val refusedPauseSetIds: Set<PauseSetId> = emptySet(),
    val defaultPauseSetId: PauseSetId? = null,
    val pauseSetsEnabled: Boolean = false,
) {
    fun pauseSetStatus(setId: PauseSetId): PauseSetStatus {
        return when {
            pauseSets.any { set -> set.setId == setId } -> PauseSetStatus.LIVE
            setId in removedPauseSetIds -> PauseSetStatus.REMOVED
            setId in refusedPauseSetIds -> PauseSetStatus.REFUSED
            else -> PauseSetStatus.UNKNOWN
        }
    }

    /** A set that is not live resolves to no domains. */
    fun pauseSetDomains(setId: PauseSetId): List<ExactDomain> {
        return pauseSets.firstOrNull { set -> set.setId == setId }?.domains.orEmpty()
    }

    override fun toString(): String {
        return "SyncProjection(redacted)"
    }
}

internal sealed interface EffectiveSession {
    data object Inactive : EffectiveSession

    data class Active(
        val sessionId: SessionId,
        val mandatoryEndEpochMillis: Long,
    ) : EffectiveSession {
        override fun toString(): String {
            return "EffectiveSession.Active(redacted)"
        }
    }
}

internal enum class SessionConclusionKind {
    ENDED,
    EXPIRED,
}

internal sealed interface SessionCandidate {
    data object None : SessionCandidate

    data class Current(
        val start: SynchronizedSessionStart,
    ) : SessionCandidate {
        override fun toString(): String {
            return "SessionCandidate.Current(redacted)"
        }
    }

    data class Future(
        val start: SynchronizedSessionStart,
    ) : SessionCandidate {
        override fun toString(): String {
            return "SessionCandidate.Future(redacted)"
        }
    }

    data class Concluded(
        val sessionId: SessionId,
        val kind: SessionConclusionKind,
    ) : SessionCandidate
}

internal object SyncReducer {
    fun reduce(operations: Collection<SyncOperation>): SyncProjection {
        val uniqueOperations = operations.distinctBy(SyncOperation::operationId)
        val applicable = applicableOperations(uniqueOperations)
        val orderedBusinessOperations = applicable
            .filterNot { operation -> operation.payload == SyncOperationPayload.AuthorRegister }
            .sortedBy(SyncOperation::order)
        val accumulator = ProjectionAccumulator(uniqueOperations, applicable)
        orderedBusinessOperations.forEach(accumulator::apply)

        return accumulator.build()
    }

    fun evaluateSession(
        projection: SyncProjection,
        evaluationEpochMillis: Long,
        terminalExpiryFacts: Set<SessionId>,
    ): EffectiveSession {
        return when (val candidate = describeSession(projection, evaluationEpochMillis, terminalExpiryFacts)) {
            is SessionCandidate.Current -> {
                EffectiveSession.Active(candidate.start.sessionId, candidate.start.mandatoryEndEpochMillis)
            }

            else -> {
                EffectiveSession.Inactive
            }
        }
    }

    fun describeSession(
        projection: SyncProjection,
        evaluationEpochMillis: Long,
        terminalExpiryFacts: Set<SessionId>,
    ): SessionCandidate {
        val current = projection.eligibleSessionStarts
            .asSequence()
            .filter { start -> start.startEpochMillis <= evaluationEpochMillis }
            .maxByOrNull(SynchronizedSessionStart::order)
        if (current != null) {
            val live = !current.isEnded &&
                current.sessionId !in terminalExpiryFacts &&
                evaluationEpochMillis < current.mandatoryEndEpochMillis
            val kind = if (current.isEnded) SessionConclusionKind.ENDED else SessionConclusionKind.EXPIRED
            return if (live) SessionCandidate.Current(current) else SessionCandidate.Concluded(current.sessionId, kind)
        }
        val future = projection.eligibleSessionStarts
            .asSequence()
            .filter { start -> start.startEpochMillis > evaluationEpochMillis }
            .maxByOrNull(SynchronizedSessionStart::order)
        return if (future == null) SessionCandidate.None else SessionCandidate.Future(future)
    }

    private fun applicableOperations(operations: Collection<SyncOperation>): List<SyncOperation> {
        return operations
            .groupBy(SyncOperation::authorId)
            .values
            .flatMap(::contiguousAuthorOperations)
    }

    private fun contiguousAuthorOperations(authorOperations: List<SyncOperation>): List<SyncOperation> {
        val bySequence = authorOperations.sortedWith(
            compareBy<SyncOperation>(SyncOperation::authorSequence).thenBy { operation -> operation.operationId.value },
        )
        val registration = bySequence.firstOrNull()
        if (registration?.authorSequence != 1L || registration.payload != SyncOperationPayload.AuthorRegister) {
            return emptyList()
        }
        val result = mutableListOf<SyncOperation>()
        var expectedSequence = 1L
        var index = 0
        while (index < bySequence.size && bySequence[index].authorSequence == expectedSequence) {
            val operation = bySequence[index]
            result += operation
            if (expectedSequence < Long.MAX_VALUE) {
                expectedSequence += 1
                index += 1
            } else {
                index = bySequence.size
            }
        }

        return result
    }
}

private class ProjectionAccumulator(
    private val uniqueOperations: List<SyncOperation>,
    applicable: List<SyncOperation>,
) {
    private val audit = uniqueOperations.associateTo(linkedMapOf()) { it.operationId to SyncAuditOutcome.SEQUENCE_GAP }
    private val sets = PauseSetLiveness(applicable)
    private val setDomains = sets.liveIds.associateWith { linkedSetOf<ExactDomain>() }
    private val domainHolders = mutableMapOf<ExactDomain, Int>()
    private val appliedSetRemoves = mutableSetOf<PauseSetId>()
    private var chosenDefault: PauseSetId? = null
    private var pauseSetsEnabled = false
    private var applicationPolicyName: ApplicationPolicyName? = null
    private val sessionStarts = mutableListOf<Pair<SyncOperation, SyncOperationPayload.SessionStart>>()
    private val endedSessionIds = mutableSetOf<SessionId>()

    // A remove wins whatever the order, so every applicable (gap-free) remove is known before any put.
    private val removedScheduleIds = applicable.mapNotNullTo(mutableSetOf()) { operation ->
        (operation.payload as? SyncOperationPayload.ScheduleRemove)?.scheduleId
    }
    private val appliedRemoves = mutableSetOf<ScheduleSyncId>()
    private val schedules = mutableMapOf<ScheduleSyncId, SyncOperationPayload.SchedulePut>()
    private val refusedSchedules = mutableMapOf<ScheduleSyncId, SyncOperationPayload.SchedulePut>()
    private val scheduleSkips = mutableSetOf<ScheduleOccurrenceRef>()
    private val scheduleEnds = mutableSetOf<ScheduleOccurrenceRef>()

    init {
        applicable
            .filter { it.payload == SyncOperationPayload.AuthorRegister }
            .forEach { audit[it.operationId] = SyncAuditOutcome.AUTHOR_REGISTERED }
    }

    fun apply(operation: SyncOperation) {
        audit[operation.operationId] = when (val payload = operation.payload) {
            SyncOperationPayload.AuthorRegister -> {
                SyncAuditOutcome.AUTHOR_REGISTERED
            }

            is SyncOperationPayload.DomainPresent -> {
                applyDomainPresent(payload.setId ?: PauseSetId.FIRST, payload.domain)
            }

            is SyncOperationPayload.DomainAbsent -> {
                applyDomainAbsent(payload.setId ?: PauseSetId.FIRST, payload.domain)
            }

            is SyncOperationPayload.ApplicationPolicyPresent -> {
                applicationPolicyName = payload.name
                SyncAuditOutcome.APPLIED
            }

            SyncOperationPayload.ApplicationPolicyAbsent -> {
                clearApplicationPolicy()
            }

            is SyncOperationPayload.SessionStart -> {
                sessionStarts += operation to payload
                SyncAuditOutcome.APPLIED
            }

            is SyncOperationPayload.SessionEnd -> {
                outcome(endedSessionIds.add(payload.sessionId))
            }

            is SyncOperationPayload.SchedulePut,
            is SyncOperationPayload.ScheduleRemove,
            is SyncOperationPayload.ScheduleSkip,
            is SyncOperationPayload.ScheduleOccurrenceEnd,
            is SyncOperationPayload.OptionalExtension -> {
                applySchedule(payload)
            }

            is SyncOperationPayload.PauseSetPut,
            is SyncOperationPayload.PauseSetRemove,
            is SyncOperationPayload.PauseSetDefault,
            SyncOperationPayload.PauseSetsEnabled -> {
                applyPauseSet(operation)
            }
        }
    }

    private fun applyPauseSet(operation: SyncOperation): SyncAuditOutcome = when (val payload = operation.payload) {
        is SyncOperationPayload.PauseSetRemove -> {
            outcome(appliedSetRemoves.add(payload.setId))
        }

        is SyncOperationPayload.PauseSetDefault -> {
            chosenDefault = payload.setId
            SyncAuditOutcome.APPLIED
        }

        SyncOperationPayload.PauseSetsEnabled -> {
            outcome(!pauseSetsEnabled).also { pauseSetsEnabled = true }
        }

        else -> {
            sets.putOutcome(operation.operationId)
        }
    }

    fun build(): SyncProjection {
        val startsBySession = sessionStarts.groupBy { (_, payload) -> payload.sessionId }
        val conflicted = startsBySession.filterValues { starts -> starts.size > 1 }.keys
        val eligible = startsBySession.flatMap { (sessionId, starts) -> eligibleStarts(sessionId, starts, conflicted) }
        val auditEntries = uniqueOperations.sortedBy(SyncOperation::order).map { operation ->
            SyncAuditEntry(operation.operationId, checkNotNull(audit[operation.operationId]))
        }
        val pauseSets = sets.liveIds.sortedBy(PauseSetId::value).map { setId ->
            SynchronizedPauseSet(setId, sets.nameOf(setId), setDomains.getValue(setId).sortedBy(ExactDomain::canonicalValue))
        }
        return SyncProjection(
            applicationPolicyName,
            eligible.sortedBy(SynchronizedSessionStart::order),
            conflicted,
            auditEntries,
            schedules = schedules.values.sortedBy { put -> put.scheduleId.value }.map(SyncOperationPayload.SchedulePut::toSynchronized),
            removedScheduleIds = removedScheduleIds.toSet(),
            scheduleSkips = scheduleSkips.toSet(),
            scheduleEnds = scheduleEnds.toSet(),
            refusedSchedules = refusedSchedules.values.sortedBy { put -> put.scheduleId.value }.map(SyncOperationPayload.SchedulePut::toSynchronized),
            pauseSets = pauseSets,
            removedPauseSetIds = sets.removedIds,
            refusedPauseSetIds = sets.refusedIds.toSet(),
            defaultPauseSetId = sets.defaultFor(chosenDefault),
            pauseSetsEnabled = pauseSetsEnabled,
        )
    }

    /** Operations for a set that is not live are left out; the cap counts unique domains across live sets. */
    private fun applyDomainPresent(
        setId: PauseSetId,
        domain: ExactDomain,
    ): SyncAuditOutcome {
        val domains = setDomains[setId]
        return when {
            domains == null || domain in domains -> {
                SyncAuditOutcome.NO_OP
            }

            domain !in domainHolders && domainHolders.size >= SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS -> {
                SyncAuditOutcome.DOMAIN_CAPACITY
            }

            else -> {
                domains += domain
                domainHolders[domain] = domainHolders.getOrElse(domain) { 0 } + 1
                SyncAuditOutcome.APPLIED
            }
        }
    }

    private fun applyDomainAbsent(
        setId: PauseSetId,
        domain: ExactDomain,
    ): SyncAuditOutcome {
        val removed = setDomains[setId]?.remove(domain) == true
        if (removed) {
            val holders = domainHolders.getValue(domain) - 1
            if (holders == 0) domainHolders.remove(domain) else domainHolders[domain] = holders
        }
        return outcome(removed)
    }

    private fun applySchedule(payload: SyncOperationPayload): SyncAuditOutcome = when (payload) {
        is SyncOperationPayload.SchedulePut -> {
            applySchedulePut(payload)
        }

        is SyncOperationPayload.ScheduleRemove -> {
            outcome(appliedRemoves.add(payload.scheduleId))
        }

        is SyncOperationPayload.ScheduleSkip -> {
            outcome(payload.occurrence.scheduleId !in removedScheduleIds && scheduleSkips.add(payload.occurrence))
        }

        is SyncOperationPayload.ScheduleOccurrenceEnd -> {
            outcome(payload.occurrence.scheduleId !in removedScheduleIds && scheduleEnds.add(payload.occurrence))
        }

        // Optional kinds are retained and advance their author's sequence, but never change state.
        else -> {
            SyncAuditOutcome.NO_OP
        }
    }

    /** Operations arrive in total order, so a put for a live identifier is always the newer one. */
    private fun applySchedulePut(put: SyncOperationPayload.SchedulePut): SyncAuditOutcome = when {
        put.scheduleId in removedScheduleIds -> {
            SyncAuditOutcome.NO_OP
        }

        put.scheduleId in schedules -> {
            schedules[put.scheduleId] = put
            SyncAuditOutcome.APPLIED
        }

        schedules.size >= SyncFormatLimits.MAX_SYNCHRONIZED_SCHEDULES -> {
            // Kept, never evicting: its author sees it as refused, and it becomes live once a slot frees.
            refusedSchedules[put.scheduleId] = put
            SyncAuditOutcome.SCHEDULE_CAPACITY
        }

        else -> {
            schedules[put.scheduleId] = put
            SyncAuditOutcome.APPLIED
        }
    }

    private fun clearApplicationPolicy(): SyncAuditOutcome {
        val changed = applicationPolicyName != null
        applicationPolicyName = null
        return outcome(changed)
    }

    private fun eligibleStarts(
        sessionId: SessionId,
        starts: List<Pair<SyncOperation, SyncOperationPayload.SessionStart>>,
        conflicted: Set<SessionId>,
    ): List<SynchronizedSessionStart> {
        if (sessionId in conflicted) {
            starts.forEach { (operation) -> audit[operation.operationId] = SyncAuditOutcome.SESSION_CONFLICT }
            return emptyList()
        }
        val (operation, payload) = starts.single()
        return listOf(
            SynchronizedSessionStart(
                operation.operationId,
                sessionId,
                payload.startEpochMillis,
                payload.mandatoryEndEpochMillis,
                operation.order(),
                sessionId in endedSessionIds,
                payload.setId ?: PauseSetId.FIRST,
            ),
        )
    }

    private fun outcome(changed: Boolean): SyncAuditOutcome = if (changed) SyncAuditOutcome.APPLIED else SyncAuditOutcome.NO_OP
}

private fun SyncOperationPayload.SchedulePut.toSynchronized(): SynchronizedSchedule {
    return SynchronizedSchedule(scheduleId, name, weekdays, startMinute, endMinute, enabled, setId ?: PauseSetId.FIRST)
}

/**
 * Set liveness from the complete applicable operation set, decided before any domain is reduced. A remove
 * wins whatever the order, the first set holds a slot unless removed, and a put for a new set at the cap is
 * refused without eviction. Removed sets never take a slot, so a set refused here stays refused.
 */
private class PauseSetLiveness(
    applicable: List<SyncOperation>,
) {
    val removedIds: Set<PauseSetId> = applicable.mapNotNullTo(mutableSetOf()) { operation ->
        (operation.payload as? SyncOperationPayload.PauseSetRemove)?.setId
    }
    val refusedIds = mutableSetOf<PauseSetId>()
    private val names = linkedMapOf<PauseSetId, String?>()
    private val firstPuts = mutableMapOf<PauseSetId, OperationOrder>()
    private val putOutcomes = mutableMapOf<BundleId, SyncAuditOutcome>()

    val liveIds: Set<PauseSetId>
        get() {
            return names.keys
        }

    init {
        if (PauseSetId.FIRST !in removedIds) {
            names[PauseSetId.FIRST] = null
        }
        applicable
            .filter { operation -> operation.payload is SyncOperationPayload.PauseSetPut }
            .sortedBy(SyncOperation::order)
            .forEach { operation -> putOutcomes[operation.operationId] = applyPut(operation) }
    }

    fun putOutcome(operationId: BundleId): SyncAuditOutcome {
        return putOutcomes.getValue(operationId)
    }

    fun nameOf(setId: PauseSetId): String? {
        return names[setId]
    }

    /** The chosen default when it is live, otherwise the first set, otherwise the live set whose earliest put is first. */
    fun defaultFor(chosen: PauseSetId?): PauseSetId? {
        return when {
            chosen != null && chosen in names -> chosen
            PauseSetId.FIRST in names -> PauseSetId.FIRST
            else -> firstPuts.filterKeys { setId -> setId in names }.minByOrNull { (_, order) -> order }?.key
        }
    }

    private fun applyPut(operation: SyncOperation): SyncAuditOutcome {
        val put = operation.payload as SyncOperationPayload.PauseSetPut
        return when {
            put.setId in removedIds -> {
                SyncAuditOutcome.NO_OP
            }

            put.setId in names -> {
                names[put.setId] = put.name
                SyncAuditOutcome.APPLIED
            }

            names.size >= SyncFormatLimits.MAX_PAUSE_SETS -> {
                refusedIds += put.setId
                SyncAuditOutcome.SET_CAPACITY
            }

            else -> {
                names[put.setId] = put.name
                firstPuts[put.setId] = operation.order()
                SyncAuditOutcome.APPLIED
            }
        }
    }
}
