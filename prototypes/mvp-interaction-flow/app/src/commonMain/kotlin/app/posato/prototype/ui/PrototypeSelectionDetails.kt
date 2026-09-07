package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoSectionHeader
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeState

@Composable
internal fun PrototypeSelectionDetails(
    state: PrototypeState,
    initialSection: PrototypeItemSection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    if (state.platform == PrototypePlatform.IPhone) {
        ModalBottomSheet(
            modifier = modifier,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CompositionLocalProvider(LocalDensity provides density) { PrototypeSelectionPanel(state, initialSection, onDismiss) }
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = modifier.padding(PosatoSpace.Section).widthIn(max = PosatoSize.Content).fillMaxWidth().fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                CompositionLocalProvider(LocalDensity provides density) { PrototypeSelectionPanel(state, initialSection, onDismiss) }
            }
        }
    }
}

@Composable
private fun PrototypeSelectionPanel(
    state: PrototypeState,
    initialSection: PrototypeItemSection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(PosatoSpace.Section),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
    ) {
        PosatoSectionHeader(
            titleContent = { Text("Selected items", style = MaterialTheme.typography.headlineSmall) },
            actionContent = {
                PosatoButton(modifier = Modifier.focusRequester(closeFocus), onClick = onDismiss, style = PosatoButtonStyle.Quiet) {
                    Text("Close list")
                }
            },
        )
        PrototypeItemBrowser(state = state, onAction = null, browser = rememberPrototypeItemBrowserState(initialSection))
    }
}
