package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoItemRow
import app.posato.core.designsystem.PosatoItemSymbol
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoSearchField
import app.posato.core.designsystem.PosatoSection
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.platformNavigationPlacement
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
import app.posato.feature.targets.ui.TargetsCategory
import app.posato.feature.targets.ui.TargetsCategoryTabs

@Composable
internal fun SessionSelectionSummary(
    state: SessionUiState,
    deviceLabel: String
) {
    var details by remember { mutableStateOf<TargetsCategory?>(null) }
    val domains = state.displayDomains()
    val applicationCount = state.displayApplicationCount()
    PosatoSection(titleContent = { Text("Selected items") }) {
        PosatoDisclosureRow(
            onClick = { details = TargetsCategory.WEBSITES },
            headlineContent = { Text(if (domains.size == 1) "1 website" else "${domains.size} websites") },
            supportingContent = { PosatoCaption("Exact domains · view all") },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
        )
        PosatoDisclosureRow(
            onClick = { details = TargetsCategory.APPLICATIONS },
            headlineContent = {
                Text(applicationCount?.let { if (it == 1) "1 application" else "$it applications" } ?: "Applications unavailable")
            },
            supportingContent = {
                PosatoCaption(
                    if (state.review.applicationGroupName == null && state.applicationMappings.isNotEmpty()) {
                        "Selection saved · not enabled in the application group"
                    } else {
                        "$deviceLabel · view all"
                    },
                )
            },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
        )
    }
    details?.let { category ->
        if (platformNavigationPlacement() == PosatoNavigationPlacement.Bottom) {
            ModalBottomSheet(onDismissRequest = { details = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                SessionSelectionPanel(state, category, onDismiss = { details = null })
            }
        } else {
            Dialog(onDismissRequest = { details = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(
                    modifier = Modifier.padding(PosatoSpace.Section).widthIn(max = PosatoSize.Content).fillMaxWidth().fillMaxHeight(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) { SessionSelectionPanel(state, category, onDismiss = { details = null }) }
            }
        }
    }
}

@Composable
private fun SessionSelectionPanel(
    state: SessionUiState,
    initialCategory: TargetsCategory,
    onDismiss: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var category by remember { mutableStateOf(initialCategory) }
    val search = remember { TextFieldState() }
    val scroll = rememberLazyListState()
    LaunchedEffect(category, search.text.toString()) { scroll.scrollToItem(0) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().imePadding().padding(PosatoSpace.Section), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(
            titleContent = { Text("Selected items", style = MaterialTheme.typography.headlineSmall) },
            actionContent = { PosatoButton(onDismiss, Modifier.focusRequester(focus), style = PosatoButtonStyle.Quiet) { Text("Close list") } },
        )
        TargetsCategoryTabs(category, state.displayDomains().size, state.applicationMappings.size) {
            focusManager.clearFocus()
            category = it
        }
        val values = when (category) {
            TargetsCategory.WEBSITES -> state.displayDomains()

            TargetsCategory.APPLICATIONS -> state.applicationMappings.mapNotNull { mapping ->
                when (val display = mapping.display) {
                    is LocalApplicationMappingDisplay.Named -> display.value
                    LocalApplicationMappingDisplay.Opaque -> null
                }
            }
        }
        if (category == TargetsCategory.WEBSITES) PosatoSearchField(search, "Search websites")
        val opaqueCount = state.applicationMappings.count { it.display is LocalApplicationMappingDisplay.Opaque }
        if (category == TargetsCategory.APPLICATIONS && opaqueCount > 0) {
            PosatoCaption("$opaqueCount applications selected privately. Review them in the system picker from Paused items.")
        }
        val visible = if (category ==
            TargetsCategory.WEBSITES
        ) {
            values.filter { it.contains(search.text.toString().trim(), ignoreCase = true) }
        } else {
            values
        }
        PosatoCaption(if (category == TargetsCategory.WEBSITES) "${visible.size} of ${values.size} websites" else "On this device only")
        PosatoDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll) {
            if (visible.isEmpty()) item { PosatoCaption(if (values.isEmpty()) "No named items to show." else "No matching websites") }
            items(visible) { value ->
                PosatoItemRow(headlineContent = { Text(value) }, leadingContent = {
                    PosatoItemSymbol { PosatoIcon(if (category == TargetsCategory.WEBSITES) PosatoIcons.Globe else PosatoIcons.Apps, null) }
                })
            }
        }
    }
}
