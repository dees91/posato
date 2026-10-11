package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorCreateResult
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapAccountPort
import app.posato.feature.sync.bootstrap.BootstrapCloudPort
import app.posato.feature.sync.bootstrap.BootstrapKeyPort
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.bootstrap.ZoneFetchResult
import app.posato.feature.sync.bootstrap.ZoneSaveResult
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.mailbox.MailboxPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal const val WORKSPACE_DIRECTORY: String = "Posato"
internal const val ANCHOR_FILE: String = "workspace"
internal const val BUNDLE_DIRECTORY: String = "bundles"
internal const val PAIRING_DIRECTORY: String = "pairing"
internal const val MAXIMUM_ANCHOR_BYTES: Int = 1_024

/** Wraps the stored workspace key item; Android seals it with a Keystore key. */
internal interface KeyItemProtection {
    fun seal(item: ByteArray): ByteArray?

    fun open(stored: ByteArray): ByteArray?

    object None : KeyItemProtection {
        override fun seal(item: ByteArray): ByteArray {
            return item.copyOf()
        }

        override fun open(stored: ByteArray): ByteArray {
            return stored.copyOf()
        }
    }
}

/**
 * The folder transport of ADR 0010 behind the same ports as CloudKit: the
 * `Posato` directory is the zone, its `workspace` file the anchor, one file
 * per bundle the mailbox ([FolderMailbox]), and a local file the workspace
 * key item ([FolderKeyStore]).
 */
internal class FolderSyncPorts(
    chosenRoot: () -> String?,
    localDirectory: String,
    crypto: SyncCryptoProvider,
    private val files: FolderFileSystem,
    private val ioDispatcher: CoroutineDispatcher,
    protection: KeyItemProtection = KeyItemProtection.None,
) : BootstrapAccountPort,
    BootstrapCloudPort,
    BootstrapKeyPort by FolderKeyStore(localDirectory, files, ioDispatcher, protection),
    MailboxPort by FolderMailbox(FolderLocation(chosenRoot, crypto, files), localDirectory, crypto, files, ioDispatcher) {
    private val location = FolderLocation(chosenRoot, crypto, files)

    private suspend fun <T> io(block: suspend () -> T): T {
        return withContext(ioDispatcher) { block() }
    }

    override suspend fun resolveBinding(): BindingResolution {
        return io { location.resolve() }
    }

    internal suspend fun workspaceDirectory(expectedBinding: AccountBinding): String? {
        return io { location.workspaceFor(expectedBinding) }
    }

    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        return io {
            val workspace = location.workspaceFor(expectedBinding) ?: return@io ZoneFetchResult.AccountChanged
            if (files.isDirectory(workspace)) ZoneFetchResult.Found else ZoneFetchResult.Missing
        }
    }

    override suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult {
        return io {
            val workspace = location.workspaceFor(expectedBinding) ?: return@io ZoneSaveResult.AccountChanged
            when {
                files.isDirectory(workspace) -> ZoneSaveResult.AlreadyExists
                files.createDirectories(workspace) -> ZoneSaveResult.Created
                else -> ZoneSaveResult.Retryable
            }
        }
    }

    override suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult {
        return io {
            val workspace = location.workspaceFor(expectedBinding) ?: return@io AnchorReadResult.AccountChanged
            when (val read = files.readFile(workspace.child(ANCHOR_FILE), MAXIMUM_ANCHOR_BYTES)) {
                FileRead.Missing -> AnchorReadResult.Missing
                FileRead.Failed -> AnchorReadResult.Retryable
                is FileRead.Found -> decodeAnchor(read.bytes)?.let { AnchorReadResult.Found(it) } ?: AnchorReadResult.IntegrityFailure
            }
        }
    }

    override suspend fun createAnchor(
        expectedBinding: AccountBinding,
        anchor: WorkspaceAnchor,
    ): AnchorCreateResult {
        return io {
            val workspace = location.workspaceFor(expectedBinding) ?: return@io AnchorCreateResult.AccountChanged
            when (files.writeExclusive(workspace.child(ANCHOR_FILE), encodeAnchor(anchor))) {
                ExclusiveWrite.CREATED -> AnchorCreateResult.Created
                ExclusiveWrite.EXISTS -> AnchorCreateResult.Conflict
                ExclusiveWrite.FAILED -> AnchorCreateResult.Retryable
            }
        }
    }
}

/** The chosen folder and the account binding derived from its canonical path; it keeps no state. */
internal class FolderLocation(
    private val chosenRoot: () -> String?,
    private val crypto: SyncCryptoProvider,
    private val files: FolderFileSystem,
) {
    fun resolve(): BindingResolution {
        val root = chosenRoot() ?: return BindingResolution.Unavailable
        return bindingFor(root)?.let { BindingResolution.Available(it) } ?: BindingResolution.Undetermined
    }

    /** The `Posato` directory of the chosen folder, or null when the folder is no longer the one [expectedBinding] names. */
    fun workspaceFor(expectedBinding: AccountBinding): String? {
        val root = chosenRoot() ?: return null
        return root.takeIf { bindingFor(it) == expectedBinding }?.child(WORKSPACE_DIRECTORY)
    }

    private fun bindingFor(root: String): AccountBinding? {
        val real = files.canonical(root) ?: return null
        return crypto.sha256(bindingDomain + byteArrayOf(0) + real.encodeToByteArray())?.let(AccountBinding::fromBytes)
    }

    private companion object {
        val bindingDomain = "app.posato.folder.binding.v1".encodeToByteArray()
    }
}
