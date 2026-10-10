package app.posato.feature.sync.folder

import app.posato.core.database.PosatoDatabase
import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.PersistedCandidate
import app.posato.feature.sync.bootstrap.SqlBootstrapStore
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.testContext
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.createLocalPolicyTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FolderWorkspaceTest {
    private class Device(
        root: Path,
        name: String,
    ) {
        private val database = createLocalPolicyTestDatabase("folder-$name-${System.nanoTime()}.db")
        private val driver = database.openDriver()
        private val posato = PosatoDatabase(driver)
        private val crypto = JdkSyncCryptoProvider()
        val folder = FolderSync(
            Files.createTempDirectory("posato-$name").toString(),
            crypto,
            null,
            NioFolderFileSystem,
            Dispatchers.IO,
            System::currentTimeMillis,
            pollsWhileRunning = false,
        ).also { it.choose(root.toString()) }
        val store = SqlBootstrapStore(posato, Dispatchers.IO)
        val sync = AppleSync(
            BootstrapCoordinator(folder.ports, folder.ports, folder.ports, store, crypto, folder.ports),
            SyncOperationCore(SqlSyncReplicaStore(posato, Dispatchers.IO), crypto, SyncWallClock { System.currentTimeMillis() }),
            folder.ports,
            folder.ports,
            store,
            SqlLocalTargetPolicyStore(posato, Dispatchers.IO),
            crypto,
            Dispatchers.IO,
        )

        suspend fun settle(): SyncStatus {
            delay(200)
            withTimeout(20_000) { while (sync.state.value.status == SyncStatus.SYNCING) delay(50) }
            return sync.state.value.status
        }

        suspend fun close() {
            sync.close()
            driver.close()
            database.delete()
        }
    }

    @Test
    fun `given two linked devices when one removes the workspace then the other needs attention and never re-creates it`() = runBlocking {
        val root = Files.createTempDirectory("posato-folder")
        val first = Device(root, "first")
        val second = Device(root, "second")
        try {
            first.sync.syncWithIcloud()
            assertEquals(SyncStatus.COMPLETED, first.settle())
            second.sync.syncWithIcloud()
            assertEquals(SyncStatus.WAITING_FOR_KEY, second.settle())
            val offer = assertIs<PairingOfferResult.Offered>(first.folder.offer()).offer
            assertEquals(PairingAcceptResult.JOINED, second.folder.accept(offer.code))
            second.sync.syncWithIcloud()
            assertEquals(SyncStatus.COMPLETED, second.settle())

            first.sync.removeWorkspace()
            assertEquals(false, Files.exists(root.resolve("Posato")))
            second.sync.syncNow()

            assertEquals(SyncStatus.ACTION_REQUIRED, second.settle())
            assertEquals(true, second.sync.state.value.linked)
            assertEquals(false, Files.exists(root.resolve("Posato")))
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a member removed its copy of a deleted workspace when it links to a new one in the folder then it waits for a code`() = runBlocking {
        val root = Files.createTempDirectory("posato-folder")
        val first = Device(root, "first")
        val second = Device(root, "second")
        try {
            first.sync.syncWithIcloud()
            first.settle()
            second.sync.syncWithIcloud()
            second.settle()
            second.folder.accept(assertIs<PairingOfferResult.Offered>(first.folder.offer()).offer.code)
            second.sync.syncWithIcloud()
            assertEquals(SyncStatus.COMPLETED, second.settle())
            first.sync.removeWorkspace()
            second.sync.syncNow()
            second.settle()
            second.sync.removeWorkspace()
            assertEquals(false, second.sync.state.value.linked)

            first.sync.syncWithIcloud()
            assertEquals(SyncStatus.COMPLETED, first.settle())
            second.sync.syncWithIcloud()

            assertEquals(SyncStatus.WAITING_FOR_KEY, second.settle())
            assertEquals(true, second.sync.state.value.joinPending)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `given a candidate left from a link attempt in another folder when the person chooses this folder and links then it links`() = runBlocking {
        val root = Files.createTempDirectory("posato-folder")
        val device = Device(Files.createTempDirectory("posato-other-folder"), "device")
        try {
            device.store.persistCandidate(
                PersistedCandidate(
                    testContext.workspaceId,
                    testContext.transportEpochId,
                    testContext.keyEpochId,
                    checkNotNull(AccountBinding.fromBytes(ByteArray(32) { 3 })),
                ),
            )
            val ui = SyncBootstrapUiState(device.sync, this, device.folder)
            ui.clearFolder()
            ui.chooseFolder(root.toString())
            delay(500)
            ui.sync()

            assertEquals(SyncStatus.COMPLETED, device.settle())
        } finally {
            device.close()
        }
    }
}
