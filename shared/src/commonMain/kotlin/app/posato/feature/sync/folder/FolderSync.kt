package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorCreateResult
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapAccountPort
import app.posato.feature.sync.bootstrap.BootstrapCloudPort
import app.posato.feature.sync.bootstrap.BootstrapKeyPort
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemDeleteResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import app.posato.feature.sync.bootstrap.ZoneFetchResult
import app.posato.feature.sync.bootstrap.ZoneSaveResult
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.RecordDeleteResult
import app.posato.feature.sync.mailbox.RemovalBudget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MILLIS: Long = 60_000L
private const val CHOICE_FILE: String = "sync-folder"
private const val CHOICE_BYTES: Int = 16_384

/** The CloudKit ports of an Apple host, used while no folder is chosen. */
internal class AppleSyncPorts(
    val account: BootstrapAccountPort,
    val cloud: BootstrapCloudPort,
    val keys: BootstrapKeyPort,
    val mailbox: MailboxPort,
)

/**
 * The folder chosen on this device, kept in one small local file. A host
 * that needs an access grant, such as an iOS security-scoped bookmark, stores
 * its own token and resolves it in [FolderAccess].
 */
internal class FolderChoice(
    private val localDirectory: String,
    private val files: FolderFileSystem,
    private val access: FolderAccess,
) {
    private val file = localDirectory.child(CHOICE_FILE)
    private var stored: String? = read()

    fun root(): String? {
        return stored?.let(access::resolve)
    }

    fun displayed(): String? {
        return stored?.let(access::display)
    }

    fun choose(token: String): Boolean {
        if (!files.createPrivateDirectories(localDirectory)) return false
        if (!files.replace(file, token.encodeToByteArray())) return false
        stored = token
        return true
    }

    fun clear() {
        files.delete(file)
        stored = null
    }

    private fun read(): String? {
        val text = files.read(file, CHOICE_BYTES)?.decodeToString()?.trim() ?: return null
        return text.takeIf { it.isNotEmpty() }
    }
}

/** How a stored folder choice becomes a readable path on this host. */
internal interface FolderAccess {
    fun resolve(token: String): String?

    fun display(token: String): String

    /** Turns what the person typed or picked into the token to store, or null when it is not usable. */
    fun accept(choice: String): String?

    /** Paths are the tokens on hosts that read any folder the person can. */
    class Paths(
        private val files: FolderFileSystem,
    ) : FolderAccess {
        override fun resolve(token: String): String {
            return token
        }

        override fun display(token: String): String {
            return token
        }

        override fun accept(choice: String): String? {
            val path = choice.trim()
            if (!path.startsWith("/") || !files.isWritableDirectory(path)) return null
            return files.canonical(path)
        }
    }
}

/**
 * The ports the coordinator and the exchange use: the folder's while a folder
 * is chosen, otherwise CloudKit's on an Apple host. The section offers a
 * folder only while no workspace is linked, so the choice never changes under
 * an established workspace.
 */
internal class SelectableSyncPorts(
    private val choice: FolderChoice,
    private val folder: FolderSyncPorts,
    private val apple: AppleSyncPorts?,
) : BootstrapAccountPort,
    BootstrapCloudPort,
    BootstrapKeyPort,
    MailboxPort {
    private val account: BootstrapAccountPort
        get() = if (apple == null || choice.root() != null) folder else apple.account
    private val cloud: BootstrapCloudPort
        get() = if (apple == null || choice.root() != null) folder else apple.cloud
    private val keys: BootstrapKeyPort
        get() = if (apple == null || choice.root() != null) folder else apple.keys
    private val mailbox: MailboxPort
        get() = if (apple == null || choice.root() != null) folder else apple.mailbox

    override suspend fun resolveBinding(): BindingResolution {
        return account.resolveBinding()
    }

    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        return cloud.fetchZone(expectedBinding)
    }

    override suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult {
        return cloud.saveZone(expectedBinding)
    }

    override suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult {
        return cloud.readAnchor(expectedBinding)
    }

    override suspend fun createAnchor(
        expectedBinding: AccountBinding,
        anchor: WorkspaceAnchor,
    ): AnchorCreateResult {
        return cloud.createAnchor(expectedBinding, anchor)
    }

    override suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemReadResult {
        return keys.readItem(expectedBinding, account)
    }

    override suspend fun createItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
        value: WorkspaceKeyItem,
    ): KeyItemCreateResult {
        return keys.createItem(expectedBinding, account, value)
    }

    override suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemDeleteResult {
        return keys.deleteItemAndVerifyAbsent(expectedBinding, account)
    }

    override suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray,
    ): BundleSaveResult {
        return mailbox.saveBundle(expectedBinding, identifier, payload)
    }

    override suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor,
    ): ChangeFetchResult {
        return mailbox.fetchChanges(expectedBinding, cursor)
    }

    override suspend fun deleteWorkspaceRecords(
        expectedBinding: AccountBinding,
        budget: RemovalBudget,
    ): RecordDeleteResult {
        return mailbox.deleteWorkspaceRecords(expectedBinding, budget)
    }

    override suspend fun sweepBundlesIfAnchorMissing(
        expectedBinding: AccountBinding,
        budget: RemovalBudget,
    ): BundleSweepResult {
        return mailbox.sweepBundlesIfAnchorMissing(expectedBinding, budget)
    }

    override suspend fun clearRemovalResumeState(expectedBinding: AccountBinding) {
        mailbox.clearRemovalResumeState(expectedBinding)
    }
}

/** Wires the folder transport for one host: its ports, the section controls, and the poll. */
internal class FolderSync(
    localDirectory: String,
    crypto: SyncCryptoProvider,
    apple: AppleSyncPorts?,
    files: FolderFileSystem,
    ioDispatcher: CoroutineDispatcher,
    now: () -> Long,
    protection: KeyItemProtection = KeyItemProtection.None,
    private val access: FolderAccess = FolderAccess.Paths(files),
    private val browser: (suspend () -> String?)? = null,
    private val pollsWhileRunning: Boolean = true,
    override val acceptsTypedPath: Boolean = true,
) : FolderSyncControls {
    private val choice = FolderChoice(localDirectory, files, access)
    private val folderPorts = FolderSyncPorts(choice::root, localDirectory, crypto, files, ioDispatcher, protection)
    private val pairing = FolderPairing(folderPorts, crypto, files, ioDispatcher, now)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val displayed = MutableStateFlow(choice.displayed())
    val ports = SelectableSyncPorts(choice, folderPorts, apple)

    override val supported: Boolean = true
    override val icloudSupported: Boolean = apple != null
    override val folder: StateFlow<String?> = displayed.asStateFlow()
    override val canBrowse: Boolean = browser != null

    override suspend fun browse(): String? {
        return browser?.invoke()
    }

    override fun choose(path: String): FolderChoiceResult {
        val token = access.accept(path) ?: return FolderChoiceResult.NOT_A_FOLDER
        if (!choice.choose(token)) return FolderChoiceResult.NOT_A_FOLDER
        displayed.value = choice.displayed()
        return FolderChoiceResult.CHOSEN
    }

    override fun clear() {
        choice.clear()
        displayed.value = null
    }

    override suspend fun offer(): PairingOfferResult {
        return pairing.offer()
    }

    override suspend fun dismissOffer() {
        pairing.dismiss()
    }

    override suspend fun accept(code: String): PairingAcceptResult {
        return pairing.accept(code)
    }

    /** A folder gives no change events, so a linked folder workspace is read once a minute (ADR 0010). */
    fun startPolling(sync: AppleSync) {
        scope.launch {
            pairing.sweepStaleOffers()
            while (pollsWhileRunning) {
                if (choice.root() != null && sync.state.value.linked) sync.syncNow()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }
}
