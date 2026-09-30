package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.sync.domain.PauseSetStatus
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.targets.data.LocalPolicyFailure
import app.posato.feature.targets.data.LocalPolicyResult
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.setIdOf
import app.posato.feature.targets.domain.SequencedPolicyIntent
import app.posato.feature.targets.domain.StoredPolicyIntent
import kotlinx.coroutines.CancellationException

internal sealed interface DrainStep {
    data object Continue : DrainStep

    data class Halt(
        val ok: Boolean
    ) : DrainStep
}

internal class AppleSyncAuthoring(
    private val bootstrap: BootstrapStore,
    private val intents: LocalPolicySyncStore,
    private val publish: (SyncStatus) -> Unit,
) {
    suspend fun captureWorkspace(): BootstrapStoreResult<EstablishedWorkspace?> {
        return when (val read = bootstrap.read()) {
            is BootstrapStoreResult.Failure -> read
            is BootstrapStoreResult.Success -> BootstrapStoreResult.Success((read.value as? BootstrapState.Established)?.workspace)
        }
    }

    /**
     * Authors this device's queued changes. Set removals wait for [setRemovals], which the policy phase runs
     * after schedules have moved away from the set.
     */
    suspend fun drain(
        writer: SyncWriter,
        setRemovals: Boolean = false,
    ): Boolean {
        return when (val captured = captureWorkspace()) {
            is BootstrapStoreResult.Failure -> fail()
            is BootstrapStoreResult.Success -> drainEstablished(writer, captured.value, setRemovals)
        }
    }

    private suspend fun drainEstablished(
        writer: SyncWriter,
        workspace: EstablishedWorkspace?,
        setRemovals: Boolean,
    ): Boolean {
        if (workspace == null) {
            return clearOrFail()
        }
        val workspaceId = workspace.context.workspaceId.value.copyBytes()
        var step: DrainStep = DrainStep.Continue
        while (step is DrainStep.Continue) {
            step = nextStep(writer, workspaceId, setRemovals)
        }
        return (step as DrainStep.Halt).ok
    }

    private suspend fun nextStep(
        writer: SyncWriter,
        workspaceId: ByteArray,
        setRemovals: Boolean,
    ): DrainStep {
        return when (val read = intents.readIntents()) {
            is LocalPolicyResult.Failure -> DrainStep.Halt(failWith(read.reason))

            is LocalPolicyResult.Success -> stepForRow(
                writer,
                workspaceId,
                read.value.filter { row ->
                    (row.intent is StoredPolicyIntent.RemoveSet) ==
                        setRemovals
                },
            )
        }
    }

    private suspend fun stepForRow(
        writer: SyncWriter,
        workspaceId: ByteArray,
        rows: List<SequencedPolicyIntent>,
    ): DrainStep {
        val row = rows.firstOrNull()
        return if (row == null) {
            DrainStep.Halt(true)
        } else if (!row.workspaceId.contentEquals(workspaceId)) {
            discard(row.sequence)
        } else if (isSkipped(writer, row)) {
            discard(row.sequence)
        } else {
            author(writer, row)
        }
    }

    private suspend fun discard(sequence: Long): DrainStep {
        return if (intents.deleteIntent(sequence) is LocalPolicyResult.Failure) {
            DrainStep.Halt(fail())
        } else {
            DrainStep.Continue
        }
    }

    private suspend fun author(
        writer: SyncWriter,
        row: SequencedPolicyIntent,
    ): DrainStep {
        return try {
            authorOnce(writer, row)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DrainStep.Halt(fail())
        }
    }

    private suspend fun authorOnce(
        writer: SyncWriter,
        row: SequencedPolicyIntent,
    ): DrainStep {
        val mutation = when (val intent = row.intent) {
            is StoredPolicyIntent.PresentDomain -> LocalSyncMutation.PresentDomain(intent.domain, intent.setId)
            is StoredPolicyIntent.RemoveDomain -> LocalSyncMutation.RemoveDomain(intent.domain, intent.setId)
            is StoredPolicyIntent.PutSet -> LocalSyncMutation.PutPauseSet(intent.setId, intent.name)
            is StoredPolicyIntent.RemoveSet -> LocalSyncMutation.RemovePauseSet(intent.setId)
            is StoredPolicyIntent.ChooseDefault -> LocalSyncMutation.ChoosePauseSetDefault(intent.setId)
        }
        if (writer.mutate(mutation) is LocalMutationResult.Failure) {
            return DrainStep.Halt(fail())
        }
        publish(SyncStatus.PENDING)
        return discard(row.sequence)
    }

    private suspend fun clearOrFail(): Boolean {
        return when (intents.clearIntents()) {
            is LocalPolicyResult.Success -> true
            is LocalPolicyResult.Failure -> fail()
        }
    }

    private fun failWith(reason: LocalPolicyFailure): Boolean {
        publish(reason.toSyncStatus())
        return false
    }

    private fun fail(): Boolean {
        publish(SyncStatus.ACTION_REQUIRED)
        return false
    }
}

private fun isSkipped(
    writer: SyncWriter,
    row: SequencedPolicyIntent,
): Boolean {
    val projection = writer.projection()
    val intent = row.intent
    // A change to a set another device removed can never apply, so it is discarded rather than authored.
    if (projection.pauseSetStatus(intent.setIdOf()) == PauseSetStatus.REMOVED) {
        return true
    }
    return when (intent) {
        is StoredPolicyIntent.PresentDomain -> intent.domain in projection.pauseSetDomains(intent.setId)
        is StoredPolicyIntent.RemoveDomain -> intent.domain !in projection.pauseSetDomains(intent.setId)
        is StoredPolicyIntent.PutSet -> projection.pauseSets.any { set -> set.setId == intent.setId && set.name == intent.name }
        is StoredPolicyIntent.RemoveSet -> false
        is StoredPolicyIntent.ChooseDefault -> projection.defaultPauseSetId == intent.setId
    }
}
