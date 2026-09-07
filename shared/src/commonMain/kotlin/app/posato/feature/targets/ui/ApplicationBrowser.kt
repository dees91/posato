package app.posato.feature.targets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoEmptyState
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoItemMenu
import app.posato.core.designsystem.PosatoItemMenuAction
import app.posato.core.designsystem.PosatoItemRow
import app.posato.core.designsystem.PosatoItemSymbol
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
import app.posato.feature.targets.data.LocalApplicationMappingId
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
        PosatoSectionHeader(
            titleContent = { PosatoCaption(deviceLabel) },
            actionContent = { PosatoButton(onChoose, enabled = state.canChooseApplications()) { Text("Choose apps") } },
        )
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
                                    if (opaqueCount == 1) "1 application" else "$opaqueCount applications",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = { PosatoCaption("Review your private selection in the system picker.") },
                            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
                        )
                        PosatoButton(onClear, style = PosatoButtonStyle.Quiet, enabled = state.canClearApplicationMappings()) {
                            Text("Clear selection")
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
    PosatoItemRow(
        headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
        modifier = Modifier.padding(top = PosatoSpace.Tiny),
        trailingContent = {
            if (removable) {
                PosatoItemMenu("Actions for $name") { dismiss ->
                    PosatoItemMenuAction(
                        onClick = {
                            dismiss()
                            onRemove()
                        },
                        destructive = true,
                        leadingContent = { PosatoIcon(PosatoIcons.Remove, null) },
                    ) { Text("Remove") }
                }
            }
        },
    )
}

@Composable
private fun ApplicationBrowserStatus(
    state: TargetsUiState,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onActivate: () -> Unit
) {
    if (state.isApplicationMappingLoading) CircularProgressIndicator()
    state.applicationMappingSupportingText()?.let { PosatoCaption(it) }
    state.applicationMappingFailure?.let { failure ->
        PosatoNotice(tone = PosatoTone.Critical, actionContent = {
            if (state.hasApplicationMappingLoadFailure) PosatoButton(onRetry) { Text("Retry") }
            if (state.canClearApplicationMappings()) {
                PosatoButton(onClear, style = PosatoButtonStyle.Quiet) { Text("Clear selection") }
            }
        }) { Text(stringResource(failure.applicationMappingMessage())) }
    }
    if (state.applicationPolicyName == null && state.applicationMappings.isNotEmpty()) {
        PosatoNotice(tone = PosatoTone.Caution, actionContent = {
            PosatoButton(onActivate, enabled = state.canMutatePolicy()) { Text("Enable selected apps") }
        }) { Text("Your selection is saved on this device, but is not yet included in the application group.") }
    }
    if (state.applicationMappings.isEmpty() && !state.isApplicationMappingLoading && !state.hasApplicationMappingLoadFailure) {
        PosatoEmptyState("Make room beyond the browser.", description = "Choose the applications you would like a little space from.")
    }
}
