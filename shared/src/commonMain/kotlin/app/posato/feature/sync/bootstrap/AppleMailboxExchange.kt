package app.posato.feature.sync.bootstrap

import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.InspectBundleHeaderResult
import app.posato.feature.sync.data.OpaqueTransportProgress
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.RemoteAcceptanceResult
import app.posato.feature.sync.domain.RemoteTransportReceipt
import app.posato.feature.sync.domain.SyncWriter
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort

internal class AppleMailboxExchange(
    private val mailbox: MailboxPort,
    crypto: SyncCryptoProvider,
) {
    private val codec = EncryptedBundleCodec(crypto)

    suspend fun publishPending(
        workspace: EstablishedWorkspace,
        writer: SyncWriter,
    ): SyncStatus? {
        for (bundle in writer.pendingBundles) {
            val header = codec.inspectHeader(bundle) as? InspectBundleHeaderResult.Success ?: return SyncStatus.ACTION_REQUIRED
            when (mailbox.saveBundle(workspace.binding, header.header.bundleId.value.copyBytes(), bundle.copyBytes())) {
                BundleSaveResult.Saved, BundleSaveResult.Identical -> {
                    if (!writer.acknowledgePublication(bundle)) return SyncStatus.ACTION_REQUIRED
                }

                BundleSaveResult.Conflict, BundleSaveResult.IntegrityFailure -> {
                    return SyncStatus.ACTION_REQUIRED
                }

                BundleSaveResult.AccountChanged, BundleSaveResult.Retryable, BundleSaveResult.UnknownOutcome -> {
                    return SyncStatus.RETRYABLE
                }
            }
        }
        return null
    }

    suspend fun consume(
        workspace: EstablishedWorkspace,
        writer: SyncWriter,
        acceptedProgress: suspend () -> Unit = {},
    ): SyncStatus {
        var cursor = MailboxCursor.fromBytes(writer.transportProgress?.copyBytes() ?: byteArrayOf()) ?: return SyncStatus.ACTION_REQUIRED
        var restarted = false
        var outcome: SyncStatus? = null
        while (outcome == null) {
            when (val fetched = mailbox.fetchChanges(workspace.binding, cursor)) {
                is ChangeFetchResult.Page -> {
                    val page = fetched.page
                    outcome = consumePage(page, workspace, cursor, writer)
                    acceptedProgress()
                    cursor = page.nextCursor
                }

                ChangeFetchResult.TokenExpired -> {
                    if (restarted || cursor.isFirstPage()) outcome = SyncStatus.RETRYABLE
                    restarted = true
                    cursor = checkNotNull(MailboxCursor.fromBytes(byteArrayOf()))
                }

                ChangeFetchResult.ZoneMissing, ChangeFetchResult.IntegrityFailure -> {
                    outcome = SyncStatus.ACTION_REQUIRED
                }

                ChangeFetchResult.AccountChanged, ChangeFetchResult.Retryable, ChangeFetchResult.UnknownOutcome -> {
                    outcome = SyncStatus.RETRYABLE
                }
            }
        }
        return outcome
    }

    private suspend fun consumePage(
        page: ChangePage,
        workspace: EstablishedWorkspace,
        cursor: MailboxCursor,
        writer: SyncWriter
    ): SyncStatus? {
        return when {
            page.moreChanges && page.nextCursor == cursor -> SyncStatus.ACTION_REQUIRED
            !acceptPage(page, workspace, writer) -> SyncStatus.ACTION_REQUIRED
            !page.moreChanges -> SyncStatus.COMPLETED
            else -> null
        }
    }

    private suspend fun acceptPage(
        page: ChangePage,
        workspace: EstablishedWorkspace,
        writer: SyncWriter
    ): Boolean {
        val progress = OpaqueTransportProgress.fromBytes(page.nextCursor.copyBytes()) ?: return false
        val mailboxBundle = page.bundle ?: return writer.commitTransportProgress(progress)
        val bytes = mailboxBundle.copyPayload()
        val bundle = EncryptedBundle.fromBytes(bytes) ?: return false
        val header = codec.inspectHeader(bundle) as? InspectBundleHeaderResult.Success ?: return false
        if (header.header.context != workspace.context) return writer.commitTransportProgress(progress)
        if (!header.header.bundleId.value.copyBytes().contentEquals(mailboxBundle.copyIdentifier())) return false
        return when (writer.acceptRemote(bytes, RemoteTransportReceipt(progress, exactRefetchAvailable = false))) {
            is RemoteAcceptanceResult.Accepted, is RemoteAcceptanceResult.Staged, is RemoteAcceptanceResult.Duplicate -> true
            is RemoteAcceptanceResult.Failure -> false
        }
    }
}
