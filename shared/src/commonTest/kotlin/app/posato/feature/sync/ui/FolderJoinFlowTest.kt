package app.posato.feature.sync.ui

import app.posato.feature.sync.folder.FolderContents
import app.posato.feature.sync.folder.PairingAcceptResult
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderJoinFlowTest {
    private val scanned = PendingCode("CODE", CodeSource.SCANNED)
    private val typed = PendingCode("CODE", CodeSource.TYPED)

    @Test
    fun `given a code scanned before a folder is chosen then the code step is done and the folder step stays open`() {
        val steps = folderJoinSteps(folder = null, contents = FolderContents.UNKNOWN, code = scanned, error = null)
        assertEquals(FolderJoinSteps(folderChosen = false, code = CodeStep.Ready(CodeSource.SCANNED)), steps)
        assertEquals(false, steps.joinsNow)
    }

    @Test
    fun `given a scanned code when the folder is chosen then the device joins without another press`() {
        val steps = folderJoinSteps(folder = "Sync", contents = FolderContents.WORKSPACE, code = scanned, error = null)
        assertEquals(true, steps.joinsNow)
    }

    @Test
    fun `given an empty folder and no code then the second step creates the workspace`() {
        val steps = folderJoinSteps(folder = "Sync", contents = FolderContents.EMPTY, code = null, error = null)
        assertEquals(CodeStep.Create, steps.code)
    }

    @Test
    fun `given an empty folder and a code then it waits to join rather than create`() {
        val steps = folderJoinSteps(folder = "Sync", contents = FolderContents.EMPTY, code = typed, error = null)
        assertEquals(CodeStep.Ready(CodeSource.TYPED), steps.code)
        assertEquals(true, steps.joinsNow)
    }

    @Test
    fun `given a folder that holds a workspace and a failed code then the code field returns with the error`() {
        val steps = folderJoinSteps(folder = "Sync", contents = FolderContents.WORKSPACE, code = null, error = PairingAcceptResult.EXPIRED)
        assertEquals(CodeStep.Enter(PairingAcceptResult.EXPIRED), steps.code)
        assertEquals(false, steps.joinsNow)
    }

    @Test
    fun `given no folder and no code then the code step is not yet active`() {
        val steps = folderJoinSteps(folder = null, contents = FolderContents.UNKNOWN, code = null, error = null)
        assertEquals(FolderJoinSteps(folderChosen = false, code = CodeStep.NotYet), steps)
    }

    @Test
    fun `given an offer that arrives late when joining then it waits with a growing delay and joins once`() = runTest {
        val results = ArrayDeque(listOf(PairingAcceptResult.NOT_FOUND, PairingAcceptResult.UNAVAILABLE, PairingAcceptResult.JOINED))
        var calls = 0
        val waiting = mutableListOf<Boolean>()
        val joiner = FolderJoiner({ calls += 1; results.removeFirst() }, { currentTime })
        val result = joiner.join("CODE") { waiting += it }
        assertEquals(PairingAcceptResult.JOINED, result)
        assertEquals(3, calls)
        assertEquals(6_000L, currentTime)
        assertEquals(listOf(true, true, false), waiting)
    }

    @Test
    fun `given an expired code when joining then it stops at once with the error`() = runTest {
        var calls = 0
        val joiner = FolderJoiner({ calls += 1; PairingAcceptResult.EXPIRED }, { currentTime })
        assertEquals(PairingAcceptResult.EXPIRED, joiner.join("CODE"))
        assertEquals(1, calls)
    }

    @Test
    fun `given an offer that never arrives when joining then it gives up after ten minutes as not found`() = runTest {
        val waiting = mutableListOf<Boolean>()
        val joiner = FolderJoiner({ PairingAcceptResult.NOT_FOUND }, { currentTime })
        assertEquals(PairingAcceptResult.NOT_FOUND, joiner.join("CODE") { waiting += it })
        assertTrue(currentTime >= 10 * 60_000L, "gave up after $currentTime ms")
        assertTrue(currentTime < 11 * 60_000L, "gave up after $currentTime ms")
        assertEquals(false, waiting.last())
    }
}
