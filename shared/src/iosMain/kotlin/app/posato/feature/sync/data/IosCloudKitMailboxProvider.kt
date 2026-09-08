package app.posato.feature.sync.data

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
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MAILBOX_BUNDLE_BYTES
import app.posato.feature.sync.mailbox.MAILBOX_BUNDLE_IDENTIFIER_BYTES
import app.posato.feature.sync.mailbox.MAILBOX_CURSOR_BYTES
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.ZoneDeleteResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

enum class IosCloudZoneFetchStatus {
    Found,
    Missing,
    Retryable,
    AccountChanged,
    UnknownOutcome,
}

enum class IosCloudZoneSaveStatus {
    Created,
    AlreadyExists,
    Retryable,
    AccountChanged,
    UnknownOutcome,
}

enum class IosCloudAnchorReadStatus {
    Found,
    Missing,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

class IosCloudAnchorRead(
    val status: IosCloudAnchorReadStatus,
    val fields: NSData?,
) {
    override fun toString(): String {
        return "IosCloudAnchorRead(redacted)"
    }
}

enum class IosCloudAnchorCreateStatus {
    Created,
    Conflict,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

enum class IosCloudBundleSaveStatus {
    Saved,
    Identical,
    Conflict,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

enum class IosCloudChangeFetchStatus {
    TokenExpired,
    Page,
    ZoneMissing,
    Retryable,
    AccountChanged,
    UnknownOutcome,
    IntegrityFailure,
}

class IosCloudChangePage(
    val status: IosCloudChangeFetchStatus,
    val moreChanges: Boolean,
    val nextCursor: NSData?,
    val bundleIdentifier: NSData?,
    val bundlePayload: NSData?,
) {
    override fun toString(): String {
        return "IosCloudChangePage(redacted)"
    }
}

enum class IosCloudZoneDeleteStatus {
    DeletedAndAbsent,
    Retryable,
    AccountChanged,
    UnknownOutcome,
}

/**
 * One in-flight call per instance: implementations keep a single CloudKit
 * operation handle, so callers must not overlap calls on one instance and
 * [cancelInflight] aborts whichever call is current. Overlapping calls may
 * observe `UnknownOutcome`. Caller serialization stays with the future
 * coordinator; adapters already leave the calling thread.
 */
interface IosCloudKitMailboxProvider {
    fun fetchZone(binding: NSData): IosCloudZoneFetchStatus

    fun saveZone(binding: NSData): IosCloudZoneSaveStatus

    fun readAnchor(binding: NSData): IosCloudAnchorRead

    fun createAnchor(
        binding: NSData,
        fields: NSData,
    ): IosCloudAnchorCreateStatus

    fun saveBundle(
        binding: NSData,
        identifier: NSData,
        payload: NSData,
    ): IosCloudBundleSaveStatus

    fun fetchChanges(
        binding: NSData,
        cursor: NSData,
    ): IosCloudChangePage

    fun deleteZoneAndVerifyAbsent(binding: NSData): IosCloudZoneDeleteStatus

    fun cancelInflight()
}

internal class IosBootstrapCloudAdapter(
    private val provider: IosCloudKitMailboxProvider,
) : BootstrapCloudPort {
    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        val binding = expectedBinding.copyBytes()
        try {
            return when (provider.cancellableCall { fetchZone(binding.toNSData()) }) {
                IosCloudZoneFetchStatus.Found -> {
                    ZoneFetchResult.Found
                }

                IosCloudZoneFetchStatus.Missing -> {
                    ZoneFetchResult.Missing
                }

                IosCloudZoneFetchStatus.Retryable -> {
                    ZoneFetchResult.Retryable
                }

                IosCloudZoneFetchStatus.AccountChanged -> {
                    ZoneFetchResult.AccountChanged
                }

                IosCloudZoneFetchStatus.UnknownOutcome -> {
                    ZoneFetchResult.UnknownOutcome
                }
            }
        } finally {
            binding.fill(0)
        }
    }

    override suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult {
        val binding = expectedBinding.copyBytes()
        try {
            return when (provider.cancellableCall { saveZone(binding.toNSData()) }) {
                IosCloudZoneSaveStatus.Created -> {
                    ZoneSaveResult.Created
                }

                IosCloudZoneSaveStatus.AlreadyExists -> {
                    ZoneSaveResult.AlreadyExists
                }

                IosCloudZoneSaveStatus.Retryable -> {
                    ZoneSaveResult.Retryable
                }

                IosCloudZoneSaveStatus.AccountChanged -> {
                    ZoneSaveResult.AccountChanged
                }

                IosCloudZoneSaveStatus.UnknownOutcome -> {
                    ZoneSaveResult.UnknownOutcome
                }
            }
        } finally {
            binding.fill(0)
        }
    }

    override suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult {
        val binding = expectedBinding.copyBytes()
        try {
            val result = provider.cancellableCall { readAnchor(binding.toNSData()) }
            val bytes = result.fields?.toByteArray(ANCHOR_FIELDS_BYTES)
            try {
                return when (result.status) {
                    IosCloudAnchorReadStatus.Found -> {
                        val anchor = bytes?.let(::decodeAnchor)

                        if (anchor == null) {
                            AnchorReadResult.IntegrityFailure
                        } else {
                            AnchorReadResult.Found(anchor)
                        }
                    }

                    IosCloudAnchorReadStatus.Missing -> {
                        AnchorReadResult.Missing
                    }

                    IosCloudAnchorReadStatus.Retryable -> {
                        AnchorReadResult.Retryable
                    }

                    IosCloudAnchorReadStatus.AccountChanged -> {
                        AnchorReadResult.AccountChanged
                    }

                    IosCloudAnchorReadStatus.UnknownOutcome -> {
                        AnchorReadResult.UnknownOutcome
                    }

                    IosCloudAnchorReadStatus.IntegrityFailure -> {
                        AnchorReadResult.IntegrityFailure
                    }
                }
            } finally {
                bytes?.fill(0)
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
        try {
            return when (provider.cancellableCall { createAnchor(binding.toNSData(), fields.toNSData()) }) {
                IosCloudAnchorCreateStatus.Created -> {
                    AnchorCreateResult.Created
                }

                IosCloudAnchorCreateStatus.Conflict -> {
                    AnchorCreateResult.Conflict
                }

                IosCloudAnchorCreateStatus.Retryable -> {
                    AnchorCreateResult.Retryable
                }

                IosCloudAnchorCreateStatus.AccountChanged -> {
                    AnchorCreateResult.AccountChanged
                }

                IosCloudAnchorCreateStatus.UnknownOutcome -> {
                    AnchorCreateResult.UnknownOutcome
                }

                IosCloudAnchorCreateStatus.IntegrityFailure -> {
                    AnchorCreateResult.IntegrityFailure
                }
            }
        } finally {
            binding.fill(0)
            fields.fill(0)
        }
    }

    private fun encodeAnchor(anchor: WorkspaceAnchor): ByteArray {
        val result = ByteArray(ANCHOR_FIELDS_BYTES)
        anchor.workspaceId.value.copyBytes().copyInto(result, WORKSPACE_OFFSET)
        anchor.transportEpochId.value.copyBytes().copyInto(result, TRANSPORT_EPOCH_OFFSET)
        anchor.keyEpochId.value.copyBytes().copyInto(result, KEY_EPOCH_OFFSET)
        return result
    }

    private fun decodeAnchor(bytes: ByteArray): WorkspaceAnchor? {
        if (bytes.size != ANCHOR_FIELDS_BYTES) {
            return null
        }
        val workspaceId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(WORKSPACE_OFFSET, TRANSPORT_EPOCH_OFFSET))
            ?: return null
        val transportEpochId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(TRANSPORT_EPOCH_OFFSET, KEY_EPOCH_OFFSET))
            ?: return null
        val keyEpochId = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(KEY_EPOCH_OFFSET, ANCHOR_FIELDS_BYTES))
            ?: return null
        return WorkspaceAnchor(
            workspaceId = WorkspaceId(workspaceId),
            transportEpochId = TransportEpochId(transportEpochId),
            keyEpochId = KeyEpochId(keyEpochId),
        )
    }

    private companion object {
        const val ANCHOR_FIELDS_BYTES: Int = 48
        const val IDENTIFIER_BYTES: Int = 16
        const val WORKSPACE_OFFSET: Int = 0
        const val TRANSPORT_EPOCH_OFFSET: Int = IDENTIFIER_BYTES
        const val KEY_EPOCH_OFFSET: Int = 2 * IDENTIFIER_BYTES
    }
}

internal class IosMailboxAdapter(
    private val provider: IosCloudKitMailboxProvider,
) : MailboxPort {
    override suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray,
    ): BundleSaveResult {
        val binding = expectedBinding.copyBytes()
        val identifierCopy = identifier.copyOf()
        val payloadCopy = payload.copyOf()
        try {
            return when (
                provider.cancellableCall { saveBundle(binding.toNSData(), identifierCopy.toNSData(), payloadCopy.toNSData()) }
            ) {
                IosCloudBundleSaveStatus.Saved -> {
                    BundleSaveResult.Saved
                }

                IosCloudBundleSaveStatus.Identical -> {
                    BundleSaveResult.Identical
                }

                IosCloudBundleSaveStatus.Conflict -> {
                    BundleSaveResult.Conflict
                }

                IosCloudBundleSaveStatus.Retryable -> {
                    BundleSaveResult.Retryable
                }

                IosCloudBundleSaveStatus.AccountChanged -> {
                    BundleSaveResult.AccountChanged
                }

                IosCloudBundleSaveStatus.UnknownOutcome -> {
                    BundleSaveResult.UnknownOutcome
                }

                IosCloudBundleSaveStatus.IntegrityFailure -> {
                    BundleSaveResult.IntegrityFailure
                }
            }
        } finally {
            binding.fill(0)
            identifierCopy.fill(0)
            payloadCopy.fill(0)
        }
    }

    override suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor,
    ): ChangeFetchResult {
        val binding = expectedBinding.copyBytes()
        val cursorBytes = cursor.copyBytes()
        try {
            val page = provider.cancellableCall { fetchChanges(binding.toNSData(), cursorBytes.toNSData()) }
            return page.toChangeFetchResult()
        } finally {
            binding.fill(0)
            cursorBytes.fill(0)
        }
    }

    override suspend fun deleteZoneAndVerifyAbsent(expectedBinding: AccountBinding): ZoneDeleteResult {
        val binding = expectedBinding.copyBytes()
        try {
            return when (provider.cancellableCall { deleteZoneAndVerifyAbsent(binding.toNSData()) }) {
                IosCloudZoneDeleteStatus.DeletedAndAbsent -> {
                    ZoneDeleteResult.DeletedAndAbsent
                }

                IosCloudZoneDeleteStatus.Retryable -> {
                    ZoneDeleteResult.Retryable
                }

                IosCloudZoneDeleteStatus.AccountChanged -> {
                    ZoneDeleteResult.AccountChanged
                }

                IosCloudZoneDeleteStatus.UnknownOutcome -> {
                    ZoneDeleteResult.UnknownOutcome
                }
            }
        } finally {
            binding.fill(0)
        }
    }

    private fun IosCloudChangePage.toChangeFetchResult(): ChangeFetchResult {
        if (status != IosCloudChangeFetchStatus.Page) {
            return mapTerminalStatus()
        }
        val nextCursor = nextCursor?.copyBounded(MAILBOX_CURSOR_BYTES)?.let(MailboxCursor::fromBytes)
            ?: return ChangeFetchResult.IntegrityFailure
        val bundle = if (bundleIdentifier == null && bundlePayload == null) {
            null
        } else {
            readBundlePair() ?: return ChangeFetchResult.IntegrityFailure
        }
        return ChangeFetchResult.Page(
            ChangePage(
                bundle = bundle,
                moreChanges = moreChanges,
                nextCursor = nextCursor,
            ),
        )
    }

    private fun IosCloudChangePage.mapTerminalStatus(): ChangeFetchResult {
        return when (status) {
            IosCloudChangeFetchStatus.TokenExpired -> {
                ChangeFetchResult.TokenExpired
            }

            IosCloudChangeFetchStatus.ZoneMissing -> {
                ChangeFetchResult.ZoneMissing
            }

            IosCloudChangeFetchStatus.Retryable -> {
                ChangeFetchResult.Retryable
            }

            IosCloudChangeFetchStatus.AccountChanged -> {
                ChangeFetchResult.AccountChanged
            }

            IosCloudChangeFetchStatus.UnknownOutcome -> {
                ChangeFetchResult.UnknownOutcome
            }

            IosCloudChangeFetchStatus.IntegrityFailure -> {
                ChangeFetchResult.IntegrityFailure
            }

            IosCloudChangeFetchStatus.Page -> {
                ChangeFetchResult.IntegrityFailure
            }
        }
    }

    private fun IosCloudChangePage.readBundlePair(): MailboxBundle? {
        val identifier = bundleIdentifier?.copyExact(MAILBOX_BUNDLE_IDENTIFIER_BYTES) ?: return null
        val payload = bundlePayload?.copyBoundedRange(1, MAILBOX_BUNDLE_BYTES) ?: return null
        return MailboxBundle.fromParts(identifier, payload)
    }
}

private suspend fun <T> IosCloudKitMailboxProvider.cancellableCall(call: IosCloudKitMailboxProvider.() -> T): T {
    val provider = this
    // Like the JVM peer, hop off the calling thread: the provider blocks
    // in CloudKit waits, and the hop also makes cancellation deliverable
    // from any dispatcher.
    return withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { provider.cancelInflight() }
            val result = provider.call()
            continuation.resumeWith(Result.success(result))
        }
    }
}

@OptIn(BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.create(bytes = null, length = 0uL)
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(expectedSize: Int): ByteArray? {
    if (expectedSize < 0 || length != expectedSize.toULong()) {
        return null
    }
    return toByteArray()
}

private fun NSData.copyBounded(maximumSize: Int): ByteArray? {
    if (maximumSize < 0 || length > maximumSize.toULong()) {
        return null
    }
    return toByteArray()
}

private fun NSData.copyExact(expectedSize: Int): ByteArray? {
    return toByteArray(expectedSize)
}

private fun NSData.copyBoundedRange(
    minimumSize: Int,
    maximumSize: Int,
): ByteArray? {
    if (minimumSize < 0 || length < minimumSize.toULong() || length > maximumSize.toULong()) {
        return null
    }
    return toByteArray()
}

private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }

    return result
}
