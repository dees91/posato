package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.PrototypeControl
import app.posato.prototype.PrototypeUiState
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoChoiceTile
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoToggleButton
import app.posato.prototype.designsystem.workbench.PrototypeInspector
import app.posato.prototype.designsystem.workbench.PrototypeStateFact
import app.posato.prototype.designsystem.workbench.PrototypeWalkthroughStep
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeMoment
import app.posato.prototype.model.PrototypeScenario
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.PrototypeSyncStatus
import app.posato.prototype.model.RecoveryAction
import app.posato.prototype.model.SessionAction
import app.posato.prototype.model.SetupAction
import app.posato.prototype.model.SyncAction
import app.posato.prototype.model.WorkspaceKey
import app.posato.prototype.model.prototypeFreePlayGroups

@Composable
internal fun PrototypeMoments(
    state: PrototypeUiState,
    onAction: (PrototypeAction) -> Unit,
    onControl: (PrototypeControl) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        Text("Jump to a moment", style = MaterialTheme.typography.titleMedium)
        PosatoActionRow {
            PrototypeMoment.entries.forEach { moment ->
                PosatoButton(onClick = { onControl(PrototypeControl.Moment(moment)) }, style = PosatoButtonStyle.Secondary) { Text(moment.label) }
            }
        }
        Text("Mock external events", style = MaterialTheme.typography.titleMedium)
        PrototypeExternalEvents(state, onAction)
    }
}

@Composable
private fun PrototypeExternalEvents(
    state: PrototypeUiState,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val prototype = state.prototype
    val actions = buildList {
        if (prototype.surface == PrototypeSurface.WorkspaceCheck) addAll(listOf(SetupAction.DiscoverEmptyWorkspace, SetupAction.DiscoverDelayedKey))
        if (prototype.workspace.key == WorkspaceKey.Waiting) add(SetupAction.KeyArrived)
        if (prototype.sync.status == PrototypeSyncStatus.Syncing && prototype.surface != PrototypeSurface.WorkspaceCheck) {
            addAll(listOf(SyncAction.Succeed, SyncAction.Fail))
        }
        if (prototype.session.active) addAll(listOf(SessionAction.OpenBlocked, SessionAction.Expire))
        if (prototype.onboardingComplete) addAll(listOf(RecoveryAction.RevokePermission, RecoveryAction.RemoveMapping))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        if (actions.isEmpty()) PosatoCaption("No external result is pending. Choose a moment or begin a walkthrough.")
        actions.forEach { action ->
            PosatoButton(modifier = Modifier.fillMaxWidth(), onClick = {
                onAction(action)
            }, style = PosatoButtonStyle.Secondary) { Text(action.label) }
        }
    }
}

@Composable
internal fun PrototypeWalkthrough(
    state: PrototypeUiState,
    onControl: (PrototypeControl) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoActionRow {
            PrototypeScenario.entries.forEach { scenario ->
                PosatoToggleButton(state.scenario == scenario, onClick = { onControl(PrototypeControl.Scenario(scenario)) }) { Text(scenario.label) }
            }
        }
        val scenario = state.scenario
        if (scenario == null) {
            PosatoCaption("Choose a guided scenario. You can advance with the app’s controls or one step at a time here.")
        } else {
            PosatoCaption(scenario.description)
            Text("${state.progress} of ${scenario.steps().size} steps complete", style = MaterialTheme.typography.titleMedium)
            scenario.steps().forEachIndexed { index, action ->
                PrototypeWalkthroughStep(
                    number = (index + 1).toString(),
                    onClick = { onControl(PrototypeControl.GuidedStep(index)) },
                    enabled = index == state.progress,
                    completed = index < state.progress,
                    supportingContent = if (scenario.expectsBlocked(index)) {
                        { PosatoCaption("Intentionally blocked · valid state must remain unchanged.") }
                    } else {
                        null
                    },
                ) {
                    Text(action.label)
                }
            }
            PosatoButton(onClick = {
                onControl(PrototypeControl.Scenario(scenario))
            }, style = PosatoButtonStyle.Secondary) { Text("Restart walkthrough") }
        }
    }
}

@Composable
internal fun PrototypeFreePlayControls(
    onControl: (PrototypeControl) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        PosatoCaption("Explore independently. Free play prepares missing prerequisites; normal app actions remain strict.")
        prototypeFreePlayGroups.forEach { group ->
            Text(group.title, style = MaterialTheme.typography.titleMedium)
            group.actions.forEach { action ->
                PosatoChoiceTile(modifier = Modifier.fillMaxWidth(), selected = false, onClick = { onControl(PrototypeControl.FreePlay(action)) }) {
                    Text(action.label)
                }
            }
        }
    }
}

@Composable
internal fun PrototypeStateInspector(
    state: PrototypeUiState,
    modifier: Modifier = Modifier
) {
    val prototype = state.prototype
    PrototypeInspector(modifier = modifier, title = "Current state", outcomeContent = { Text(prototype.outcome.message) }) {
        PrototypeStateFact("Device", prototype.platform.label)
        PrototypeStateFact("Screen", prototype.surface.name)
        PrototypeStateFact("Workspace", "${prototype.workspace.status} · key ${prototype.workspace.key}")
        PrototypeStateFact("Permission", prototype.permission.name)
        PrototypeStateFact("Session", if (prototype.session.active) "Active until ${prototype.session.endsAt}" else "Inactive")
        PrototypeStateFact("Websites", prototype.policy.domains.joinToString().ifEmpty { "None" })
        PrototypeStateFact("Local apps", prototype.localApplications().joinToString().ifEmpty { "None" })
        PrototypeStateFact("Synchronization", prototypeSyncMessage(prototype))
        PrototypeStateFact("Pending local work", prototype.sync.pendingWork.toString())
        PrototypeStateFact("Storage", "In memory only. Restart resets this installation.")
    }
}
