package app.posato.feature.sync.data

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
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

enum class IosKeychainBindingStatus {
    Available,
    Unavailable,
    Restricted,
    Undetermined,
}

class IosKeychainBinding(
    val status: IosKeychainBindingStatus,
    val value: NSData?,
) {
    override fun toString(): String {
        return "IosKeychainBinding(redacted)"
    }
}

enum class IosKeychainReadStatus {
    Found,
    Missing,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

class IosKeychainItemRead(
    val status: IosKeychainReadStatus,
    val value: NSData?,
) {
    override fun toString(): String {
        return "IosKeychainItemRead(redacted)"
    }
}

enum class IosKeychainCreateStatus {
    Created,
    AlreadyExists,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

enum class IosKeychainDeleteStatus {
    DeletedAndAbsent,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

interface IosKeychainProvider {
    fun resolveBinding(): IosKeychainBinding

    fun readItem(
        binding: NSData,
        account: String,
    ): IosKeychainItemRead

    fun createItem(
        binding: NSData,
        account: String,
        value: NSData,
    ): IosKeychainCreateStatus

    fun deleteItemAndVerifyAbsent(
        binding: NSData,
        account: String,
    ): IosKeychainDeleteStatus
}

internal class IosBootstrapKeychainAdapter(
    private val provider: IosKeychainProvider,
) : BootstrapAccountPort,
    BootstrapKeyPort {
    override suspend fun resolveBinding(): BindingResolution {
        val result = offMainThread { provider.resolveBinding() }
        val bytes = result.value?.toByteArray(ACCOUNT_BINDING_BYTES)
        try {
            return when (result.status) {
                IosKeychainBindingStatus.Available -> {
                    val binding = bytes?.let { AccountBinding.fromBytes(it) }

                    if (binding == null) {
                        BindingResolution.Undetermined
                    } else {
                        BindingResolution.Available(binding)
                    }
                }

                IosKeychainBindingStatus.Unavailable -> {
                    BindingResolution.Unavailable
                }

                IosKeychainBindingStatus.Restricted -> {
                    BindingResolution.Restricted
                }

                IosKeychainBindingStatus.Undetermined -> {
                    BindingResolution.Undetermined
                }
            }
        } finally {
            bytes?.fill(0)
        }
    }

    override suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemReadResult {
        val binding = expectedBinding.copyBytes()
        try {
            val result = offMainThread { provider.readItem(binding.toNSData(), account.text) }
            val bytes = result.value?.toByteArray(KEYCHAIN_ITEM_BYTES)
            try {
                return when (result.status) {
                    IosKeychainReadStatus.Found -> {
                        val item = bytes?.let { WorkspaceKeyItem.fromBytes(it) }

                        if (item == null) {
                            KeyItemReadResult.IntegrityFailure
                        } else {
                            KeyItemReadResult.Found(item)
                        }
                    }

                    IosKeychainReadStatus.Missing -> {
                        KeyItemReadResult.Missing
                    }

                    IosKeychainReadStatus.Retryable -> {
                        KeyItemReadResult.Retryable
                    }

                    IosKeychainReadStatus.AccountChanged -> {
                        KeyItemReadResult.AccountChanged
                    }

                    IosKeychainReadStatus.UnknownOutcome -> {
                        KeyItemReadResult.UnknownOutcome
                    }

                    IosKeychainReadStatus.IntegrityFailure -> {
                        KeyItemReadResult.IntegrityFailure
                    }
                }
            } finally {
                bytes?.fill(0)
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
        val bytes = value.copyBytes()
        try {
            val status = offMainThread { provider.createItem(binding.toNSData(), account.text, bytes.toNSData()) }
            return when (status) {
                IosKeychainCreateStatus.Created -> KeyItemCreateResult.Created
                IosKeychainCreateStatus.AlreadyExists -> KeyItemCreateResult.AlreadyExists
                IosKeychainCreateStatus.Retryable -> KeyItemCreateResult.Retryable
                IosKeychainCreateStatus.AccountChanged -> KeyItemCreateResult.AccountChanged
                IosKeychainCreateStatus.UnknownOutcome -> KeyItemCreateResult.UnknownOutcome
                IosKeychainCreateStatus.IntegrityFailure -> KeyItemCreateResult.IntegrityFailure
            }
        } finally {
            binding.fill(0)
            bytes.fill(0)
        }
    }

    override suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemDeleteResult {
        val binding = expectedBinding.copyBytes()
        try {
            val status = offMainThread { provider.deleteItemAndVerifyAbsent(binding.toNSData(), account.text) }
            return when (status) {
                IosKeychainDeleteStatus.DeletedAndAbsent -> KeyItemDeleteResult.DeletedAndAbsent
                IosKeychainDeleteStatus.Retryable -> KeyItemDeleteResult.Retryable
                IosKeychainDeleteStatus.AccountChanged -> KeyItemDeleteResult.AccountChanged
                IosKeychainDeleteStatus.UnknownOutcome -> KeyItemDeleteResult.UnknownOutcome
                IosKeychainDeleteStatus.IntegrityFailure -> KeyItemDeleteResult.IntegrityFailure
            }
        } finally {
            binding.fill(0)
        }
    }
}

internal object UnavailableIosKeychainProvider : IosKeychainProvider {
    override fun resolveBinding(): IosKeychainBinding {
        return IosKeychainBinding(IosKeychainBindingStatus.Undetermined, null)
    }

    override fun readItem(
        binding: NSData,
        account: String,
    ): IosKeychainItemRead {
        return IosKeychainItemRead(IosKeychainReadStatus.UnknownOutcome, null)
    }

    override fun createItem(
        binding: NSData,
        account: String,
        value: NSData,
    ): IosKeychainCreateStatus {
        return IosKeychainCreateStatus.UnknownOutcome
    }

    override fun deleteItemAndVerifyAbsent(
        binding: NSData,
        account: String,
    ): IosKeychainDeleteStatus {
        return IosKeychainDeleteStatus.UnknownOutcome
    }
}

private suspend fun <T> offMainThread(call: () -> T): T {
    return withContext(Dispatchers.IO) {
        call()
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(expectedSize: Int): ByteArray? {
    if (expectedSize < 0 || length != expectedSize.toULong()) {
        return null
    }
    val result = ByteArray(expectedSize)
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, expectedSize.toULong())
        }
    }

    return result
}
