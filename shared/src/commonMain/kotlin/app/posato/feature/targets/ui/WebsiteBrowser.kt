package app.posato.feature.targets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.posato.core.designsystem.PosatoActionRow
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
import app.posato.core.designsystem.PosatoSearchField
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoSwipeAction
import app.posato.core.designsystem.PosatoSwipeRow
import app.posato.core.designsystem.PosatoTextField
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.feature.targets.domain.ExactDomain
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_back_to_adding
import app.posato.generated.resources.action_cancel
import app.posato.generated.resources.action_edit
import app.posato.generated.resources.action_remove
import app.posato.generated.resources.count_websites
import app.posato.generated.resources.onboarding_progress
import app.posato.generated.resources.schedule_actions_for
import app.posato.generated.resources.targets_also_pauses
import app.posato.generated.resources.targets_edit_website
import app.posato.generated.resources.targets_host_www
import app.posato.generated.resources.targets_save_changes
import app.posato.generated.resources.targets_saved_websites
import app.posato.generated.resources.targets_search
import app.posato.generated.resources.targets_search_websites
import app.posato.generated.resources.targets_website_domain
import app.posato.generated.resources.targets_websites_empty
import app.posato.generated.resources.targets_websites_empty_body
import app.posato.generated.resources.targets_websites_none
import app.posato.generated.resources.targets_websites_none_body
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WebsiteBrowser(
    state: TargetsUiState,
    browser: TargetsBrowserState,
    onSubmit: (String, Long) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    rowActionsEnabled: Boolean = true,
) {
    val focus = LocalFocusManager.current
    val query = browser.search.text.toString().trim()
    val domains = if (browser.searching) state.domains.filter { it.contains(query, ignoreCase = true) } else state.domains
    Column(modifier.fillMaxSize()) {
        if (browser.searching) {
            PosatoSearchField(browser.search, stringResource(Res.string.targets_search_websites))
        } else {
            WebsiteEntry(browser, state.canMutatePolicy(), onSubmit)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = PosatoSpace.Medium, bottom = PosatoSpace.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosatoCaption(
                websiteCountLabel(domains.size, state.domains.size, browser.searching),
                Modifier.weight(1f),
            )
            WebsiteSearchToggle(browser, focus)
        }
        PosatoDivider()
        val savedWebsites = stringResource(Res.string.targets_saved_websites)
        LazyColumn(
            state = browser.websitesScroll,
            modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = savedWebsites },
        ) {
            if (domains.isEmpty()) {
                item {
                    PosatoEmptyState(
                        title = if (state.domains.isEmpty()) {
                            stringResource(
                                Res.string.targets_websites_empty,
                            )
                        } else {
                            stringResource(Res.string.targets_websites_none)
                        },
                        description = if (state.domains.isEmpty()) {
                            stringResource(Res.string.targets_websites_empty_body)
                        } else {
                            stringResource(Res.string.targets_websites_none_body)
                        },
                        modifier = Modifier.padding(vertical = PosatoSpace.Section),
                    )
                }
            }
            items(domains, key = { it }) { domain ->
                WebsiteRow(domain, state.domains, state.canMutatePolicy() && rowActionsEnabled, {
                    focus.clearFocus()
                    onEdit(domain)
                }, {
                    focus.clearFocus()
                    onRemove(domain)
                })
            }
        }
    }
}

@Composable
private fun WebsiteSearchToggle(
    browser: TargetsBrowserState,
    focus: FocusManager,
) {
    PosatoButton(
        onClick = {
            focus.clearFocus()
            browser.searching = !browser.searching
            if (!browser.searching) browser.search.edit { replace(0, length, "") }
        },
        style = PosatoButtonStyle.Quiet,
        controlId = "targets_search",
    ) {
        PosatoIcon(if (browser.searching) PosatoIcons.Close else PosatoIcons.Search, null)
        Text(
            if (browser.searching) stringResource(Res.string.action_back_to_adding) else stringResource(Res.string.targets_search),
            Modifier.padding(start = PosatoSpace.Small),
        )
    }
}

@Composable
private fun websiteCountLabel(
    visible: Int,
    total: Int,
    searching: Boolean,
): String {
    val progress = stringResource(Res.string.onboarding_progress, visible, total)
    val count = pluralStringResource(Res.plurals.count_websites, total, total)
    return if (searching) progress else count
}

@Composable
internal fun WebsiteEditor(
    state: TargetsUiState,
    input: TextFieldState,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    showsTitle: Boolean = true,
) {
    val submit: () -> Unit = { if (state.canMutatePolicy()) onSubmit(input.text.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        if (showsTitle) Text(stringResource(Res.string.targets_edit_website), style = MaterialTheme.typography.titleMedium)
        PosatoTextField(
            state = input,
            label = stringResource(Res.string.targets_website_domain),
            enabled = state.canMutatePolicy(),
            errorMessage = state.domainInputFailure?.let { stringResource(it.domainMessage()) },
            supportingText = stringResource(Res.string.targets_host_www),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            onSubmit = submit,
        )
        PosatoActionRow {
            PosatoButton(
                submit,
                enabled = state.canMutatePolicy(),
                controlId = "targets_save_changes",
            ) { Text(stringResource(Res.string.targets_save_changes)) }
            PosatoButton(
                onCancel,
                style = PosatoButtonStyle.Quiet,
                enabled = !state.isSaving,
                controlId = "action_cancel",
            ) { Text(stringResource(Res.string.action_cancel)) }
        }
    }
}

@Composable
private fun WebsiteRow(
    domain: String,
    listed: List<String>,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val counterpart = remember(domain, listed) {
        val value = ExactDomain.restore(domain)?.wwwCounterpart()?.canonicalValue
        if (value != null && value !in listed) value else null
    }
    val alsoPauses = stringResource(Res.string.targets_also_pauses, counterpart.orEmpty())
    val caption = if (counterpart != null) alsoPauses else null
    val row = @Composable { rowActions: List<CustomAccessibilityAction> ->
        PosatoItemRow(
            headlineContent = { Text(domain, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = caption?.let { covered ->
                { PosatoCaption(covered) }
            },
            leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClickLabel = stringResource(Res.string.action_edit)) { onEdit() }
                .semantics { if (rowActions.isNotEmpty()) customActions = rowActions },
            trailingContent = {
                if (enabled && !platformUsesCupertinoChrome) {
                    PosatoItemMenu(
                        stringResource(Res.string.schedule_actions_for, domain),
                        listOf(
                            PosatoMenuItem(stringResource(Res.string.action_edit), onEdit, PosatoMenuSymbol.Edit, controlId = "action_edit"),
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
    // On iOS the row edits on a tap and removes under a left swipe, as list rows there do, with no button of its own.
    if (platformUsesCupertinoChrome && enabled) {
        PosatoSwipeRow(
            listOf(PosatoSwipeAction(stringResource(Res.string.action_remove), onRemove, destructive = true, controlId = "action_remove")),
        ) { rowActions -> row(rowActions) }
    } else {
        row(emptyList())
    }
}
