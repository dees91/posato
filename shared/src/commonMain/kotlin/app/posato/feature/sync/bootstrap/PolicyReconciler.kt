package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.sync.domain.SyncAuditOutcome
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.domain.ApplicationPolicyName
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import app.posato.feature.targets.domain.TargetPolicyValidationResult

internal sealed interface GroupOutcome {
    data object Settled : GroupOutcome

    data object Authored : GroupOutcome

    data class Failed(
        val status: SyncStatus
    ) : GroupOutcome
}

internal sealed interface ReconcileOutcome {
    data object AppliedClean : ReconcileOutcome

    data object RefusedWorkspaceFull : ReconcileOutcome

    data object RefusedLocalCap : ReconcileOutcome

    data object Corrupt : ReconcileOutcome

    data object Conflict : ReconcileOutcome

    data object StorageFailure : ReconcileOutcome
}

internal class PolicyReconciler(
    private val policies: LocalPolicySyncStore,
) {
    suspend fun seedLocalExtras(workspaceId: ByteArray): LocalPolicyResult<Unit> {
        return policies.withWriteGate {
            val local = when (val read = policies.read()) {
                is LocalPolicyResult.Success -> read.value.policy
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val pending = when (val read = policies.readIntents()) {
                is LocalPolicyResult.Success -> read.value
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val pendingDomains = pending.mapNotNull { row ->
                when (val intent = row.intent) {
                    is StoredPolicyIntent.PresentDomain -> intent.domain
                    is StoredPolicyIntent.RemoveDomain -> intent.domain
                    is StoredPolicyIntent.PresentApplicationPolicy -> null
                }
            }.toSet()
            val seeds = local.domains
                .filter { domain -> domain !in pendingDomains }
                .map { domain -> StoredPolicyIntent.PresentDomain(domain) }
            val pendingName = pending.mapNotNull { row -> (row.intent as? StoredPolicyIntent.PresentApplicationPolicy)?.name }.lastOrNull()
            val groupSeed = local.applicationPolicyName
                ?.takeIf { name -> pendingName == null }
                ?.let { name -> StoredPolicyIntent.PresentApplicationPolicy(name) }
            val intents = seeds + listOfNotNull(groupSeed)
            if (intents.isEmpty()) {
                LocalPolicyResult.Success(Unit)
            } else {
                policies.recordIntents(PolicySyncWrite(workspaceId, intents))
            }
        }
    }

    suspend fun decideGroup(
        writer: SyncWriter,
        projection: SyncProjection,
    ): GroupOutcome {
        return when (val read = policies.readIntents()) {
            is LocalPolicyResult.Failure -> GroupOutcome.Failed(read.reason.toSyncStatus())
            is LocalPolicyResult.Success -> resolveGroup(writer, projection, groupNames(read.value))
        }
    }

    private suspend fun resolveGroup(
        writer: SyncWriter,
        projection: SyncProjection,
        names: List<Pair<Long, ApplicationPolicyName>>,
    ): GroupOutcome {
        if (names.isEmpty()) {
            return GroupOutcome.Settled
        }
        if (projection.applicationPolicyName != null) {
            return if (discardGroupRows(policies, names)) GroupOutcome.Settled else GroupOutcome.Failed(SyncStatus.ACTION_REQUIRED)
        }
        return authorGroupName(writer, names)
    }

    private suspend fun authorGroupName(
        writer: SyncWriter,
        names: List<Pair<Long, ApplicationPolicyName>>,
    ): GroupOutcome {
        if (writer.mutate(LocalSyncMutation.PresentApplicationPolicy(names.last().second)) is LocalMutationResult.Failure) {
            return GroupOutcome.Failed(SyncStatus.ACTION_REQUIRED)
        }
        return if (discardGroupRows(policies, names)) GroupOutcome.Authored else GroupOutcome.Failed(SyncStatus.ACTION_REQUIRED)
    }

    suspend fun apply(
        projection: SyncProjection,
        base: PolicySyncBase?,
    ): ReconcileOutcome {
        return policies.withWriteGate {
            when (val read = policies.read()) {
                is LocalPolicyResult.Failure -> {
                    read.reason.toReconcileOutcome()
                }

                is LocalPolicyResult.Success -> {
                    when (val pending = policies.readIntents()) {
                        is LocalPolicyResult.Failure -> pending.reason.toReconcileOutcome()
                        is LocalPolicyResult.Success -> applyPending(read.value, projection, base, pending.value)
                    }
                }
            }
        }
    }

    private suspend fun applyPending(
        local: LocalTargetPolicyState,
        projection: SyncProjection,
        base: PolicySyncBase?,
        pending: List<SequencedPolicyIntent>,
    ): ReconcileOutcome {
        return applyMerge(local, projection, base, pending, retrying = false)
    }

    private suspend fun retryApply(
        staleRevision: Long,
        projection: SyncProjection,
        base: PolicySyncBase?,
    ): ReconcileOutcome {
        return when (val local = policies.read()) {
            is LocalPolicyResult.Failure -> {
                local.reason.toReconcileOutcome()
            }

            is LocalPolicyResult.Success -> {
                if (local.value.revision == staleRevision) {
                    ReconcileOutcome.StorageFailure
                } else {
                    retryMerge(local.value, projection, base)
                }
            }
        }
    }

    private suspend fun retryMerge(
        local: LocalTargetPolicyState,
        projection: SyncProjection,
        base: PolicySyncBase?,
    ): ReconcileOutcome {
        return when (val pending = policies.readIntents()) {
            is LocalPolicyResult.Failure -> pending.reason.toReconcileOutcome()
            is LocalPolicyResult.Success -> writeRetry(local, projection, base, pending.value)
        }
    }

    private suspend fun writeRetry(
        local: LocalTargetPolicyState,
        projection: SyncProjection,
        base: PolicySyncBase?,
        pending: List<SequencedPolicyIntent>,
    ): ReconcileOutcome {
        return applyMerge(local, projection, base, pending, retrying = true)
    }

    private suspend fun applyMerge(
        local: LocalTargetPolicyState,
        projection: SyncProjection,
        base: PolicySyncBase?,
        pending: List<SequencedPolicyIntent>,
        retrying: Boolean,
    ): ReconcileOutcome {
        val projected = projectionPolicy(projection)
        val merged = mergePolicy(local.policy, base?.policy, projected, pending.map { row -> row.intent })
        return when {
            projection.isWorkspaceFull() -> {
                ReconcileOutcome.RefusedWorkspaceFull
            }

            merged.domains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT -> {
                ReconcileOutcome.RefusedLocalCap
            }

            projected.domains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT -> {
                ReconcileOutcome.RefusedLocalCap
            }

            else -> {
                writeMerge(local, projection, base, projected, merged, retrying)
            }
        }
    }

    private suspend fun writeMerge(
        local: LocalTargetPolicyState,
        projection: SyncProjection,
        base: PolicySyncBase?,
        projected: UnboundedPolicy,
        merged: UnboundedPolicy,
        retrying: Boolean,
    ): ReconcileOutcome {
        val projectedPolicy = projected.toTargetPolicy()
        val mergedPolicy = merged.toTargetPolicy()
        if (projectedPolicy == null || mergedPolicy == null) {
            return ReconcileOutcome.Corrupt
        }
        if (mergedPolicy == local.policy && base?.policy == projectedPolicy) {
            return projection.toReconcileOutcome()
        }
        return when (val replaced = policies.replaceWithBase(local.revision, mergedPolicy, projectedPolicy)) {
            is LocalPolicyResult.Success -> {
                projection.toReconcileOutcome()
            }

            is LocalPolicyResult.Failure -> {
                if (replaced.reason == LocalPolicyFailure.REVISION_CONFLICT && !retrying) {
                    retryApply(local.revision, projection, base)
                } else if (replaced.reason == LocalPolicyFailure.REVISION_CONFLICT) {
                    ReconcileOutcome.Conflict
                } else {
                    ReconcileOutcome.StorageFailure
                }
            }
        }
    }
}

private fun groupNames(rows: List<SequencedPolicyIntent>): List<Pair<Long, ApplicationPolicyName>> {
    return rows.mapNotNull { row ->
        (row.intent as? StoredPolicyIntent.PresentApplicationPolicy)?.let { intent -> row.sequence to intent.name }
    }
}

private suspend fun discardGroupRows(
    policies: LocalPolicySyncStore,
    names: List<Pair<Long, ApplicationPolicyName>>,
): Boolean {
    names.forEach { (sequence, _) ->
        if (policies.deleteIntent(sequence) is LocalPolicyResult.Failure) {
            return false
        }
    }
    return true
}

private data class UnboundedPolicy(
    val domains: Set<ExactDomain>,
    val name: ApplicationPolicyName?,
) {
    fun toTargetPolicy(): TargetPolicy? {
        val validation = TargetPolicy.fromStoredValues(
            domains.map { domain -> domain.canonicalValue },
            name?.canonicalValue,
        )
        return (validation as? TargetPolicyValidationResult.Success)?.policy
    }
}

private fun mergePolicy(
    local: TargetPolicy,
    base: TargetPolicy?,
    projected: UnboundedPolicy,
    pending: List<StoredPolicyIntent>,
): UnboundedPolicy {
    val presents = pending.mapNotNull { intent -> (intent as? StoredPolicyIntent.PresentDomain)?.domain }.toSet()
    val removals = pending.mapNotNull { intent -> (intent as? StoredPolicyIntent.RemoveDomain)?.domain }.toSet()
    val domains = if (base == null) {
        (projected.domains + local.domains.toSet()).toMutableSet()
    } else {
        mergeEstablished(local.domains.toSet(), base.domains.toSet(), projected.domains, presents, removals)
    }
    pending.forEach { intent ->
        when (intent) {
            is StoredPolicyIntent.PresentDomain -> domains.add(intent.domain)
            is StoredPolicyIntent.RemoveDomain -> domains.remove(intent.domain)
            is StoredPolicyIntent.PresentApplicationPolicy -> Unit
        }
    }
    val remoteName = if (base == null) {
        projected.name ?: local.applicationPolicyName
    } else if (projected.name == base.applicationPolicyName) {
        local.applicationPolicyName
    } else {
        projected.name
    }
    val pendingName = pending.mapNotNull { intent ->
        (intent as? StoredPolicyIntent.PresentApplicationPolicy)?.name
    }.lastOrNull()
    return UnboundedPolicy(domains, pendingName ?: remoteName)
}

private fun mergeEstablished(
    local: Set<ExactDomain>,
    base: Set<ExactDomain>,
    projected: Set<ExactDomain>,
    presents: Set<ExactDomain>,
    removals: Set<ExactDomain>,
): MutableSet<ExactDomain> {
    val gained = projected - base
    val lost = base - projected
    val provisional = (local + gained) - lost
    val intentDomains = presents + removals
    val kept = provisional.filter { domain -> domain in projected || domain in intentDomains }.toMutableSet()
    kept += projected.filter { domain -> domain !in local && domain !in removals }
    return kept
}

private fun projectionPolicy(projection: SyncProjection): UnboundedPolicy {
    return UnboundedPolicy(projection.domains.toSet(), projection.applicationPolicyName)
}

private fun SyncProjection.isWorkspaceFull(): Boolean {
    return audit.any { entry -> entry.outcome == SyncAuditOutcome.DOMAIN_CAPACITY } &&
        domains.size >= SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS
}

private fun SyncProjection.toReconcileOutcome(): ReconcileOutcome {
    return if (isWorkspaceFull()) ReconcileOutcome.RefusedWorkspaceFull else ReconcileOutcome.AppliedClean
}

private fun LocalPolicyFailure.toReconcileOutcome(): ReconcileOutcome {
    return when (this) {
        LocalPolicyFailure.CORRUPTION -> ReconcileOutcome.Corrupt
        else -> ReconcileOutcome.StorageFailure
    }
}

internal fun LocalPolicyFailure.toSyncStatus(): SyncStatus {
    return when (this) {
        LocalPolicyFailure.CORRUPTION -> SyncStatus.ACTION_REQUIRED
        else -> SyncStatus.RETRYABLE
    }
}
