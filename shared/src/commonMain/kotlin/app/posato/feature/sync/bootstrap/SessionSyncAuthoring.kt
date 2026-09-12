package app.posato.feature.sync.bootstrap

import app.posato.feature.session.data.LocalSessionResult
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.domain.SequencedSessionIntent
import app.posato.feature.session.domain.StoredSessionIntent
import app.posato.feature.sync.domain.LocalMutationResult
import app.posato.feature.sync.domain.LocalSyncMutation
import app.posato.feature.sync.domain.SyncWriter
import kotlinx.coroutines.CancellationException

internal class SessionSyncAuthoring(
    private val sessions: LocalSessionSyncStore,
) {
    suspend fun drain(
        writer: SyncWriter,
        workspaceId: ByteArray,
    ): Boolean {
        var step: DrainStep = DrainStep.Continue
        while (step is DrainStep.Continue) {
            step = nextStep(writer, workspaceId)
        }
        return (step as DrainStep.Halt).ok
    }

    private suspend fun nextStep(
        writer: SyncWriter,
        workspaceId: ByteArray,
    ): DrainStep {
        return when (val read = sessions.readIntents()) {
            is LocalSessionResult.Failure -> DrainStep.Halt(false)
            is LocalSessionResult.Success -> stepForRow(writer, workspaceId, read.value)
        }
    }

    private suspend fun stepForRow(
        writer: SyncWriter,
        workspaceId: ByteArray,
        rows: List<SequencedSessionIntent>,
    ): DrainStep {
        val row = rows.firstOrNull() ?: return DrainStep.Halt(true)
        if (!row.workspaceId.contentEquals(workspaceId)) {
            return discard(row.sequence)
        }
        if (isSkipped(writer, row)) {
            return discard(row.sequence)
        }
        return author(writer, row)
    }

    private suspend fun discard(sequence: Long): DrainStep {
        return if (sessions.deleteIntent(sequence) is LocalSessionResult.Failure) {
            DrainStep.Halt(false)
        } else {
            DrainStep.Continue
        }
    }

    private suspend fun author(
        writer: SyncWriter,
        row: SequencedSessionIntent,
    ): DrainStep {
        return try {
            authorOnce(writer, row)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DrainStep.Halt(false)
        }
    }

    private suspend fun authorOnce(
        writer: SyncWriter,
        row: SequencedSessionIntent,
    ): DrainStep {
        val mutation = when (val intent = row.intent) {
            is StoredSessionIntent.StartSession -> {
                LocalSyncMutation.StartSession(intent.sessionId, intent.startEpochMillis, intent.mandatoryEndEpochMillis)
            }

            is StoredSessionIntent.EndSession -> {
                LocalSyncMutation.EndSession(intent.sessionId)
            }
        }
        if (writer.mutate(mutation) is LocalMutationResult.Failure) {
            return DrainStep.Halt(false)
        }
        return discard(row.sequence)
    }
}

private fun isSkipped(
    writer: SyncWriter,
    row: SequencedSessionIntent,
): Boolean {
    val projection = writer.projection()
    return when (val intent = row.intent) {
        is StoredSessionIntent.StartSession -> {
            projection.eligibleSessionStarts.any { start -> start.sessionId == intent.sessionId } ||
                intent.sessionId in projection.conflictedSessionIds
        }

        is StoredSessionIntent.EndSession -> {
            projection.eligibleSessionStarts.any { start -> start.sessionId == intent.sessionId && start.isEnded }
        }
    }
}
