package app.posato.feature.sync.macos

import app.posato.feature.sync.bootstrap.ACCOUNT_BINDING_BYTES
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapAccountPort
import app.posato.feature.sync.bootstrap.BootstrapKeyPort
import app.posato.feature.sync.bootstrap.KEYCHAIN_ITEM_BYTES
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemDeleteResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import kotlinx.coroutines.CancellationException

internal class MacOsBootstrapKeychainAdapter(
    private val transport: SyncCompanionTransport,
    private val deadlineMilliseconds: Int = DEFAULT_DEADLINE_MILLISECONDS,
) : BootstrapAccountPort,
    BootstrapKeyPort {
    override suspend fun resolveBinding(): BindingResolution {
        val response = exchange(
            operation = SyncCompanionOperation.ResolveBinding,
            payload = ByteArray(0),
        )
        return when (response) {
            CompanionExchange.Unknown -> BindingResolution.Undetermined
            is CompanionExchange.Message -> mapBinding(response.message)
        }
    }

    override suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemReadResult {
        val binding = expectedBinding.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.ReadItem,
                payload = MacOsSyncCompanionProtocol.keyPayload(binding, account.text),
            )
            when (response) {
                CompanionExchange.Unknown -> KeyItemReadResult.UnknownOutcome
                is CompanionExchange.Message -> mapRead(response.message)
            }
        } finally {
            binding.fill(0)
        }
    }

    override suspend fun createItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
        value: WorkspaceKeyItem,
    ): KeyItemCreateResult {
        val binding = expectedBinding.copyBytes()
        val item = value.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.CreateItem,
                payload = MacOsSyncCompanionProtocol.keyPayload(binding, account.text, item),
            )
            when (response) {
                CompanionExchange.Unknown -> KeyItemCreateResult.UnknownOutcome
                is CompanionExchange.Message -> mapCreate(response.message)
            }
        } finally {
            binding.fill(0)
            item.fill(0)
        }
    }

    override suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemDeleteResult {
        val binding = expectedBinding.copyBytes()
        return try {
            val response = exchange(
                operation = SyncCompanionOperation.DeleteItemAndVerifyAbsent,
                payload = MacOsSyncCompanionProtocol.keyPayload(binding, account.text),
            )
            when (response) {
                CompanionExchange.Unknown -> KeyItemDeleteResult.UnknownOutcome
                is CompanionExchange.Message -> mapDelete(response.message)
            }
        } finally {
            binding.fill(0)
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
            capabilities = MacOsSyncCompanionProtocol.KEYCHAIN_CAPABILITY,
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

    private fun mapBinding(message: SyncCompanionMessage): BindingResolution {
        return when (message.outcome) {
            SyncCompanionOutcome.Found -> {
                val binding = AccountBinding.fromBytes(message.payload)
                if (binding == null || message.payload.size != ACCOUNT_BINDING_BYTES) {
                    BindingResolution.Undetermined
                } else {
                    BindingResolution.Available(binding)
                }
            }

            SyncCompanionOutcome.Unavailable -> {
                BindingResolution.Unavailable
            }

            SyncCompanionOutcome.Restricted -> {
                BindingResolution.Restricted
            }

            SyncCompanionOutcome.Undetermined,
            SyncCompanionOutcome.Retryable,
            SyncCompanionOutcome.UnknownOutcome,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.AccountChanged,
            SyncCompanionOutcome.IntegrityFailure,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                BindingResolution.Undetermined
            }
        }
    }

    private fun mapRead(message: SyncCompanionMessage): KeyItemReadResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Found -> {
                val item = WorkspaceKeyItem.fromBytes(message.payload)
                if (item == null || message.payload.size != KEYCHAIN_ITEM_BYTES) {
                    KeyItemReadResult.IntegrityFailure
                } else {
                    KeyItemReadResult.Found(item)
                }
            }

            SyncCompanionOutcome.Missing -> {
                KeyItemReadResult.Missing
            }

            SyncCompanionOutcome.Retryable -> {
                KeyItemReadResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                KeyItemReadResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                KeyItemReadResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                KeyItemReadResult.IntegrityFailure
            }

            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined -> {
                KeyItemReadResult.Retryable
            }

            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                KeyItemReadResult.UnknownOutcome
            }
        }
    }

    private fun mapCreate(message: SyncCompanionMessage): KeyItemCreateResult {
        return when (message.outcome) {
            SyncCompanionOutcome.Created -> {
                KeyItemCreateResult.Created
            }

            SyncCompanionOutcome.Identical -> {
                KeyItemCreateResult.AlreadyExists
            }

            SyncCompanionOutcome.Retryable -> {
                KeyItemCreateResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                KeyItemCreateResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                KeyItemCreateResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                KeyItemCreateResult.IntegrityFailure
            }

            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined -> {
                KeyItemCreateResult.Retryable
            }

            SyncCompanionOutcome.Found,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.DeletedAndAbsent,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                KeyItemCreateResult.UnknownOutcome
            }
        }
    }

    private fun mapDelete(message: SyncCompanionMessage): KeyItemDeleteResult {
        return when (message.outcome) {
            SyncCompanionOutcome.DeletedAndAbsent -> {
                KeyItemDeleteResult.DeletedAndAbsent
            }

            SyncCompanionOutcome.Retryable -> {
                KeyItemDeleteResult.Retryable
            }

            SyncCompanionOutcome.AccountChanged -> {
                KeyItemDeleteResult.AccountChanged
            }

            SyncCompanionOutcome.UnknownOutcome -> {
                KeyItemDeleteResult.UnknownOutcome
            }

            SyncCompanionOutcome.IntegrityFailure -> {
                KeyItemDeleteResult.IntegrityFailure
            }

            SyncCompanionOutcome.Unavailable,
            SyncCompanionOutcome.Restricted,
            SyncCompanionOutcome.Undetermined -> {
                KeyItemDeleteResult.Retryable
            }

            SyncCompanionOutcome.Found,
            SyncCompanionOutcome.Missing,
            SyncCompanionOutcome.Created,
            SyncCompanionOutcome.Identical,
            SyncCompanionOutcome.AlreadyExists,
            SyncCompanionOutcome.Conflict,
            SyncCompanionOutcome.TokenExpired,
            null -> {
                KeyItemDeleteResult.UnknownOutcome
            }
        }
    }

    private companion object {
        const val DEFAULT_DEADLINE_MILLISECONDS: Int = 15_000
    }
}
