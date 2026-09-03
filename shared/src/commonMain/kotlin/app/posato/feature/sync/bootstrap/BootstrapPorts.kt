package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncContext
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId

internal const val ACCOUNT_BINDING_BYTES: Int = 32
internal const val WORKSPACE_KEY_BYTES: Int = 32
internal const val KEYCHAIN_ITEM_BYTES: Int = 84

internal class AccountBinding private constructor(
    private val bytes: ByteArray
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is AccountBinding && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "AccountBinding(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): AccountBinding? {
            return if (bytes.size == ACCOUNT_BINDING_BYTES) {
                AccountBinding(bytes.copyOf())
            } else {
                null
            }
        }
    }
}

internal class WorkspaceKeyValue private constructor(
    private val bytes: ByteArray
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is WorkspaceKeyValue && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "WorkspaceKeyValue(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): WorkspaceKeyValue? {
            return if (bytes.size == WORKSPACE_KEY_BYTES) {
                WorkspaceKeyValue(bytes.copyOf())
            } else {
                null
            }
        }
    }
}

internal class WorkspaceKeyItem private constructor(
    private val bytes: ByteArray
) {
    fun copyBytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun equals(other: Any?): Boolean {
        return other is WorkspaceKeyItem && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "WorkspaceKeyItem(redacted)"
    }

    companion object {
        fun fromBytes(bytes: ByteArray): WorkspaceKeyItem? {
            return if (bytes.size == KEYCHAIN_ITEM_BYTES) {
                WorkspaceKeyItem(bytes.copyOf())
            } else {
                null
            }
        }
    }
}

internal class KeyAccount private constructor(
    val text: String
) {
    override fun equals(other: Any?): Boolean {
        return other is KeyAccount && text == other.text
    }

    override fun hashCode(): Int {
        return text.hashCode()
    }

    override fun toString(): String {
        return "KeyAccount(redacted)"
    }

    companion object {
        fun fromText(text: String): KeyAccount? {
            return if (BootstrapEncoding.isCanonicalAccountText(text)) {
                KeyAccount(text)
            } else {
                null
            }
        }
    }
}

internal data class WorkspaceAnchor(
    val workspaceId: WorkspaceId,
    val transportEpochId: TransportEpochId,
    val keyEpochId: KeyEpochId
) {
    override fun toString(): String {
        return "WorkspaceAnchor(redacted)"
    }
}

internal sealed interface BindingResolution {
    data class Available(
        val binding: AccountBinding
    ) : BindingResolution

    data object Unavailable : BindingResolution

    data object Restricted : BindingResolution

    data object Undetermined : BindingResolution
}

internal sealed interface ZoneFetchResult {
    data object Found : ZoneFetchResult

    data object Missing : ZoneFetchResult

    data object Retryable : ZoneFetchResult

    data object AccountChanged : ZoneFetchResult

    data object UnknownOutcome : ZoneFetchResult
}

internal sealed interface ZoneSaveResult {
    data object Created : ZoneSaveResult

    data object AlreadyExists : ZoneSaveResult

    data object Retryable : ZoneSaveResult

    data object AccountChanged : ZoneSaveResult

    data object UnknownOutcome : ZoneSaveResult
}

internal sealed interface AnchorReadResult {
    data class Found(
        val anchor: WorkspaceAnchor
    ) : AnchorReadResult

    data object Missing : AnchorReadResult

    data object Retryable : AnchorReadResult

    data object AccountChanged : AnchorReadResult

    data object UnknownOutcome : AnchorReadResult

    data object IntegrityFailure : AnchorReadResult
}

internal sealed interface AnchorCreateResult {
    data object Created : AnchorCreateResult

    data object Conflict : AnchorCreateResult

    data object Retryable : AnchorCreateResult

    data object AccountChanged : AnchorCreateResult

    data object UnknownOutcome : AnchorCreateResult

    data object IntegrityFailure : AnchorCreateResult
}

internal sealed interface KeyItemReadResult {
    data class Found(
        val value: WorkspaceKeyItem
    ) : KeyItemReadResult

    data object Missing : KeyItemReadResult

    data object Retryable : KeyItemReadResult

    data object AccountChanged : KeyItemReadResult

    data object UnknownOutcome : KeyItemReadResult

    data object IntegrityFailure : KeyItemReadResult
}

internal sealed interface KeyItemCreateResult {
    data object Created : KeyItemCreateResult

    data object AlreadyExists : KeyItemCreateResult

    data object Retryable : KeyItemCreateResult

    data object AccountChanged : KeyItemCreateResult

    data object UnknownOutcome : KeyItemCreateResult

    data object IntegrityFailure : KeyItemCreateResult
}

internal sealed interface KeyItemDeleteResult {
    data object DeletedAndAbsent : KeyItemDeleteResult

    data object Retryable : KeyItemDeleteResult

    data object AccountChanged : KeyItemDeleteResult

    data object UnknownOutcome : KeyItemDeleteResult

    data object IntegrityFailure : KeyItemDeleteResult
}

internal interface BootstrapAccountPort {
    suspend fun resolveBinding(): BindingResolution
}

internal interface BootstrapCloudPort {
    suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult

    suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult

    suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult

    suspend fun createAnchor(
        expectedBinding: AccountBinding,
        anchor: WorkspaceAnchor
    ): AnchorCreateResult
}

internal interface BootstrapKeyPort {
    suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount
    ): KeyItemReadResult

    suspend fun createItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
        value: WorkspaceKeyItem
    ): KeyItemCreateResult

    suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount
    ): KeyItemDeleteResult
}

internal sealed interface BootstrapResult {
    data class Ready(
        val context: SyncContext
    ) : BootstrapResult

    data object WaitingForWorkspaceKey : BootstrapResult

    data object Retryable : BootstrapResult

    data object ActionRequired : BootstrapResult
}
