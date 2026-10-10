package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AnchorReadResult
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.testContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A shared folder whose metadata cache still reports deleted entries, as a virtiofs guest does after another writer removes them. */
private object StaleMetadataFileSystem : FolderFileSystem by NioFolderFileSystem {
    override fun isDirectory(path: String): Boolean {
        return path.contains("/Posato") || NioFolderFileSystem.isDirectory(path)
    }

    override fun isFile(path: String): Boolean {
        return path.endsWith("/workspace") || NioFolderFileSystem.isFile(path)
    }
}

class StaleFolderTest {
    @Test
    fun `given metadata that still reports a removed workspace file when the anchor is read then it is missing rather than retryable`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val crypto = JdkSyncCryptoProvider()
        val writer = nioPorts({ root }, Files.createTempDirectory("posato-local"), crypto)
        val writerBinding = assertIs<BindingResolution.Available>(writer.resolveBinding()).binding
        writer.saveZone(writerBinding)
        writer.createAnchor(writerBinding, WorkspaceAnchor(testContext.workspaceId, testContext.transportEpochId, testContext.keyEpochId))
        root.resolve("Posato").toFile().deleteRecursively()
        val stale = FolderSyncPorts(
            { root.toString() },
            Files.createTempDirectory("posato-local").toString(),
            crypto,
            StaleMetadataFileSystem,
            Dispatchers.IO,
        )
        val binding = assertIs<BindingResolution.Available>(stale.resolveBinding()).binding

        assertEquals(AnchorReadResult.Missing, stale.readAnchor(binding))
    }
}
