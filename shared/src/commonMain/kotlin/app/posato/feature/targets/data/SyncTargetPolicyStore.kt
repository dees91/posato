package app.posato.feature.targets.data

import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapStoreResult
import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.targets.domain.LocalPauseSet
import app.posato.feature.targets.domain.PauseSets
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
        return local.read()
    }

    override suspend fun replace(
        expectedRevision: Long,
        policy: TargetPolicy,
        syncWrite: PolicySyncWrite?,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        // The decorator always derives its own write from the before/after diff; a caller
        // write targets the raw store and never reaches this decorator.
        return recorded({ before -> diffIntents(before.policy, policy) }) { write -> local.replace(expectedRevision, policy, write) }
    }

    override suspend fun replaceSets(
        expectedRevision: Long,
        sets: PauseSets,
        syncWrite: PolicySyncWrite?,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return recorded({ before -> pauseSetIntents(before.sets, sets) }) { write -> local.replaceSets(expectedRevision, sets, write) }
    }

    private suspend fun recorded(
        intents: (LocalTargetPolicyState) -> List<StoredPolicyIntent>,
        replace: suspend (PolicySyncWrite?) -> LocalPolicyResult<LocalTargetPolicyState>,
    ): LocalPolicyResult<LocalTargetPolicyState> {
        return local.withWriteGate {
            val before = when (val read = local.read()) {
                is LocalPolicyResult.Success -> read.value
                is LocalPolicyResult.Failure -> return@withWriteGate read
            }
            val workspace = sync.captureWorkspace()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val write = recordedWrite(workspace, intents(before))
                val result = replace(write)
                if (result is LocalPolicyResult.Success && (write != null || workspace is BootstrapStoreResult.Failure)) {
                    sync.syncNow()
                }
                result
            }
        }
    }

    private fun recordedWrite(
        workspace: BootstrapStoreResult<EstablishedWorkspace?>,
        intents: List<StoredPolicyIntent>,
    ): PolicySyncWrite? {
        val established = when (workspace) {
            is BootstrapStoreResult.Success -> workspace.value ?: return null
            is BootstrapStoreResult.Failure -> return null
        }
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
        return removed.map(StoredPolicyIntent::RemoveDomain) + added.map(StoredPolicyIntent::PresentDomain)
    }
}

/**
 * The intents one edit of the pause sets authors, in the order other devices must apply them: a set's name
 * before its websites, the default after the set it names exists, and a removal last. A removed set's
 * websites author nothing, because its removal takes them with it.
 */
internal fun pauseSetIntents(
    before: PauseSets,
    after: PauseSets,
): List<StoredPolicyIntent> {
    val previous = before.sets.associateBy(LocalPauseSet::id)
    val kept = after.sets.mapTo(mutableSetOf(), LocalPauseSet::id)
    val names = after.sets.mapNotNull { set ->
        set.name?.takeIf { name -> name != previous[set.id]?.name }?.let { name -> StoredPolicyIntent.PutSet(set.id, name) }
    }
    val websites = after.sets.flatMap { set ->
        val held = previous[set.id]?.domains.orEmpty()
        (held - set.domains.toSet()).map { domain -> StoredPolicyIntent.RemoveDomain(domain, set.id) } +
            (set.domains - held.toSet()).map { domain -> StoredPolicyIntent.PresentDomain(domain, set.id) }
    }
    val default = after.defaultSetId?.takeIf { id -> id != before.defaultSetId }?.let { id -> listOf(StoredPolicyIntent.ChooseDefault(id)) }
    val removals = before.sets.filter { set -> set.id !in kept }.map { set -> StoredPolicyIntent.RemoveSet(set.id) }
    return names + websites + default.orEmpty() + removals
}
