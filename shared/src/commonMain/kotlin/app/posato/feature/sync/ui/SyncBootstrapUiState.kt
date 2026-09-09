package app.posato.feature.sync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.posato.feature.sync.bootstrap.AppleSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class SyncBootstrapUiState(
    private val sync: AppleSync,
    private val scope: CoroutineScope,
) {
    val syncState = sync.state
    var running by mutableStateOf(false)
        private set

    fun sync() {
        if (running) return
        running = true
        scope.launch {
            try {
                if (syncState.value.linked) sync.syncNow() else sync.syncWithIcloud()
            } finally {
                running = false
            }
        }
    }

    fun removeWorkspace() {
        if (running) return
        running = true
        scope.launch {
            try {
                sync.removeWorkspace()
            } finally {
                running = false
            }
        }
    }

    fun onForeground() {
        scope.launch { sync.onForeground() }
    }
}

@Composable
internal fun rememberSyncBootstrapUiState(sync: AppleSync): SyncBootstrapUiState {
    val scope = rememberCoroutineScope()
    val state = remember(sync) { SyncBootstrapUiState(sync, scope) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = state::onForeground)
    return state
}
