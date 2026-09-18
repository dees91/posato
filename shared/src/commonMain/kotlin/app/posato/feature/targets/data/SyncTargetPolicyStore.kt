package app.posato.feature.targets.data

import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.targets.domain.PolicySyncWrite
import app.posato.feature.targets.domain.StoredPolicyIntent
import app.posato.feature.targets.domain.TargetPolicy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class SyncTargetPolicyStore(
    private val local: LocalTargetPolicyStore,
    private val sync: AppleSync,
) : LocalTargetPolicyStore {
    override suspend fun <T> withWriteGate(block: suspend () -> T): T {
        return local.withWriteGate(block)
    }

    override val policyChanges: Flow<Unit>
        get() = local.policyChanges

    override suspend fun read(): LocalPolicyResult<LocalTargetPolicyState> {
        if (local.wwwCounterpartExpansionCompleted()) {
            return local.read()
        }
        return local.withWriteGate {
            if (local.wwwCounterpartExpansionCompleted()) {
                return@withWriteGate local.read()
            }
            val before = when (val read = local.read()) {
                is LocalPolicyResult.Success -> read.value
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val expanded = before.policy.withWwwCounterparts()
            if (expanded == before.policy) {
                local.markWwwCounterpartExpansionCompleted()
                return@withWriteGate LocalPolicyResult.Success(before)
            }
            val workspace = sync.captureWorkspace()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val write = recordedWrite(workspace, before.policy, expanded)
                val result = local.replace(before.revision, expanded, write)
                if (result is LocalPolicyResult.Success) {
                    local.markWwwCounterpartExpansionCompleted()
                    if (write != null || workspace is BootstrapStoreResult.Failure) {
                        sync.syncNow()
                    }
                }
                result
            }
        }
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
        syncWrite: PolicySyncWrite?,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        // The decorator always derives its own write from the before/after diff; a caller
        // write targets the raw store and never reaches this decorator.
        return local.withWriteGate {
            val before = when (val read = local.read()) {
                is LocalPolicyResult.Success -> read.value
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val workspace = sync.captureWorkspace()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val write = recordedWrite(workspace, before.policy, policy)
                val result = local.replace(expectedRevision, policy, write)
                if (result is LocalPolicyResult.Success && (write != null || workspace is BootstrapStoreResult.Failure)) {
                    sync.syncNow()
                }
                result
            }
        }
    }

    private fun recordedWrite(
        workspace: BootstrapStoreResult<EstablishedWorkspace?>,
        before: TargetPolicy,
        after: TargetPolicy,
    ): PolicySyncWrite? {
        val established = when (workspace) {
            is BootstrapStoreResult.Success -> workspace.value ?: return null
            is BootstrapStoreResult.Failure -> return null
        }
        val intents = diffIntents(before, after)
        if (intents.isEmpty()) {
            return null
        }
        return PolicySyncWrite(established.context.workspaceId.value.copyBytes(), intents)
    }

    private fun diffIntents(
        before: TargetPolicy,
        after: TargetPolicy,
    ): List<StoredPolicyIntent> {
        val removed = before.domains - after.domains.toSet()
        val added = after.domains - before.domains.toSet()
        val renamed = after.applicationPolicyName
            ?.takeIf { name -> name != before.applicationPolicyName }
            ?.let { name -> StoredPolicyIntent.PresentApplicationPolicy(name) }
        return removed.map(StoredPolicyIntent::RemoveDomain) +
            added.map(StoredPolicyIntent::PresentDomain) +
            listOfNotNull(renamed)
    }
}
