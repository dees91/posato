package app.posato.feature.sync.ui

import app.posato.feature.sync.domain.SyncIdentifier
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.folder.FolderChoiceResult
import app.posato.feature.sync.folder.FolderContents
import app.posato.feature.sync.folder.FolderReading
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.PairingAcceptResult
import app.posato.feature.sync.folder.PairingOfferResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FolderJoinStateTest {
    private val workspace = WorkspaceId(SyncIdentifier.fromExactBytes(ByteArray(16) { 7 })!!)

    private class Controls(
        var reading: FolderReading,
        val accepted: PairingAcceptResult = PairingAcceptResult.NOT_FOUND,
    ) : FolderSyncControls {
        override val supported = true
        override val icloudSupported = false
        override val folder: StateFlow<String?> = MutableStateFlow("Sync")

        override fun choose(path: String) = FolderChoiceResult.CHOSEN

        override fun clear() = Unit

        override suspend fun offer(): PairingOfferResult = PairingOfferResult.Unavailable

        override suspend fun dismissOffer() = Unit

        override suspend fun accept(code: String) = accepted

        override suspend fun read() = reading
    }

    @Test
    fun `given a folder that holds a workspace this device removed when it is read then it reads as the removed copy`() = runTest {
        val join = FolderJoinState(Controls(FolderReading(FolderContents.WORKSPACE, workspace)), FolderJoinMemory())
        join.readFolder(linked = false, isRemoved = { it == workspace }) {}
        assertEquals(FolderContents.REMOVED_COPY, join.folderContents)
    }

    @Test
    fun `given a folder whose key this device already holds but is not linked when it is read then it links once`() = runTest {
        var links = 0
        val join = FolderJoinState(Controls(FolderReading(FolderContents.WORKSPACE, workspace, keyHere = true)), FolderJoinMemory())
        join.readFolder(linked = false, isRemoved = { false }) { links += 1 }
        join.readFolder(linked = true, isRemoved = { false }) { links += 1 }
        assertEquals(1, links)
    }

    @Test
    fun `given a joined code when linking is cancelled because the device linked then the used code is forgotten`() = runTest {
        val memory = FolderJoinMemory()
        val join = FolderJoinState(Controls(FolderReading(FolderContents.WORKSPACE, workspace), PairingAcceptResult.JOINED), memory)
        join.enterCode("ABCDEF")
        assertFailsWith<CancellationException> {
            join.join(join.pendingCode!!) { throw CancellationException("the linked screen replaced the steps") }
        }
        assertNull(memory.pendingCode)
    }

    @Test
    fun `given a typed code and its draft when the screen is rebuilt then a new state still holds them`() {
        val memory = FolderJoinMemory()
        val first = FolderJoinState(Controls(FolderReading(FolderContents.UNKNOWN)), memory)
        first.draft = "ABC DEF"
        first.enterCode("ABCDEF")
        val rebuilt = FolderJoinState(Controls(FolderReading(FolderContents.UNKNOWN)), memory)
        assertEquals("ABCDEF", rebuilt.pendingCode?.code)
        assertEquals("ABC DEF", rebuilt.draft)
    }
}
