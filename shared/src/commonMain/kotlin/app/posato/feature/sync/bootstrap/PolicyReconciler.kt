package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.PauseSetId
import app.posato.feature.sync.domain.PauseSetStatus
import app.posato.feature.sync.domain.SyncAuditOutcome
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncProjection
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.LocalTargetPolicyState
import app.posato.feature.targets.data.setIdOf
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
import app.posato.feature.targets.domain.PolicySyncBase
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent

internal sealed interface ReconcileOutcome {
    data object AppliedClean : ReconcileOutcome

    data object RefusedWorkspaceFull : ReconcileOutcome

    data object RefusedLocalCap : ReconcileOutcome

    /** More than ten live sets would result on this device; slice 4 gives it its own copy. */
    data object RefusedSetCapacity : ReconcileOutcome

    data object Corrupt : ReconcileOutcome

    data object Conflict : ReconcileOutcome

    data object StorageFailure : ReconcileOutcome
}

internal class PolicyReconciler(
    private val policies: LocalPolicySyncStore,
    /** Told the sets that survive when an applied change removed some, so app choices can follow at once. */
    private val onSetsRemoved: suspend (Set<PauseSetId>) -> Unit = {},
) {
    /**
     * At first link, queues what this device holds and the workspace may not: every website of a set the
     * workspace has not removed, and a name only for sets other than the first. The first set's name and the
     * default are never published here, so a late link cannot overwrite a choice made on another device.
     */
    suspend fun seedLocalExtras(
        workspaceId: ByteArray,
        projection: SyncProjection,
    ): LocalPolicyResult<Unit> {
        return policies.withWriteGate {
            val local = when (val read = policies.read()) {
                is LocalPolicyResult.Success -> read.value.sets
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val pending = when (val read = policies.readIntents()) {
                is LocalPolicyResult.Success -> read.value.map(SequencedPolicyIntent::intent)
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val seeds = local.sets
                .filter { set -> projection.pauseSetStatus(set.id) != PauseSetStatus.REMOVED }
                .flatMap { set -> seedsFor(set, projection, pending) }
            if (seeds.isEmpty()) {
                LocalPolicyResult.Success(Unit)
            } else {
                policies.recordIntents(PolicySyncWrite(workspaceId, seeds))
            }
        }
    }

    suspend fun apply(
        projection: SyncProjection,
        base: PolicySyncBase?,
    ): ReconcileOutcome {
        return policies.withWriteGate {
            applyOnce(projection, base, retrying = false)
        }
    }

    private suspend fun applyOnce(
        projection: SyncProjection,
        base: PolicySyncBase?,
        retrying: Boolean,
    ): ReconcileOutcome {
        val local = when (val read = policies.read()) {
            is LocalPolicyResult.Success -> read.value
            is LocalPolicyResult.Failure -> return read.reason.toReconcileOutcome()
        }
        val pending = when (val read = policies.readIntents()) {
            is LocalPolicyResult.Success -> read.value.map(SequencedPolicyIntent::intent)
            is LocalPolicyResult.Failure -> return read.reason.toReconcileOutcome()
        }
        val merged = mergeSets(local.sets, base, projection, pending)
        val projectedBase = projection.pauseSets.associate { set -> set.setId to set.domains }
        return when {
            projection.isWorkspaceFull() -> ReconcileOutcome.RefusedWorkspaceFull

            merged.flatMapTo(
                mutableSetOf(),
                LocalPauseSet::domains,
            ).size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT -> ReconcileOutcome.RefusedLocalCap

            projection.uniqueDomains().size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT -> ReconcileOutcome.RefusedLocalCap

            merged.count { set -> !set.refused } > SyncFormatLimits.MAX_PAUSE_SETS -> ReconcileOutcome.RefusedSetCapacity

            else -> write(local, projection, base, PauseSets.of(merged, chosenDefault(pending, projection)), projectedBase, retrying)
        }
    }

    private suspend fun write(
        local: LocalTargetPolicyState,
        projection: SyncProjection,
        base: PolicySyncBase?,
        merged: PauseSets?,
        projectedBase: Map<PauseSetId, List<ExactDomain>>,
        retrying: Boolean,
    ): ReconcileOutcome {
        if (merged == null) {
            return ReconcileOutcome.Corrupt
        }
        if (merged == local.sets && base?.domains == projectedBase) {
            return projection.toReconcileOutcome()
        }
        return when (val replaced = policies.replaceWithBase(local.revision, merged, projectedBase)) {
            is LocalPolicyResult.Success -> {
                val surviving = merged.sets.mapTo(mutableSetOf()) { set -> set.id }
                if (local.sets.sets.any { set -> set.id !in surviving }) {
                    onSetsRemoved(surviving)
                }
                projection.toReconcileOutcome()
            }

            is LocalPolicyResult.Failure -> {
                when {
                    replaced.reason == LocalPolicyFailure.REVISION_CONFLICT && !retrying -> applyOnce(projection, base, retrying = true)
                    replaced.reason == LocalPolicyFailure.REVISION_CONFLICT -> ReconcileOutcome.Conflict
                    else -> ReconcileOutcome.StorageFailure
                }
            }
        }
    }
}

/** The workspace refused a website because the unique websites of its live sets already fill the shared cap. */
private fun SyncProjection.isWorkspaceFull(): Boolean {
    return audit.any { entry -> entry.outcome == SyncAuditOutcome.DOMAIN_CAPACITY } &&
        uniqueDomains().size >= SyncFormatLimits.MAX_SYNCHRONIZED_DOMAINS
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
