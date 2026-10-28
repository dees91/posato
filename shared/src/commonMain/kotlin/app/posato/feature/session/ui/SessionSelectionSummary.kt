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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoBarButton
import app.posato.core.designsystem.PosatoBarContentTop
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoDivider
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoItemRow
import app.posato.core.designsystem.PosatoItemSymbol
import app.posato.core.designsystem.PosatoNavigationBar
import app.posato.core.designsystem.PosatoSearchField
import app.posato.core.designsystem.PosatoSection
import app.posato.core.designsystem.PosatoSectionHeader
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTypography
import app.posato.core.designsystem.barColumn
import app.posato.core.designsystem.platformDevice
import app.posato.feature.targets.data.LocalApplicationMappingDisplay
import app.posato.feature.targets.ui.TargetsCategory
import app.posato.feature.targets.ui.TargetsCategoryTabs
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_edit
import app.posato.generated.resources.count_applications
import app.posato.generated.resources.count_websites
import app.posato.generated.resources.count_websites_of
import app.posato.generated.resources.session_add_or_edit_websites
import app.posato.generated.resources.session_apps_in_set
import app.posato.generated.resources.session_apps_unavailable
import app.posato.generated.resources.session_close_list
import app.posato.generated.resources.session_current_apps
import app.posato.generated.resources.session_device_view_all
import app.posato.generated.resources.session_filter_list
import app.posato.generated.resources.session_filter_only
import app.posato.generated.resources.session_filter_websites
import app.posato.generated.resources.session_items_at_start
import app.posato.generated.resources.session_manage_apps
import app.posato.generated.resources.session_no_apps_show
import app.posato.generated.resources.session_no_filter_match
import app.posato.generated.resources.session_no_websites_show
import app.posato.generated.resources.session_private_apps
import app.posato.generated.resources.session_selected_items
import app.posato.generated.resources.session_selection_saved_group
import app.posato.generated.resources.session_websites_at_start
import app.posato.generated.resources.session_websites_at_start_title
import app.posato.generated.resources.session_websites_view
import app.posato.generated.resources.targets_on_device_only
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionSelectionSummary(
    state: SessionUiState,
    deviceLabel: String,
    onEditItems: (TargetsCategory) -> Unit,
    onShowItems: ((TargetsCategory) -> Unit)? = null,
) {
    var details by remember { mutableStateOf<TargetsCategory?>(null) }
    val show = onShowItems ?: { category: TargetsCategory -> details = category }
    val domains = state.displayDomains()
    val applicationCount = state.displayApplicationCount()
    val websiteCount = pluralStringResource(Res.plurals.count_websites, domains.size, domains.size)
    val applicationLabel = pluralStringResource(Res.plurals.count_applications, applicationCount ?: 0, applicationCount ?: 0)
    val applicationsUnavailable = stringResource(Res.string.session_apps_unavailable)
    val savedGroup = stringResource(Res.string.session_selection_saved_group)
    val deviceViewAll = stringResource(Res.string.session_device_view_all, deviceLabel)
    PosatoSection(titleContent = { Text(summaryTitle(state)) }) {
        PosatoDisclosureRow(
            onClick = { show(TargetsCategory.WEBSITES) },
            headlineContent = { Text(websiteCount) },
            supportingContent = { PosatoCaption(stringResource(Res.string.session_websites_view)) },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
        )
        PosatoDisclosureRow(
            onClick = { show(TargetsCategory.APPLICATIONS) },
            headlineContent = {
                Text(if (applicationCount == null) applicationsUnavailable else applicationLabel)
            },
            supportingContent = {
                PosatoCaption(
                    if (state.review.applicationGroupName == null && state.applicationMappings.isNotEmpty()) {
                        savedGroup
                    } else {
                        deviceViewAll
                    },
                )
            },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
        )
        PosatoActionRow {
            PosatoButton(
                onClick = { onEditItems(TargetsCategory.WEBSITES) },
                style = PosatoButtonStyle.Quiet,
                controlId = "session_add_or_edit_websites",
            ) {
                Text(stringResource(Res.string.session_add_or_edit_websites))
            }
            PosatoButton(
                onClick = { onEditItems(TargetsCategory.APPLICATIONS) },
                style = PosatoButtonStyle.Quiet,
                controlId = "session_manage_apps",
            ) {
                Text(stringResource(Res.string.session_manage_apps))
            }
        }
    }
    details?.let { category ->
        SelectionDetails(state, category, onDismiss = { details = null }, onEditItems = onEditItems)
    }
}

@Composable
private fun SelectionDetails(
    state: SessionUiState,
    category: TargetsCategory,
    onDismiss: () -> Unit,
    onEditItems: (TargetsCategory) -> Unit,
) {
    if (platformDevice() != PosatoDevice.Mac) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SessionSelectionPanel(state, category, onDismiss = onDismiss, onEditItems = onEditItems)
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.padding(PosatoSpace.Section).widthIn(max = PosatoSize.Content).fillMaxWidth().fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) { SessionSelectionPanel(state, category, onDismiss = onDismiss, onEditItems = onEditItems) }
        }
    }
}

/**
 * The selected items as a screen pushed over Session on iOS, in place of the sheet: the bar leads back, Edit opens
 * the shown category for editing, and the websites keep their filter field at the top of the list.
 */
@Composable
internal fun SessionSelectionScreen(
    state: SessionUiState,
    initialCategory: TargetsCategory,
    backLabel: String,
    inset: Dp,
    onBack: () -> Unit,
    onEditItems: (TargetsCategory) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    val search = rememberTextFieldState()
    val scroll = rememberLazyListState()
    LaunchedEffect(category, search.text.toString()) { scroll.scrollToItem(0) }
    // The tab frame below reserves the keyboard's height for the whole pane.
    Column(Modifier.fillMaxSize()) {
        PosatoNavigationBar(
            title = selectionPanelTitle(state, category),
            backLabel = backLabel,
            onBack = onBack,
            edgeInset = inset,
            separatorAlpha = { if (scroll.canScrollBackward) 1f else 0f },
            trailingContent = {
                val editWebsites = stringResource(Res.string.session_add_or_edit_websites)
                val manageApps = stringResource(Res.string.session_manage_apps)
                PosatoBarButton(
                    onClick = {
                        focusManager.clearFocus()
                        onBack()
                        onEditItems(category)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (category == TargetsCategory.WEBSITES) editWebsites else manageApps
                    },
                ) { Text(stringResource(Res.string.action_edit), style = PosatoTypography.BarAction) }
            },
        )
        Column(
            Modifier.weight(1f).barColumn().padding(horizontal = inset).padding(top = PosatoBarContentTop),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium),
        ) {
            TargetsCategoryTabs(category, state.displayDomains().size, state.applicationMappings.size) {
                focusManager.clearFocus()
                category = it
            }
            PersistedStartSetCaption(state, category)
            SessionSelectionPanelList(
                state,
                category,
                search,
                scroll,
                filterVisible = state.displayDomains().isNotEmpty(),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SessionSelectionPanel(
    state: SessionUiState,
    initialCategory: TargetsCategory,
    onDismiss: () -> Unit,
    onEditItems: (TargetsCategory) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var category by remember { mutableStateOf(initialCategory) }
    var filterVisible by remember { mutableStateOf(false) }
    val search = remember { TextFieldState() }
    val scroll = rememberLazyListState()
    LaunchedEffect(category, search.text.toString()) { scroll.scrollToItem(0) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().imePadding().padding(PosatoSpace.Section), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoSectionHeader(
            titleContent = { Text(selectionPanelTitle(state, category), style = MaterialTheme.typography.headlineSmall) },
            actionContent = {
                PosatoButton(
                    onDismiss,
                    Modifier.focusRequester(focus),
                    style = PosatoButtonStyle.Quiet,
                    controlId = "session_close_list",
                ) { Text(stringResource(Res.string.session_close_list)) }
            },
        )
        TargetsCategoryTabs(category, state.displayDomains().size, state.applicationMappings.size) {
            focusManager.clearFocus()
            category = it
        }
        PersistedStartSetCaption(state, category)
        PosatoActionRow {
            PosatoButton(
                onClick = {
                    focusManager.clearFocus()
                    onDismiss()
                    onEditItems(category)
                },
                style = PosatoButtonStyle.Quiet,
            ) {
                Text(
                    if (category ==
                        TargetsCategory.WEBSITES
                    ) {
                        stringResource(Res.string.session_add_or_edit_websites)
                    } else {
                        stringResource(Res.string.session_manage_apps)
                    },
                )
            }
            if (category == TargetsCategory.WEBSITES && state.displayDomains().isNotEmpty() && !filterVisible) {
                PosatoButton(
                    onClick = { filterVisible = true },
                    style = PosatoButtonStyle.Quiet,
                    controlId = "session_filter_list",
                ) { Text(stringResource(Res.string.session_filter_list)) }
            }
        }
        SessionSelectionPanelList(state, category, search, scroll, filterVisible, Modifier.weight(1f))
    }
}

@Composable
private fun PersistedStartSetCaption(
    state: SessionUiState,
    category: TargetsCategory,
) {
    if (state.showsPersistedStartSet()) {
        PosatoCaption(
            if (category == TargetsCategory.WEBSITES) {
                stringResource(Res.string.session_websites_at_start)
            } else {
                stringResource(Res.string.session_apps_in_set)
            },
        )
    }
}

@Composable
private fun SessionSelectionPanelList(
    state: SessionUiState,
    category: TargetsCategory,
    search: TextFieldState,
    scroll: LazyListState,
    filterVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val values = selectedItemValues(state, category)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        if (category == TargetsCategory.WEBSITES && filterVisible) {
            PosatoSearchField(search, stringResource(Res.string.session_filter_websites))
            PosatoCaption(stringResource(Res.string.session_filter_only))
        }
        val opaqueCount = state.applicationMappings.count { it.display is LocalApplicationMappingDisplay.Opaque }
        val privateApps = stringResource(Res.string.session_private_apps, opaqueCount)
        if (category == TargetsCategory.APPLICATIONS && opaqueCount > 0) {
            PosatoCaption(privateApps)
        }
        val visible = if (category == TargetsCategory.WEBSITES) {
            values.filter { it.contains(search.text.toString().trim(), ignoreCase = true) }
        } else {
            values
        }
        val websitesOf = pluralStringResource(Res.plurals.count_websites_of, values.size, visible.size, values.size)
        val onDeviceOnly = stringResource(Res.string.targets_on_device_only)
        PosatoCaption(if (category == TargetsCategory.WEBSITES) websitesOf else onDeviceOnly)
        PosatoDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll) {
            if (visible.isEmpty()) {
                item {
                    PosatoCaption(emptyResultsMessage(values, category))
                }
            }
            items(visible) { value ->
                PosatoItemRow(headlineContent = { Text(value) }, leadingContent = {
                    PosatoItemSymbol { PosatoIcon(if (category == TargetsCategory.WEBSITES) PosatoIcons.Globe else PosatoIcons.Apps, null) }
                })
            }
        }
    }
}

@Composable
private fun summaryTitle(state: SessionUiState): String {
    val atStart = stringResource(Res.string.session_items_at_start)
    val selected = stringResource(Res.string.session_selected_items)
    return if (state.showsPersistedStartSet()) atStart else selected
}

@Composable
private fun selectionPanelTitle(
    state: SessionUiState,
    category: TargetsCategory,
): String {
    val selected = stringResource(Res.string.session_selected_items)
    val websites = stringResource(Res.string.session_websites_at_start_title)
    val apps = stringResource(Res.string.session_current_apps)
    if (!state.showsPersistedStartSet()) return selected
    return if (category == TargetsCategory.WEBSITES) websites else apps
}

private fun selectedItemValues(
    state: SessionUiState,
    category: TargetsCategory
): List<String> {
    return when (category) {
        TargetsCategory.WEBSITES -> state.displayDomains()

        TargetsCategory.APPLICATIONS -> state.applicationMappings.mapNotNull { mapping ->
            when (val display = mapping.display) {
                is LocalApplicationMappingDisplay.Named -> display.value
                LocalApplicationMappingDisplay.Opaque -> null
            }
        }
    }
}

@Composable
private fun emptyResultsMessage(
    values: List<String>,
    category: TargetsCategory
): String {
    val noWebsites = stringResource(Res.string.session_no_websites_show)
    val noApps = stringResource(Res.string.session_no_apps_show)
    val noMatch = stringResource(Res.string.session_no_filter_match)
    return when {
        values.isEmpty() && category == TargetsCategory.WEBSITES -> noWebsites
        values.isEmpty() -> noApps
        else -> noMatch
    }
}
