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
import app.posato.feature.sync.folder.PairingOffer
import app.posato.feature.sync.folder.PairingOfferResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class SyncBootstrapUiState(
    private val sync: AppleSync,
    private val scope: CoroutineScope,
    val folderControls: FolderSyncControls = AppleOnlySync,
) {
    val syncState = sync.state
    val folder = folderControls.folder

    var offer by mutableStateOf<PairingOffer?>(null)
        private set
    var offerFailed by mutableStateOf(false)
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

    fun closeCode() {
        offer = null
        offerFailed = false
        scope.launch { folderControls.dismissOffer() }
    }

    fun join(code: String) {
        if (running) return
        running = true
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
): SyncBootstrapUiState {
    val scope = rememberCoroutineScope()
    val state = remember(sync, folderControls) { SyncBootstrapUiState(sync, scope, folderControls) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = state::onForeground)
    return state
}
