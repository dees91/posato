package app.posato.feature.session.data

import app.posato.feature.sync.bootstrap.EstablishedWorkspace
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.domain.SyncWriter

internal sealed interface SessionWorkspaceCapture {
    data class Linked(
        val workspaceId: ByteArray,
    ) : SessionWorkspaceCapture {
        override fun equals(other: Any?): Boolean {
            return other is Linked && workspaceId.contentEquals(other.workspaceId)
        }

        override fun hashCode(): Int {
            return workspaceId.contentHashCode()
        }

        override fun toString(): String {
            return "SessionWorkspaceCapture.Linked(redacted)"
        }
    }

    data object Unlinked : SessionWorkspaceCapture

    data object Unknown : SessionWorkspaceCapture
}

internal interface SessionSyncTriggers {
    suspend fun captureWorkspace(): SessionWorkspaceCapture

    fun requestSync()
}

internal interface SessionExchangeObserver {
    suspend fun onExchange(
        writer: SyncWriter,
        workspace: EstablishedWorkspace,
    ): SyncStatus?
}
