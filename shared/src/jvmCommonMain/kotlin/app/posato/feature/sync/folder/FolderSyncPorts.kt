package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorCreateResult
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapAccountPort
import app.posato.feature.sync.bootstrap.BootstrapCloudPort
import app.posato.feature.sync.bootstrap.BootstrapEncoding
import app.posato.feature.sync.bootstrap.BootstrapKeyPort
import app.posato.feature.sync.bootstrap.KEYCHAIN_ITEM_BYTES
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemCreateResult
import app.posato.feature.sync.bootstrap.KeyItemDeleteResult
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.bootstrap.WorkspaceKeyItem
import app.posato.feature.sync.bootstrap.ZoneFetchResult
import app.posato.feature.sync.bootstrap.ZoneSaveResult
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.InspectBundleHeaderResult
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.BundleSweepResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MAILBOX_BUNDLE_BYTES
import app.posato.feature.sync.mailbox.MailboxBundle
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.mailbox.MailboxPort
import app.posato.feature.sync.mailbox.RecordDeleteResult
import app.posato.feature.sync.mailbox.RemovalBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

internal const val WORKSPACE_DIRECTORY: String = "Posato"
internal const val ANCHOR_FILE: String = "workspace"
internal const val BUNDLE_DIRECTORY: String = "bundles"
internal const val PAIRING_DIRECTORY: String = "pairing"
private const val BUNDLE_SUFFIX: String = ".pbundle"
private const val KEY_SUFFIX: String = ".key"
private const val ANCHOR_VERSION: Int = 1
private const val CURSOR_DELIVERED: Byte = 1
private const val CURSOR_CAUGHT_UP: Byte = 2
private const val MAXIMUM_ANCHOR_BYTES: Int = 1_024
private const val MAXIMUM_STORED_KEY_BYTES: Int = 4_096
private val anchorMagic = "PSW1".encodeToByteArray()
private val bindingDomain = "app.posato.folder.binding.v1".encodeToByteArray()

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
 * per bundle the mailbox, and a local file the workspace key item.
 */
internal class FolderSyncPorts(
    private val chosenRoot: () -> Path?,
    private val localDirectory: Path,
    private val crypto: SyncCryptoProvider,
    private val protection: KeyItemProtection = KeyItemProtection.None,
) : BootstrapAccountPort,
    BootstrapCloudPort,
    BootstrapKeyPort,
    MailboxPort {
    private val codec = EncryptedBundleCodec(crypto)
    private val seenLock = Mutex()
    private var seenCache: SeenNames? = null

    override suspend fun resolveBinding(): BindingResolution {
        return io {
            val root = chosenRoot() ?: return@io BindingResolution.Unavailable
            bindingFor(root)?.let { BindingResolution.Available(it) } ?: BindingResolution.Undetermined
        }
    }

    internal suspend fun workspaceDirectory(expectedBinding: AccountBinding): Path? {
        return io { rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) }
    }

    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io ZoneFetchResult.AccountChanged
            if (Files.isDirectory(workspace)) ZoneFetchResult.Found else ZoneFetchResult.Missing
        }
    }

    override suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io ZoneSaveResult.AccountChanged
            try {
                if (Files.isDirectory(workspace)) {
                    ZoneSaveResult.AlreadyExists
                } else {
                    Files.createDirectories(workspace)
                    ZoneSaveResult.Created
                }
            } catch (_: IOException) {
                ZoneSaveResult.Retryable
            }
        }
    }

    override suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io AnchorReadResult.AccountChanged
            val file = workspace.resolve(ANCHOR_FILE)
            if (!Files.exists(file)) return@io AnchorReadResult.Missing
            val bytes = FolderFiles.readOrNull(file, MAXIMUM_ANCHOR_BYTES) ?: return@io AnchorReadResult.Retryable
            decodeAnchor(bytes)?.let { AnchorReadResult.Found(it) } ?: AnchorReadResult.IntegrityFailure
        }
    }

    override suspend fun createAnchor(
        expectedBinding: AccountBinding,
        anchor: WorkspaceAnchor,
    ): AnchorCreateResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io AnchorCreateResult.AccountChanged
            when (FolderFiles.writeExclusive(workspace.resolve(ANCHOR_FILE), encodeAnchor(anchor))) {
                ExclusiveWrite.CREATED -> AnchorCreateResult.Created
                ExclusiveWrite.EXISTS -> AnchorCreateResult.Conflict
                ExclusiveWrite.FAILED -> AnchorCreateResult.Retryable
            }
        }
    }

    override suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemReadResult {
        return io {
            val file = keyFile(account)
            if (!Files.exists(file)) return@io KeyItemReadResult.Missing
            val stored = FolderFiles.readOrNull(file, MAXIMUM_STORED_KEY_BYTES) ?: return@io KeyItemReadResult.Retryable
            val item = protection.open(stored)?.let(WorkspaceKeyItem::fromBytes) ?: return@io KeyItemReadResult.IntegrityFailure
            KeyItemReadResult.Found(item)
        }
    }

    override suspend fun createItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
        value: WorkspaceKeyItem,
    ): KeyItemCreateResult {
        return io {
            val item = value.copyBytes()
            try {
                if (item.size != KEYCHAIN_ITEM_BYTES) return@io KeyItemCreateResult.IntegrityFailure
                val sealed = protection.seal(item) ?: return@io KeyItemCreateResult.IntegrityFailure
                ensurePrivateDirectory(keyFile(account).parent)
                when (FolderFiles.writeExclusive(keyFile(account), sealed)) {
                    ExclusiveWrite.CREATED -> KeyItemCreateResult.Created.also { restrictToOwner(keyFile(account)) }
                    ExclusiveWrite.EXISTS -> KeyItemCreateResult.AlreadyExists
                    ExclusiveWrite.FAILED -> KeyItemCreateResult.Retryable
                }
            } finally {
                item.fill(0)
            }
        }
    }

    override suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount,
    ): KeyItemDeleteResult {
        return io {
            val file = keyFile(account)
            if (FolderFiles.deleteQuietly(file) && !Files.exists(file)) KeyItemDeleteResult.DeletedAndAbsent else KeyItemDeleteResult.Retryable
        }
    }

    override suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray,
    ): BundleSaveResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io BundleSaveResult.AccountChanged
            if (!Files.isRegularFile(workspace.resolve(ANCHOR_FILE))) return@io BundleSaveResult.IntegrityFailure
            if (identifier.size != SyncFormatLimits.IDENTIFIER_BYTES) return@io BundleSaveResult.IntegrityFailure
            val file = workspace.resolve(BUNDLE_DIRECTORY).resolve(identifier.toHex() + BUNDLE_SUFFIX)
            when (FolderFiles.writeExclusive(file, payload)) {
                ExclusiveWrite.CREATED -> BundleSaveResult.Saved
                ExclusiveWrite.FAILED -> BundleSaveResult.Retryable
                ExclusiveWrite.EXISTS -> when (val existing = FolderFiles.readOrNull(file, MAILBOX_BUNDLE_BYTES)) {
                    null -> BundleSaveResult.Retryable
                    else -> if (existing.contentEquals(payload)) BundleSaveResult.Identical else BundleSaveResult.Conflict
                }
            }
        }
    }

    override suspend fun fetchChanges(
        expectedBinding: AccountBinding,
        cursor: MailboxCursor,
    ): ChangeFetchResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io ChangeFetchResult.AccountChanged
            if (!Files.isRegularFile(workspace.resolve(ANCHOR_FILE))) return@io ChangeFetchResult.ZoneMissing
            val seen = seenLock.withLock { advanceSeen(expectedBinding, cursor) } ?: return@io ChangeFetchResult.Retryable
            val names = FolderFiles.names(workspace.resolve(BUNDLE_DIRECTORY)) ?: return@io ChangeFetchResult.Retryable
            val candidates = names.mapNotNull(::bundleIdentifierOrNull).filter { it !in seen }.sorted()
            for ((index, name) in candidates.withIndex()) {
                val bundle = readCheckedBundle(workspace, name) ?: continue
                val next = checkNotNull(MailboxCursor.fromBytes(byteArrayOf(CURSOR_DELIVERED) + name.encodeToByteArray()))
                return@io ChangeFetchResult.Page(ChangePage(bundle, index < candidates.lastIndex, next))
            }
            ChangeFetchResult.Page(ChangePage(null, false, checkNotNull(MailboxCursor.fromBytes(byteArrayOf(CURSOR_CAUGHT_UP)))))
        }
    }

    override suspend fun deleteWorkspaceRecords(
        expectedBinding: AccountBinding,
        budget: RemovalBudget,
    ): RecordDeleteResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io RecordDeleteResult.AccountChanged
            if (FolderFiles.deleteTree(workspace)) RecordDeleteResult.DeletedAndAbsent else RecordDeleteResult.Retryable
        }
    }

    override suspend fun sweepBundlesIfAnchorMissing(
        expectedBinding: AccountBinding,
        budget: RemovalBudget,
    ): BundleSweepResult {
        return io {
            val workspace = rootFor(expectedBinding)?.resolve(WORKSPACE_DIRECTORY) ?: return@io BundleSweepResult.AccountChanged
            when {
                Files.exists(workspace.resolve(ANCHOR_FILE)) -> BundleSweepResult.AnchorPresent
                FolderFiles.deleteTree(workspace.resolve(BUNDLE_DIRECTORY)) -> BundleSweepResult.Swept
                else -> BundleSweepResult.Retryable
            }
        }
    }

    override suspend fun clearRemovalResumeState(expectedBinding: AccountBinding) {
        io {
            seenLock.withLock {
                seenCache = null
                FolderFiles.deleteQuietly(seenFile(expectedBinding))
            }
        }
    }

    private fun readCheckedBundle(
        workspace: Path,
        name: String,
    ): MailboxBundle? {
        val bytes = FolderFiles.readOrNull(workspace.resolve(BUNDLE_DIRECTORY).resolve(name + BUNDLE_SUFFIX), MAILBOX_BUNDLE_BYTES)
        val bundle = bytes?.let(EncryptedBundle::fromBytes) ?: return null
        val header = (codec.inspectHeader(bundle) as? InspectBundleHeaderResult.Success)?.header ?: return null
        val expectedSize = SyncFormatLimits.HEADER_BYTES + header.ciphertextLength + SyncFormatLimits.SIGNATURE_BYTES
        val identifier = header.bundleId.value.copyBytes()
        return if (bytes.size == expectedSize && identifier.toHex() == name) MailboxBundle.fromParts(identifier, bytes) else null
    }

    private fun advanceSeen(
        binding: AccountBinding,
        cursor: MailboxCursor,
    ): Set<String>? {
        val file = seenFile(binding)
        val current = seenCache?.takeIf { it.file == file } ?: SeenNames(file, readSeen(file) ?: return null)
        val bytes = cursor.copyBytes()
        val names = when {
            cursor.isFirstPage() -> emptySet()
            bytes[0] == CURSOR_DELIVERED -> current.names + bytes.copyOfRange(1, bytes.size).decodeToString()
            else -> current.names
        }
        if (names != current.names || cursor.isFirstPage()) {
            ensurePrivateDirectory(localDirectory)
            if (!FolderFiles.replace(file, names.joinToString("\n").encodeToByteArray())) return null
        }
        seenCache = SeenNames(file, names)
        return names
    }

    private fun readSeen(file: Path): Set<String>? {
        if (!Files.exists(file)) return emptySet()
        val bytes = FolderFiles.readOrNull(file, Int.MAX_VALUE) ?: return null
        return bytes.decodeToString().split('\n').filter { it.isNotEmpty() }.toSet()
    }

    private fun seenFile(binding: AccountBinding): Path {
        return localDirectory.resolve("folder-seen-" + binding.copyBytes().copyOf(8).toHex())
    }

    private fun keyFile(account: KeyAccount): Path {
        return localDirectory.resolve("keys").resolve(account.text + KEY_SUFFIX)
    }

    private fun rootFor(expectedBinding: AccountBinding): Path? {
        val root = chosenRoot() ?: return null
        return root.takeIf { bindingFor(it) == expectedBinding }
    }

    private fun bindingFor(root: Path): AccountBinding? {
        val real = try {
            root.toRealPath().toString()
        } catch (_: IOException) {
            return null
        }
        return crypto.sha256(bindingDomain + byteArrayOf(0) + real.encodeToByteArray())?.let(AccountBinding::fromBytes)
    }

    private data class SeenNames(
        val file: Path,
        val names: Set<String>,
    )
}

private fun bundleIdentifierOrNull(fileName: String): String? {
    if (!fileName.endsWith(BUNDLE_SUFFIX)) return null
    val name = fileName.removeSuffix(BUNDLE_SUFFIX)
    return name.takeIf { it.length == SyncFormatLimits.IDENTIFIER_BYTES * 2 && it.hexToBytesOrNull() != null }
}

internal fun encodeAnchor(anchor: WorkspaceAnchor): ByteArray {
    val body = anchorMagic + byteArrayOf(ANCHOR_VERSION.toByte()) + anchor.workspaceId.value.copyBytes() +
        anchor.transportEpochId.value.copyBytes() + anchor.keyEpochId.value.copyBytes()
    val checksum = BootstrapEncoding.crc32(body, body.size)
    return body + ByteArray(4) { index -> (checksum ushr (24 - index * 8)).toByte() }
}

internal fun decodeAnchor(bytes: ByteArray): WorkspaceAnchor? {
    val identifiers = SyncFormatLimits.IDENTIFIER_BYTES
    val bodySize = anchorMagic.size + 1 + identifiers * 3
    if (bytes.size != bodySize + 4) return null
    if (!bytes.copyOf(anchorMagic.size).contentEquals(anchorMagic) || bytes[anchorMagic.size].toInt() != ANCHOR_VERSION) return null
    val checksum = BootstrapEncoding.crc32(bytes, bodySize)
    val stored = (0 until 4).fold(0) { value, index -> (value shl 8) or (bytes[bodySize + index].toInt() and 0xFF) }
    if (checksum != stored) return null
    val start = anchorMagic.size + 1
    val workspace = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(start, start + identifiers)) ?: return null
    val transport = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(start + identifiers, start + identifiers * 2)) ?: return null
    val key = SyncIdentifier.fromUuidV4Bytes(bytes.copyOfRange(start + identifiers * 2, start + identifiers * 3)) ?: return null
    return WorkspaceAnchor(WorkspaceId(workspace), TransportEpochId(transport), KeyEpochId(key))
}

internal fun ensurePrivateDirectory(directory: Path) {
    Files.createDirectories(directory)
    restrictToOwner(directory, "rwx------")
}

private fun restrictToOwner(
    path: Path,
    permissions: String = "rw-------",
) {
    try {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    } catch (_: UnsupportedOperationException) {
        return
    } catch (_: IOException) {
        return
    }
}

private suspend fun <T> io(block: suspend () -> T): T {
    return withContext(Dispatchers.IO) { block() }
}
