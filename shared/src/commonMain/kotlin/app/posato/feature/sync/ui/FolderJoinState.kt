package app.posato.feature.sync.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.posato.feature.sync.folder.FolderContents
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.PairingAcceptResult
import app.posato.feature.sync.folder.PairingLinks
import kotlin.time.TimeSource

/** The join half of the folder section: the pending code, what the chosen folder holds, and the waiting join. */
@Stable
internal class FolderJoinState(
    private val controls: FolderSyncControls,
) {
    /** A code waiting to be used: scanned from a join link or typed. It joins once a folder is chosen too. */
    var pendingCode by mutableStateOf<PendingCode?>(null)
        private set

    /** What the chosen folder holds, read when it is chosen and when the section opens. */
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
     * aside, so a screen the system rebuilds (a rotation, a theme change) still has it.
     */
    fun capture(
        code: String,
        linked: Boolean,
    ) {
        if (linked) {
            PairingLinks.consume(code)
            return
        }
        if (pendingCode?.code == code) return
        error = null
        pendingCode = PendingCode(code, CodeSource.SCANNED)
    }

    fun enterCode(text: String) {
        val code = text.trim()
        if (code.isEmpty()) return
        error = null
        pendingCode = PendingCode(code, CodeSource.TYPED)
    }

    fun clearCode() {
        pendingCode?.let(::release)
        pendingCode = null
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

    suspend fun readFolder() {
        folderContents = controls.contents()
    }

    /**
     * Joins with [code], waiting while it has not reached the folder; [onJoined] links the device. Cancelled when
     * the section closes; a newer code replaces it.
     */
    suspend fun join(
        code: PendingCode,
        onJoined: suspend () -> Unit,
    ) {
        try {
            val result = joiner.join(code.code) { waitingForOffer = it }
            if (pendingCode !== code) return
            release(code)
            if (result != PairingAcceptResult.JOINED) {
                pendingCode = null
                error = result
                return
            }
            waitingForOffer = false
            onJoined()
            pendingCode = null
        } finally {
            waitingForOffer = false
        }
    }
}
