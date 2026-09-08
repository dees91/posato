package app.posato.feature.sync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.BootstrapResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class SyncBootstrapUiState(
    private val bootstrap: AppleBootstrap,
    private val scope: CoroutineScope,
) {
    var running by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<BootstrapResult?>(null)
        private set
    var linked by mutableStateOf<Boolean?>(null)
        private set

    suspend fun refreshLinked() {
        linked = bootstrap.establishedContext() != null
    }

    fun sync() {
        if (running) {
            return
        }
        running = true
        scope.launch {
            try {
                val result = bootstrap.syncWithIcloud()
                outcome = result
                linked = result is BootstrapResult.Ready
            } finally {
                running = false
            }
        }
    }
}

@Composable
internal fun rememberSyncBootstrapUiState(bootstrap: AppleBootstrap): SyncBootstrapUiState {
    val scope = rememberCoroutineScope()
    return remember(bootstrap) { SyncBootstrapUiState(bootstrap, scope) }
}
