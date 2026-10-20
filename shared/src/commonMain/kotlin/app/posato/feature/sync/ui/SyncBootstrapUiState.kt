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
import app.posato.feature.sync.folder.PairingAcceptResult
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
    var joinResult by mutableStateOf<PairingAcceptResult?>(null)
        private set
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
        if (!folderRefused) scope.launch { sync.discardCandidate() }
    }

    fun browseFolder() {
        if (running) return
        scope.launch { folderControls.browse()?.let(::chooseFolder) }
    }

    fun clearFolder() {
        if (running || syncState.value.linked) return
        folderControls.clear()
        joinResult = null
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

    /** Uses a link's code once: joins at once while this device waits for one, and drops it once linked. */
    fun useLinkCode(code: String) {
        val snapshot = syncState.value
        if (snapshot.linked) {
            PairingLinks.consume(code)
        } else if (snapshot.joinPending && !running) {
            join(code)
            PairingLinks.consume(code)
        }
    }

    fun closeCode() {
        offer = null
        offerFailed = false
        linkCopied = false
        scope.launch { folderControls.dismissOffer() }
    }

    fun join(code: String) {
        if (running) return
        running = true
        // The previous attempt's message would otherwise read as this attempt's outcome until it finishes.
        joinResult = null
        scope.launch {
            try {
                val result = folderControls.accept(code)
                joinResult = result
                if (result == PairingAcceptResult.JOINED) {
                    checking = true
                    sync.syncWithIcloud()
                    completedChecks += 1
                }
            } finally {
                checking = false
                running = false
            }
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
