package app.posato.feature.sync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.folder.AppleOnlySync
import app.posato.feature.sync.folder.FolderChoiceResult
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.PairingLinks
import app.posato.feature.sync.folder.PairingOffer
import app.posato.feature.sync.folder.PairingOfferResult
import app.posato.feature.sync.folder.pairingLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class SyncBootstrapUiState(
    private val sync: AppleSync,
    private val scope: CoroutineScope,
    val folderControls: FolderSyncControls = AppleOnlySync,
    /** A time of the last sync as the device writes times: the clock time today, the date and time before. */
    val formatLastSync: (Long) -> String,
) {
    val syncState = sync.state
    val folder = folderControls.folder

    /** A code from a join link the system opened Posato with, until it is used. */
    val linkCode = PairingLinks.code

    var offer by mutableStateOf<PairingOffer?>(null)
        private set
    var offerFailed by mutableStateOf(false)
        private set
    var linkCopied by mutableStateOf(false)
        private set
    val joining = FolderJoinState(folderControls)

    var folderRefused by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set

    var checking by mutableStateOf(false)
        private set
    var completedChecks by mutableLongStateOf(0)
        private set

    fun sync() {
        if (running || syncState.value.checkingJoin) return
        running = true
        checking = syncState.value.joinPending
        scope.launch {
            try {
                if (syncState.value.linked) sync.syncNow() else sync.syncWithIcloud()
                if (checking) completedChecks += 1
                // A folder may now hold a workspace (created here, or a removed copy refused) that the steps read from.
                if (!syncState.value.linked && folder.value != null) readFolder()
            } finally {
                checking = false
                running = false
            }
        }
    }

    fun removeWorkspace() {
        if (running) return
        running = true
        scope.launch {
            try {
                if (offer != null) closeCode()
                sync.removeWorkspace()
            } finally {
                running = false
            }
        }
    }

    /** A folder may be chosen only while nothing is linked, so a workspace never changes transport under it. */
    fun chooseFolder(path: String) {
        if (running || syncState.value.linked) return
        folderRefused = folderControls.choose(path) != FolderChoiceResult.CHOSEN
        if (folderRefused) return
        joining.forgetFolder()
        scope.launch {
            sync.discardCandidate()
            readFolder()
        }
    }

    fun browseFolder() {
        if (running) return
        scope.launch { folderControls.browse()?.let(::chooseFolder) }
    }

    fun clearFolder() {
        if (running || syncState.value.linked) return
        folderControls.clear()
        joining.forgetFolder()
        folderRefused = false
        scope.launch { sync.discardCandidate() }
    }

    fun showCode() {
        if (running) return
        running = true
        scope.launch {
            try {
                val result = folderControls.offer()
                offer = (result as? PairingOfferResult.Offered)?.offer
                offerFailed = offer == null
            } finally {
                running = false
            }
        }
    }

    /** Passes the shown code on as a link: the share sheet on a phone, the clipboard on a computer. */
    fun shareLink() {
        val code = offer?.code ?: return
        scope.launch {
            val sharing = folderControls.linkSharing
            linkCopied = sharing.share(pairingLink(code)) && !sharing.usesShareSheet
        }
    }

    fun closeCode() {
        offer = null
        offerFailed = false
        linkCopied = false
        scope.launch { folderControls.dismissOffer() }
    }

    /** Joins with a pending code once both steps are done, then links this device with the received key. */
    suspend fun join(code: PendingCode) {
        joining.join(code, linkWithStoredKey)
    }

    /** Reads the chosen folder; one whose key this device already holds while nothing is linked links at once. */
    suspend fun readFolder() {
        joining.readFolder(syncState.value.linked, sync.removedWorkspaces::contains, linkWithStoredKey)
    }

    /** Links with the key a join stored; shown as a check, like a press of Sync. */
    private val linkWithStoredKey: suspend () -> Unit = {
        running = true
        checking = true
        try {
            sync.syncWithIcloud(waitForRunning = true)
            completedChecks += 1
        } finally {
            checking = false
            running = false
        }
    }

    fun onForeground() {
        scope.launch { sync.onForeground() }
    }
}

@Composable
internal fun rememberSyncBootstrapUiState(
    sync: AppleSync,
    folderControls: FolderSyncControls = AppleOnlySync,
    formatLastSync: (Long) -> String,
): SyncBootstrapUiState {
    val scope = rememberCoroutineScope()
    val state = remember(sync, folderControls) { SyncBootstrapUiState(sync, scope, folderControls, formatLastSync) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = state::onForeground)
    return state
}
