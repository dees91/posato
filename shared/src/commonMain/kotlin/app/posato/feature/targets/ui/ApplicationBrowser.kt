package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.posato.core.designsystem.PosatoActivityIndicator
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoEmptyState
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoItemMenu
import app.posato.core.designsystem.PosatoItemRow
import app.posato.core.designsystem.PosatoItemSymbol
import app.posato.core.designsystem.PosatoMenuItem
import app.posato.core.designsystem.PosatoMenuSymbol
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwipeAction
import app.posato.core.designsystem.PosatoSwipeRow
import app.posato.core.designsystem.PosatoTone
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
import app.posato.feature.targets.data.LocalApplicationMappingId
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_clear_applications
import app.posato.generated.resources.action_remove
import app.posato.generated.resources.action_retry
import app.posato.generated.resources.count_applications
import app.posato.generated.resources.schedule_actions_for
import app.posato.generated.resources.targets_apps_empty_body
import app.posato.generated.resources.targets_apps_empty_title
import app.posato.generated.resources.targets_apps_private
import app.posato.generated.resources.targets_choose_apps
import app.posato.generated.resources.targets_enable_apps
import app.posato.generated.resources.targets_selection_saved
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationBrowser(
    state: TargetsUiState,
    scroll: LazyListState,
    deviceLabel: String,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    onRemove: (LocalApplicationMappingId) -> Unit,
    onRetry: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large)) {
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            PosatoButton(
                onChoose,
                enabled = state.canChooseApplications(),
                controlId = "targets_choose_apps",
            ) { Text(stringResource(Res.string.targets_choose_apps)) }
            PosatoCaption(deviceLabel)
        }
        PosatoDivider()
        LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                ApplicationBrowserStatus(state, onRetry, onClear, onActivate)
            }
            if (!state.hasApplicationMappingLoadFailure) {
                val opaqueCount = state.applicationMappings.count { it.display is LocalApplicationMappingDisplay.Opaque }
                if (opaqueCount > 0) {
                    item {
                        PosatoItemRow(
                            headlineContent = {
                                Text(
                                    pluralStringResource(Res.plurals.count_applications, opaqueCount, opaqueCount),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = { PosatoCaption(stringResource(Res.string.targets_apps_private)) },
                            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
                        )
                        PosatoButton(
                            onClear,
                            style = PosatoButtonStyle.Quiet,
                            enabled = state.canClearApplicationMappings(),
                            controlId = "action_clear_applications",
                        ) {
                            Text(stringResource(Res.string.action_clear_applications))
                        }
                    }
                }
                items(
                    state.applicationMappings.filter {
                        it.display is LocalApplicationMappingDisplay.Named
                    },
                    key = { it.id.canonicalValue },
                ) { mapping ->
                    val display = mapping.display
                    if (display is LocalApplicationMappingDisplay.Named) {
                        ApplicationRow(display.value, state.canRemoveApplicationMapping(mapping.id)) { onRemove(mapping.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationRow(
    name: String,
    removable: Boolean,
    onRemove: () -> Unit
) {
    val row = @Composable { rowActions: List<CustomAccessibilityAction> ->
        PosatoItemRow(
            headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
            modifier = Modifier.padding(top = PosatoSpace.Tiny).semantics(mergeDescendants = true) {
                if (rowActions.isNotEmpty()) customActions = rowActions
            },
            trailingContent = {
                if (removable && !platformUsesCupertinoChrome) {
                    PosatoItemMenu(
                        stringResource(Res.string.schedule_actions_for, name),
                        listOf(
                            PosatoMenuItem(
                                stringResource(Res.string.action_remove),
                                onRemove,
                                PosatoMenuSymbol.Remove,
                                destructive = true,
                                controlId = "action_remove",
                            ),
                        ),
                    )
                }
            },
        )
    }
    // On iOS a removable app slides out Remove under a left swipe instead of carrying a menu button.
    if (platformUsesCupertinoChrome && removable) {
        PosatoSwipeRow(
            listOf(PosatoSwipeAction(stringResource(Res.string.action_remove), onRemove, destructive = true, controlId = "action_remove")),
        ) { rowActions -> row(rowActions) }
    } else {
        row(emptyList())
    }
}

@Composable
private fun ApplicationBrowserStatus(
    state: TargetsUiState,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onActivate: () -> Unit
) {
    Column(Modifier.padding(top = PosatoSpace.Large), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        if (state.isApplicationMappingLoading) PosatoActivityIndicator()
        state.applicationMappingSupportingText()?.let { PosatoCaption(it) }
        state.applicationMappingFailure?.let { failure ->
            PosatoNotice(tone = PosatoTone.Critical, actionContent = {
                if (state.hasApplicationMappingLoadFailure) {
                    PosatoButton(
                        onRetry,
                        controlId = "action_retry",
                    ) { Text(stringResource(Res.string.action_retry)) }
                }
                if (state.canClearApplicationMappings()) {
                    PosatoButton(
                        onClear,
                        style = PosatoButtonStyle.Quiet,
                        controlId = "action_clear_applications",
                    ) { Text(stringResource(Res.string.action_clear_applications)) }
                }
            }) { Text(stringResource(failure.applicationMappingMessage())) }
        }
        if (state.applicationPolicyName == null && state.applicationMappings.isNotEmpty()) {
            PosatoNotice(tone = PosatoTone.Caution, actionContent = {
                PosatoButton(
                    onActivate,
                    enabled = state.canMutatePolicy(),
                    controlId = "targets_enable_apps",
                ) { Text(stringResource(Res.string.targets_enable_apps)) }
            }) { Text(stringResource(Res.string.targets_selection_saved)) }
        }
        if (state.applicationMappings.isEmpty() && !state.isApplicationMappingLoading && !state.hasApplicationMappingLoadFailure) {
            PosatoEmptyState(stringResource(Res.string.targets_apps_empty_title), description = stringResource(Res.string.targets_apps_empty_body))
        }
    }
}
