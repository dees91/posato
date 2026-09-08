package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorCreateResult
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.BootstrapCloudPort
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.bootstrap.ZoneFetchResult
import app.posato.feature.sync.bootstrap.ZoneSaveResult
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import kotlinx.coroutines.CancellationException

internal class MacOsBootstrapCloudAdapter(
    private val transport: SyncCompanionTransport,
    private val deadlineMilliseconds: Int = DEFAULT_DEADLINE_MILLISECONDS,
) : BootstrapCloudPort {
    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        val binding = expectedBinding.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.FetchZone,
                payload = MacOsSyncCompanionProtocol.cloudPayload(binding),
            )
            when (response) {
                CompanionExchange.Unknown -> ZoneFetchResult.UnknownOutcome
                is CompanionExchange.Message -> mapZoneFetch(response.message)
            }
        } finally {
            binding.fill(0)
        }
    }

    override suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult {
        val binding = expectedBinding.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.SaveZone,
                payload = MacOsSyncCompanionProtocol.cloudPayload(binding),
            )
            when (response) {
                CompanionExchange.Unknown -> ZoneSaveResult.UnknownOutcome
                is CompanionExchange.Message -> mapZoneSave(response.message)
            }
        } finally {
            binding.fill(0)
        }
    }

    override suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult {
        val binding = expectedBinding.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.ReadAnchor,
                payload = MacOsSyncCompanionProtocol.cloudPayload(binding),
            )
            when (response) {
                CompanionExchange.Unknown -> AnchorReadResult.UnknownOutcome
                is CompanionExchange.Message -> mapAnchorRead(response.message)
            }
        } finally {
            binding.fill(0)
        }
    }

    override suspend fun createAnchor(
        expectedBinding: AccountBinding,
        anchor: WorkspaceAnchor,
    ): AnchorCreateResult {
        val binding = expectedBinding.copyBytes()
        val fields = encodeAnchor(anchor)
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.CreateAnchor,
                payload = MacOsSyncCompanionProtocol.anchorPayload(binding, fields),
            )
            when (response) {
                CompanionExchange.Unknown -> AnchorCreateResult.UnknownOutcome
                is CompanionExchange.Message -> mapAnchorCreate(response.message)
            }
        } finally {
            binding.fill(0)
            fields.fill(0)
        }
    }

    private suspend fun exchange(
        operation: SyncCompanionOperation,
        payload: ByteArray,
    ): CompanionExchange {
        val message = SyncCompanionMessage(
            operation = operation,
            requestIdentifier = transport.newRequestIdentifier(),
            deadlineMilliseconds = deadlineMilliseconds,
            capabilities = MacOsSyncCompanionProtocol.CLOUDKIT_CAPABILITY,
            outcome = null,
            payload = payload,
        )
        return try {
            transport.transact(message)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            CompanionExchange.Unknown
        } catch (_: IllegalStateException) {
            CompanionExchange.Unknown
        } finally {
            payload.fill(0)
        }
    }

    private fun mapZoneFetch(message: SyncCompanionMessage): ZoneFetchResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Found -> {
                ZoneFetchResult.Found
            }

            SyncCompanionOutcome.Missing -> {
                ZoneFetchResult.Missing
            }

            SyncCompanionOutcome.Retryable -> {
                ZoneFetchResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                ZoneFetchResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                ZoneFetchResult.UnknownOutcome
            }

            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.IntegrityFailure,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                ZoneFetchResult.UnknownOutcome
            }
        }
    }

    private fun mapZoneSave(message: SyncCompanionMessage): ZoneSaveResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Created -> {
                ZoneSaveResult.Created
            }

            SyncCompanionOutcome.AlreadyExists -> {
                ZoneSaveResult.AlreadyExists
            }

            SyncCompanionOutcome.Retryable -> {
                ZoneSaveResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                ZoneSaveResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                ZoneSaveResult.UnknownOutcome
            }

            SyncCompanionOutcome.Found,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.IntegrityFailure,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                ZoneSaveResult.UnknownOutcome
            }
        }
    }

    private fun mapAnchorRead(message: SyncCompanionMessage): AnchorReadResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Found -> {
                val anchor = decodeAnchor(message.payload)
                if (anchor == null || message.payload.size != MacOsSyncCompanionProtocol.ANCHOR_BYTES) {
                    AnchorReadResult.IntegrityFailure
                } else {
                    AnchorReadResult.Found(anchor)
                }
            }

            SyncCompanionOutcome.Missing -> {
                AnchorReadResult.Missing
            }

            SyncCompanionOutcome.Retryable -> {
                AnchorReadResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                AnchorReadResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                AnchorReadResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                AnchorReadResult.IntegrityFailure
            }

            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                AnchorReadResult.UnknownOutcome
            }
        }
    }

    private fun mapAnchorCreate(message: SyncCompanionMessage): AnchorCreateResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Created -> {
                AnchorCreateResult.Created
            }

            SyncCompanionOutcome.Conflict -> {
                AnchorCreateResult.Conflict
            }

            SyncCompanionOutcome.Retryable -> {
                AnchorCreateResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                AnchorCreateResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                AnchorCreateResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                AnchorCreateResult.IntegrityFailure
            }

            SyncCompanionOutcome.Found,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                AnchorCreateResult.UnknownOutcome
            }
        }
    }

    private fun encodeAnchor(anchor: WorkspaceAnchor): ByteArray {
        val result = ByteArray(MacOsSyncCompanionProtocol.ANCHOR_BYTES)
        anchor.workspaceId.value.copyBytes().copyInto(result, WORKSPACE_OFFSET)
        anchor.transportEpochId.value.copyBytes().copyInto(result, TRANSPORT_EPOCH_OFFSET)
        anchor.keyEpochId.value.copyBytes().copyInto(result, KEY_EPOCH_OFFSET)
        return result
    }

    private fun decodeAnchor(bytes: ByteArray): WorkspaceAnchor? {
        if (bytes.size != MacOsSyncCompanionProtocol.ANCHOR_BYTES) {
            return null
        }
        val workspaceId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(WORKSPACE_OFFSET, TRANSPORT_EPOCH_OFFSET))
            ?: return null
        val transportEpochId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(TRANSPORT_EPOCH_OFFSET, KEY_EPOCH_OFFSET))
            ?: return null
        val keyEpochId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(KEY_EPOCH_OFFSET, MacOsSyncCompanionProtocol.ANCHOR_BYTES))
            ?: return null
        return WorkspaceAnchor(
            workspaceId = WorkspaceId(workspaceId),
            transportEpochId = TransportEpochId(transportEpochId),
            keyEpochId = KeyEpochId(keyEpochId),
        )
    }

    private companion object {
        const val DEFAULT_DEADLINE_MILLISECONDS: Int = 30_000
        const val IDENTIFIER_BYTES: Int = 16
        const val WORKSPACE_OFFSET: Int = 0
        const val TRANSPORT_EPOCH_OFFSET: Int = IDENTIFIER_BYTES
        const val KEY_EPOCH_OFFSET: Int = 2 * IDENTIFIER_BYTES
    }
}
