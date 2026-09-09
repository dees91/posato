package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.OpenSyncWriterResult
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.domain.TransportKey

internal class AppleSyncWriter(
    private val coordinator: BootstrapCoordinator,
    private val core: SyncOperationCore,
    private val publish: (SyncStatus) -> Unit,
) {
    private var current: SyncWriter? = null

    suspend fun open(): SyncWriter? {
        current?.takeIf { it.isActive }?.let { return it }
        close()
        return when (val read = coordinator.readWorkspaceKey()) {
            is WorkspaceKeyRead.Ready -> {
                val key = read.key.useAndClear { checkNotNull(TransportKey.fromBytes(it)) }
                when (val opened = core.open(read.context, key)) {
                    is OpenSyncWriterResult.Success -> {
                        opened.writer.also { current = it }
                    }

                    is OpenSyncWriterResult.Failure -> {
                        publish(SyncStatus.ACTION_REQUIRED)
                        null
                    }
                }
            }

            WorkspaceKeyRead.WaitingForWorkspaceKey -> {
                stopOpening(SyncStatus.WAITING_FOR_KEY)
            }

            WorkspaceKeyRead.Retryable -> {
                stopOpening(SyncStatus.RETRYABLE)
            }

            WorkspaceKeyRead.ActionRequired -> {
                stopOpening(SyncStatus.ACTION_REQUIRED)
            }
        }
    }

    private fun stopOpening(status: SyncStatus): SyncWriter? {
        publish(status)
        return null
    }

    suspend fun close() {
        current?.close()
        current = null
    }
}
