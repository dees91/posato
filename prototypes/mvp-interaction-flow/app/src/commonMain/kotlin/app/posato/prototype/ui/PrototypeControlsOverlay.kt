package app.posato.prototype.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.posato.prototype.PrototypeControl
import app.posato.prototype.PrototypeUiState
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDivider
import app.posato.prototype.designsystem.PosatoSectionHeader
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoToggleButton
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypePlatform

@Composable
internal fun PrototypeControlsOverlay(
    state: PrototypeUiState,
    options: PrototypeDisplayOptions,
    onOptions: (PrototypeDisplayOptions) -> Unit,
    onAction: (PrototypeAction) -> Unit,
    onControl: (PrototypeControl) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.prototype.platform == PrototypePlatform.IPhone) {
        ModalBottomSheet(
            modifier = modifier,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PrototypeControlsPanel(state, options, onOptions, onAction, onControl, onDismiss)
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val panelFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { panelFocus.requestFocus() }
            Box(
                modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    val dismissKey = event.key == Key.Escape ||
                        (event.key == Key.P && event.isMetaPressed && event.isShiftPressed)
                    val dismiss = dismissKey && event.type == KeyEventType.KeyDown
                    if (dismiss) onDismiss()
                    dismiss
                }.focusRequester(panelFocus).focusable(),
            ) {
                Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
                Surface(
                    modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = PrototypeUiTokens.OverlayWidth).fillMaxWidth().fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    PrototypeControlsPanel(state, options, onOptions, onAction, onControl, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun PrototypeControlsPanel(
    state: PrototypeUiState,
    options: PrototypeDisplayOptions,
    onOptions: (PrototypeDisplayOptions) -> Unit,
    onAction: (PrototypeAction) -> Unit,
    onControl: (PrototypeControl) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(PosatoSpace.Section),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
    ) {
        PosatoSectionHeader(
            titleContent = { Text("Prototype controls", style = MaterialTheme.typography.titleLarge) },
            actionContent = { PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text("Close controls") } },
        )
        PosatoCaption("Mock data · manual clock 17:45 · this ${state.prototype.platform.label} only")
        PosatoActionRow {
            PrototypeControlTab.entries.forEach { tab ->
                PosatoToggleButton(options.tab == tab, onClick = { onOptions(options.copy(tab = tab)) }) { Text(tab.label) }
            }
        }
        when (options.tab) {
            PrototypeControlTab.Moments -> PrototypeMoments(state, onAction, onControl)
            PrototypeControlTab.Walkthrough -> PrototypeWalkthrough(state, onControl)
            PrototypeControlTab.FreePlay -> PrototypeFreePlayControls(onControl)
            PrototypeControlTab.State -> PrototypeStateInspector(state)
        }
        PosatoDivider()
        PrototypeAppearanceControls(options, onOptions)
        PosatoCaption("Long-press posato to reopen. On Mac: ⌘⇧P. Closing controls keeps your current draft and session.")
    }
}

@Composable
private fun PrototypeAppearanceControls(
    options: PrototypeDisplayOptions,
    onOptions: (PrototypeDisplayOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        PosatoActionRow {
            PrototypeAppearance.entries.forEach { appearance ->
                PosatoToggleButton(options.appearance == appearance, onClick = {
                    onOptions(options.copy(appearance = appearance))
                }) { Text(appearance.name) }
            }
        }
        PosatoActionRow {
            PosatoToggleButton(options.highContrast, onClick = {
                onOptions(options.copy(highContrast = !options.highContrast))
            }) { Text("More contrast") }
            PosatoToggleButton(options.enlargedText, onClick = {
                onOptions(options.copy(enlargedText = !options.enlargedText))
            }) { Text("Larger text") }
        }
    }
}
