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
) {
    override fun toString(): String {
        return "SynchronizedSessionStart(redacted)"
    }
}

internal data class SyncProjection(
    val domains: List<ExactDomain>,
    val applicationPolicyName: ApplicationPolicyName?,
    val eligibleSessionStarts: List<SynchronizedSessionStart>,
    val conflictedSessionIds: Set<SessionId>,
    val audit: List<SyncAuditEntry>,
) {
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
    private val domains = linkedSetOf<ExactDomain>()
    private var applicationPolicyName: ApplicationPolicyName? = null
    private val sessionStarts = mutableListOf<Pair<SyncOperation, SyncOperationPayload.SessionStart>>()
    private val endedSessionIds = mutableSetOf<SessionId>()

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
                applyDomainPresent(payload.domain)
            }

            is SyncOperationPayload.DomainAbsent -> {
                outcome(domains.remove(payload.domain))
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
        }
    }

    fun build(): SyncProjection {
        val startsBySession = sessionStarts.groupBy { (_, payload) -> payload.sessionId }
        val conflicted = startsBySession.filterValues { starts -> starts.size > 1 }.keys
        val eligible = startsBySession.flatMap { (sessionId, starts) -> eligibleStarts(sessionId, starts, conflicted) }
        val auditEntries = uniqueOperations.sortedBy(SyncOperation::order).map { operation ->
            SyncAuditEntry(operation.operationId, checkNotNull(audit[operation.operationId]))
        }
        return SyncProjection(
            domains.sortedBy(ExactDomain::canonicalValue),
            applicationPolicyName,
            eligible.sortedBy(SynchronizedSessionStart::order),
            conflicted,
            auditEntries,
        )
    }

    private fun applyDomainPresent(domain: ExactDomain): SyncAuditOutcome = when {
        domain in domains -> {
            SyncAuditOutcome.NO_OP
        }

        domains.size >= SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS -> {
            SyncAuditOutcome.DOMAIN_CAPACITY
        }

        else -> {
            domains += domain
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
            ),
        )
    }

    private fun outcome(changed: Boolean): SyncAuditOutcome = if (changed) SyncAuditOutcome.APPLIED else SyncAuditOutcome.NO_OP
}
