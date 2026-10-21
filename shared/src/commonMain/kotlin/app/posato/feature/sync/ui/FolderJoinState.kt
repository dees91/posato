package app.posato.feature.sync.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.folder.FolderContents
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.PairingAcceptResult
import app.posato.feature.sync.folder.PairingLinks
import kotlin.time.TimeSource

/**
 * The pending code and the typed draft, kept for the app's lifetime, so a screen the system rebuilds (a rotation, a
 * theme change) still has them. The app keeps one; a test passes its own.
 */
internal class FolderJoinMemory {
    var pendingCode by mutableStateOf<PendingCode?>(null)
    var draft by mutableStateOf("")

    companion object {
        val app = FolderJoinMemory()
    }
}

/** The join half of the folder section: the pending code, what the chosen folder holds, and the waiting join. */
@Stable
internal class FolderJoinState(
    private val controls: FolderSyncControls,
    private val memory: FolderJoinMemory = FolderJoinMemory.app,
) {
    /** A code waiting to be used: scanned from a join link or typed. It joins once a folder is chosen too. */
    val pendingCode: PendingCode?
        get() {
            return memory.pendingCode
        }

    /** What the code field holds while a person types. */
    var draft: String
        get() {
            return memory.draft
        }
        set(value) {
            memory.draft = value
        }

    /** What the chosen folder holds, read when it is chosen, when the section opens, and while it offers to create. */
    var folderContents by mutableStateOf(FolderContents.UNKNOWN)
        private set

    /** The code has not reached this copy of the folder yet, so the join keeps checking. */
    var waitingForOffer by mutableStateOf(false)
        private set

    /** A code that cannot work, shown under the code field. */
    var error by mutableStateOf<PairingAcceptResult?>(null)
        private set

    private val started = TimeSource.Monotonic.markNow()
    private val joiner = FolderJoiner(controls::accept) { started.elapsedNow().inWholeMilliseconds }

    /**
     * Takes a join link's code; a linked device has no use for it. The link stays until the code is used or set
     * aside, so a rebuilt screen still has it.
     */
    fun capture(
        code: String,
        linked: Boolean,
    ) {
        if (linked) {
            PairingLinks.consume(code)
            return
        }
        if (memory.pendingCode?.code == code) return
        error = null
        memory.pendingCode = PendingCode(code, CodeSource.SCANNED)
    }

    fun enterCode(text: String) {
        val code = text.trim()
        if (code.isEmpty()) return
        error = null
        memory.pendingCode = PendingCode(code, CodeSource.TYPED)
    }

    fun clearCode() {
        memory.pendingCode?.let(::release)
        memory.pendingCode = null
        error = null
    }

    private fun release(code: PendingCode) {
        if (code.source == CodeSource.SCANNED) PairingLinks.consume(code.code)
    }

    /** Forgets what the previous folder held, when the folder changes. */
    fun forgetFolder() {
        folderContents = FolderContents.UNKNOWN
        error = null
    }

    /**
     * Reads the chosen folder. A copy of a workspace this device removed reads as such, so no code is used on it. A
     * workspace whose key this device already holds while nothing is linked (a join that stored the key, then Posato
     * stopped before linking) links once through [onLinkable].
     */
    suspend fun readFolder(
        linked: Boolean,
        isRemoved: suspend (WorkspaceId) -> Boolean,
        onLinkable: suspend () -> Unit,
    ) {
        val reading = controls.read()
        val removed = reading.workspaceId?.let { isRemoved(it) } == true
        folderContents = if (removed) FolderContents.REMOVED_COPY else reading.contents
        if (!linked && !removed && reading.contents == FolderContents.WORKSPACE && reading.keyHere) onLinkable()
    }

    /**
     * Joins with [code], waiting while it has not reached the folder; [onJoined] links the device. Cancelled when
     * the section closes or the app goes to the background; a newer code replaces it.
     */
    suspend fun join(
        code: PendingCode,
        onJoined: suspend () -> Unit,
    ) {
        try {
            val result = joiner.join(code.code) { waitingForOffer = it }
            if (memory.pendingCode !== code) return
            release(code)
            if (result != PairingAcceptResult.JOINED) {
                memory.pendingCode = null
                error = result
                return
            }
            waitingForOffer = false
            memory.draft = ""
            onJoined()
            memory.pendingCode = null
        } finally {
            waitingForOffer = false
        }
    }
}
