package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.InspectBundleHeaderResult
import app.posato.feature.sync.data.SyncCryptoProvider
import app.posato.feature.sync.domain.EncryptedBundle
import app.posato.feature.sync.domain.SyncFormatLimits
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CURSOR_DELIVERED: Byte = 1
private const val CURSOR_CAUGHT_UP: Byte = 2
private const val MAXIMUM_SEEN_BYTES: Int = 16 * 1_024 * 1_024
private const val SEEN_FILE_BINDING_BYTES: Int = 8

/**
 * The mailbox over `Posato/bundles`: one ADR 0006 bundle per file, fetched in name order. A reader keeps the names it
 * committed in a local seen file; the cursor names the last delivered bundle and the next fetch marks it seen, so a
 * crash before the commit delivers it again.
 */
internal class FolderMailbox(
    private val location: FolderLocation,
    private val localDirectory: String,
    crypto: SyncCryptoProvider,
    private val files: FolderFileSystem,
    private val ioDispatcher: CoroutineDispatcher,
) : MailboxPort {
    private val codec = EncryptedBundleCodec(crypto)
    private val seenLock = Mutex()
    private var seenCache: SeenNames? = null

    private suspend fun <T> io(block: suspend () -> T): T {
        return withContext(ioDispatcher) { block() }
    }

    override suspend fun saveBundle(
        expectedBinding: AccountBinding,
        identifier: ByteArray,
        payload: ByteArray,
    ): BundleSaveResult {
        return io {
            val workspace = location.workspaceFor(expectedBinding) ?: return@io BundleSaveResult.AccountChanged
            if (!files.isFile(workspace.child(ANCHOR_FILE))) return@io BundleSaveResult.IntegrityFailure
            if (identifier.size != SyncFormatLimits.IDENTIFIER_BYTES) return@io BundleSaveResult.IntegrityFailure
            val file = workspace.child(BUNDLE_DIRECTORY).child(identifier.toHex() + BUNDLE_SUFFIX)
            when (files.writeExclusive(file, payload)) {
                ExclusiveWrite.CREATED -> BundleSaveResult.Saved

                ExclusiveWrite.FAILED -> BundleSaveResult.Retryable

                ExclusiveWrite.EXISTS -> when (val existing = files.read(file, MAILBOX_BUNDLE_BYTES)) {
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
            val workspace = location.workspaceFor(expectedBinding) ?: return@io ChangeFetchResult.AccountChanged
            if (!files.isFile(workspace.child(ANCHOR_FILE))) return@io ChangeFetchResult.ZoneMissing
            val seen = seenLock.withLock { advanceSeen(expectedBinding, cursor) } ?: return@io ChangeFetchResult.Retryable
            val names = files.names(workspace.child(BUNDLE_DIRECTORY)) ?: return@io ChangeFetchResult.Retryable
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
            val workspace = location.workspaceFor(expectedBinding) ?: return@io RecordDeleteResult.AccountChanged
            if (files.deleteTree(workspace)) RecordDeleteResult.DeletedAndAbsent else RecordDeleteResult.Retryable
        }
    }

    override suspend fun sweepBundlesIfAnchorMissing(
        expectedBinding: AccountBinding,
        budget: RemovalBudget,
    ): BundleSweepResult {
        return io {
            val workspace = location.workspaceFor(expectedBinding) ?: return@io BundleSweepResult.AccountChanged
            when (files.readFile(workspace.child(ANCHOR_FILE), MAXIMUM_ANCHOR_BYTES)) {
                is FileRead.Found -> BundleSweepResult.AnchorPresent
                FileRead.Failed -> BundleSweepResult.Retryable
                FileRead.Missing -> if (files.deleteTree(workspace.child(BUNDLE_DIRECTORY))) BundleSweepResult.Swept else BundleSweepResult.Retryable
            }
        }
    }

    override suspend fun clearRemovalResumeState(expectedBinding: AccountBinding) {
        io {
            seenLock.withLock {
                seenCache = null
                files.delete(seenFile(expectedBinding))
            }
        }
    }

    private fun readCheckedBundle(
        workspace: String,
        name: String,
    ): MailboxBundle? {
        val bytes = files.read(workspace.child(BUNDLE_DIRECTORY).child(name + BUNDLE_SUFFIX), MAILBOX_BUNDLE_BYTES)
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
            if (!files.createPrivateDirectories(localDirectory)) return null
            if (!files.replace(file, names.joinToString("\n").encodeToByteArray())) return null
        }
        seenCache = SeenNames(file, names)
        return names
    }

    private fun readSeen(file: String): Set<String>? {
        if (!files.isFile(file)) return emptySet()
        val bytes = files.read(file, MAXIMUM_SEEN_BYTES) ?: return null
        return bytes.decodeToString().split('\n').filter { it.isNotEmpty() }.toSet()
    }

    private fun seenFile(binding: AccountBinding): String {
        return localDirectory.child("folder-seen-" + binding.copyBytes().copyOf(SEEN_FILE_BINDING_BYTES).toHex())
    }

    private data class SeenNames(
        val file: String,
        val names: Set<String>,
    )
}
