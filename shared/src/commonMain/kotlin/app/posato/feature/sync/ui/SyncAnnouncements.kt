package app.posato.feature.sync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.generated.resources.Res
import app.posato.generated.resources.sync_checking_key
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncAnnouncements(
    state: SyncBootstrapUiState,
    onAnnouncement: (String) -> Unit,
) {
    val snapshot by state.syncState.collectAsState()
    val announce by rememberUpdatedState(onAnnouncement)
    var trackingJoin by remember { mutableStateOf(false) }
    val message = stringResource(if (state.checking) Res.string.sync_checking_key else snapshot.status.message(snapshot.linked))
    LaunchedEffect(snapshot.status, state.checking, state.completedChecks) {
        if (snapshot.joinPending || state.checking) trackingJoin = true
        if (trackingJoin) {
            announce(message)
            val settled = when (snapshot.status) {
                SyncStatus.WAITING_FOR_KEY, SyncStatus.SYNCING -> false
                else -> true
            }
            if (settled && !snapshot.joinPending && !state.checking) {
                trackingJoin = false
            }
        }
    }
}
