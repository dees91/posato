package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AnchorCreateResult
import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.data.EncryptedBundleCodec
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.PrepareBundleResult
import app.posato.feature.sync.domain.AuthorId
import app.posato.feature.sync.domain.BundleId
import app.posato.feature.sync.domain.HybridLogicalClock
import app.posato.feature.sync.domain.SyncOperation
import app.posato.feature.sync.domain.SyncOperationPayload
import app.posato.feature.sync.domain.TransportKey
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.mailbox.BundleSaveResult
import app.posato.feature.sync.mailbox.ChangeFetchResult
import app.posato.feature.sync.mailbox.ChangePage
import app.posato.feature.sync.mailbox.MailboxCursor
import app.posato.feature.sync.testContext
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FolderSyncPortsTest {
    private val crypto = JdkSyncCryptoProvider()
    private val signingKey = checkNotNull(crypto.createSigningKey())
    private val transportKey = checkNotNull(TransportKey.fromBytes(ByteArray(32) { 9 }))

    private fun bundle(id: Int): Pair<ByteArray, ByteArray> {
        val operation = SyncOperation(
            operationId = BundleId(testIdentifier(id)),
            context = testContext,
            authorId = AuthorId(testIdentifier(100)),
            publicSigningKey = signingKey.publicKey,
            authorSequence = id.toLong(),
            clock = HybridLogicalClock(id.toLong(), 0),
            payload = SyncOperationPayload.PauseSetsEnabled,
        )
        val prepared = assertIs<PrepareBundleResult.Success>(
            EncryptedBundleCodec(crypto).prepare(operation, transportKey, signingKey, ByteArray(32) { id.toByte() }),
        )
        return testIdentifier(id).copyBytes() to prepared.bundle.copyBytes()
    }

    private suspend fun FolderSyncPorts.save(
        binding: AccountBinding,
        bundle: Pair<ByteArray, ByteArray>,
    ): BundleSaveResult {
        return saveBundle(binding, bundle.first, bundle.second)
    }

    private suspend fun FolderSyncPorts.establish(): AccountBinding {
        val binding = binding()
        saveZone(binding)
        createAnchor(binding, WorkspaceAnchor(testContext.workspaceId, testContext.transportEpochId, testContext.keyEpochId))
        return binding
    }

    private fun ports(
        root: Path,
        local: Path = Files.createTempDirectory("posato-local"),
    ): FolderSyncPorts {
        return FolderSyncPorts({ root }, local, crypto)
    }

    private suspend fun FolderSyncPorts.binding(): AccountBinding {
        return assertIs<BindingResolution.Available>(resolveBinding()).binding
    }

    private suspend fun FolderSyncPorts.readAll(
        binding: AccountBinding,
        start: MailboxCursor,
        commit: Boolean = true,
    ): Pair<List<ByteArray>, MailboxCursor> {
        val payloads = mutableListOf<ByteArray>()
        var cursor = start
        while (true) {
            val page: ChangePage = assertIs<ChangeFetchResult.Page>(fetchChanges(binding, cursor)).page
            page.bundle?.let { payloads += it.copyPayload() }
            if (!commit) return payloads to cursor
            cursor = page.nextCursor
            if (!page.moreChanges) return payloads to cursor
        }
    }

    private fun emptyCursor(): MailboxCursor {
        return checkNotNull(MailboxCursor.fromBytes(byteArrayOf()))
    }

    @Test
    fun `given no chosen folder when resolving the binding then the folder transport is unavailable`() = runTest {
        val ports = FolderSyncPorts({ null }, Files.createTempDirectory("posato-local"), crypto)

        assertEquals(BindingResolution.Unavailable, ports.resolveBinding())
    }

    @Test
    fun `given a written anchor when another device creates a different anchor then it conflicts and the first is read`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val first = ports(root)
        val second = ports(root)
        val anchor = WorkspaceAnchor(WorkspaceId(testIdentifier(1)), TransportEpochId(testIdentifier(2)), KeyEpochId(testIdentifier(3)))
        val other = WorkspaceAnchor(WorkspaceId(testIdentifier(4)), TransportEpochId(testIdentifier(5)), KeyEpochId(testIdentifier(6)))
        first.saveZone(first.binding())

        assertEquals(AnchorCreateResult.Created, first.createAnchor(first.binding(), anchor))
        assertEquals(AnchorCreateResult.Conflict, second.createAnchor(second.binding(), other))
        assertEquals(anchor, assertIs<AnchorReadResult.Found>(second.readAnchor(second.binding())).anchor)
    }

    @Test
    fun `given a damaged anchor file when read then it is an integrity failure`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val ports = ports(root)
        ports.saveZone(ports.binding())
        Files.write(root.resolve("Posato").resolve("workspace"), byteArrayOf(1, 2, 3))

        assertEquals(AnchorReadResult.IntegrityFailure, ports.readAnchor(ports.binding()))
    }

    @Test
    fun `given bundles from two devices and unrelated files when fetched then each bundle arrives once in name order and the rest is ignored`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val writer = ports(root)
        val reader = ports(root)
        writer.establish()
        assertEquals(BundleSaveResult.Saved, writer.save(writer.binding(), bundle(2)))
        assertEquals(BundleSaveResult.Saved, reader.save(reader.binding(), bundle(1)))
        val bundles = root.resolve("Posato").resolve("bundles")
        Files.write(bundles.resolve(".tmp-unfinished"), bundle(3).second)
        Files.write(bundles.resolve("${bundle(4).first.toHexText()} (conflicted copy).pbundle"), bundle(4).second)
        Files.write(bundles.resolve("notes.txt"), byteArrayOf(97))

        val (first, cursor) = reader.readAll(reader.binding(), emptyCursor())
        val (second, _) = reader.readAll(reader.binding(), cursor)

        assertEquals(listOf(bundle(1).second, bundle(2).second).map { it.toList() }, first.map { it.toList() })
        assertEquals(emptyList(), second)
    }

    @Test
    fun `given a truncated or misnamed bundle file when fetched then it is skipped without blocking and arrives once complete`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val ports = ports(root)
        val binding = ports.establish()
        ports.save(binding, bundle(2))
        val bundles = root.resolve("Posato").resolve("bundles")
        val partial = bundles.resolve("${bundle(1).first.toHexText()}.pbundle")
        Files.write(partial, bundle(1).second.copyOf(40))
        Files.write(bundles.resolve("${bundle(9).first.toHexText()}.pbundle"), bundle(3).second)

        val (first, cursor) = ports.readAll(binding, emptyCursor())
        Files.write(partial, bundle(1).second)
        val (second, _) = ports.readAll(binding, cursor)

        assertEquals(listOf(bundle(2).second.toList()), first.map { it.toList() })
        assertEquals(listOf(bundle(1).second.toList()), second.map { it.toList() })
    }

    @Test
    fun `given a delivered bundle that was not committed when fetched again then it is delivered again`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val ports = ports(root)
        val binding = ports.establish()
        ports.save(binding, bundle(3))

        val (uncommitted, cursor) = ports.readAll(binding, emptyCursor(), commit = false)
        val (again, _) = ports.readAll(binding, cursor)

        assertContentEquals(bundle(3).second, uncommitted.single())
        assertContentEquals(bundle(3).second, again.single())
    }

    @Test
    fun `given a committed history when a fresh replica starts from an empty cursor then everything is delivered again`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val local = Files.createTempDirectory("posato-local")
        val ports = ports(root, local)
        val binding = ports.establish()
        ports.save(binding, bundle(4))
        ports.readAll(binding, emptyCursor())

        val (fresh, _) = ports(root, local).readAll(binding, emptyCursor())

        assertContentEquals(bundle(4).second, fresh.single())
    }

    @Test
    fun `given an existing bundle id when saved with the same or other bytes then it is identical or a conflict`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val ports = ports(root)
        val binding = ports.establish()
        ports.save(binding, bundle(5))

        assertEquals(BundleSaveResult.Identical, ports.save(binding, bundle(5)))
        assertEquals(BundleSaveResult.Conflict, ports.saveBundle(binding, bundle(5).first, bundle(6).second))
    }

    @Test
    fun `given the workspace file was removed by another member when saving or fetching then nothing is written and the zone is missing`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val ports = ports(root)
        val binding = ports.establish()
        root.resolve("Posato").toFile().deleteRecursively()

        assertEquals(BundleSaveResult.IntegrityFailure, ports.save(binding, bundle(7)))
        assertEquals(ChangeFetchResult.ZoneMissing, ports.fetchChanges(binding, emptyCursor()))
        assertEquals(false, Files.exists(root.resolve("Posato")))
    }

    @Test
    fun `given another folder was chosen when a port is used with the old binding then the account changed`() = runTest {
        val first = Files.createTempDirectory("posato-folder")
        val second = Files.createTempDirectory("posato-folder")
        var root = first
        val ports = FolderSyncPorts({ root }, Files.createTempDirectory("posato-local"), crypto)
        val binding = ports.establish()
        root = second

        assertEquals(BundleSaveResult.AccountChanged, ports.save(binding, bundle(8)))
        assertEquals(ChangeFetchResult.AccountChanged, ports.fetchChanges(binding, emptyCursor()))
    }
}

private fun ByteArray.toHexText(): String {
    return joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
