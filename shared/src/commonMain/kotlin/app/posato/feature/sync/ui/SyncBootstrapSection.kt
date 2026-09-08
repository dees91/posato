package app.posato.feature.sync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.sync.bootstrap.BootstrapResult
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_sync_with_icloud
import app.posato.generated.resources.sync_icloud_action_required
import app.posato.generated.resources.sync_icloud_description
import app.posato.generated.resources.sync_icloud_linked
import app.posato.generated.resources.sync_icloud_retryable
import app.posato.generated.resources.sync_icloud_running
import app.posato.generated.resources.sync_icloud_waiting_for_key
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SyncBootstrapSection(
    state: SyncBootstrapUiState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) {
        state.refreshLinked()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption(stringResource(statusText(state.running, state.linked, state.outcome)))
        PosatoButton(onClick = state::sync, enabled = !state.running) {
            Text(stringResource(Res.string.action_sync_with_icloud))
        }
    }
}

private fun statusText(
    running: Boolean,
    linked: Boolean?,
    outcome: BootstrapResult?,
): StringResource {
    return when {
        running -> Res.string.sync_icloud_running
        outcome != null -> outcome.message()
        linked == true -> Res.string.sync_icloud_linked
        else -> Res.string.sync_icloud_description
    }
}

private fun BootstrapResult.message(): StringResource {
    return when (this) {
        is BootstrapResult.Ready -> Res.string.sync_icloud_linked
        is BootstrapResult.WaitingForWorkspaceKey -> Res.string.sync_icloud_waiting_for_key
        is BootstrapResult.Retryable -> Res.string.sync_icloud_retryable
        is BootstrapResult.ActionRequired -> Res.string.sync_icloud_action_required
    }
}
