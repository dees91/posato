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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
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
import app.posato.core.designsystem.PosatoItemMenuAction
import app.posato.core.designsystem.PosatoItemRow
import app.posato.core.designsystem.PosatoItemSymbol
import app.posato.core.designsystem.PosatoSearchField
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTextField
import app.posato.feature.targets.domain.ExactDomain
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WebsiteBrowser(
    state: TargetsUiState,
    browser: TargetsBrowserState,
    onSubmit: (String, Long) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val query = browser.search.text.toString().trim()
    val domains = if (browser.searching) state.domains.filter { it.contains(query, ignoreCase = true) } else state.domains
    Column(modifier.fillMaxSize()) {
        if (browser.searching) {
            PosatoSearchField(browser.search, "Search websites")
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
            PosatoButton(
                onClick = {
                    focus.clearFocus()
                    browser.searching = !browser.searching
                    if (!browser.searching) browser.search.edit { replace(0, length, "") }
                },
                style = PosatoButtonStyle.Quiet,
            ) {
                PosatoIcon(if (browser.searching) PosatoIcons.Close else PosatoIcons.Search, null)
                Text(if (browser.searching) "Back to adding" else "Search", Modifier.padding(start = PosatoSpace.Small))
            }
        }
        PosatoDivider()
        LazyColumn(
            state = browser.websitesScroll,
            modifier = Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Saved websites" },
        ) {
            if (domains.isEmpty()) {
                item {
                    PosatoEmptyState(
                        title = if (state.domains.isEmpty()) "A little less noise starts here." else "No matching websites",
                        description = if (state.domains.isEmpty()) {
                            "Add the websites you would like a little space from."
                        } else {
                            "Try another name or clear your search."
                        },
                        modifier = Modifier.padding(vertical = PosatoSpace.Section),
                    )
                }
            }
            items(domains, key = { it }) { domain ->
                WebsiteRow(domain, state.canMutatePolicy(), {
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

private fun websiteCountLabel(
    visible: Int,
    total: Int,
    searching: Boolean
): String {
    return when {
        searching -> "$visible of $total"
        total == 1 -> "1 website"
        else -> "$total websites"
    }
}

@Composable
internal fun WebsiteEditor(
    state: TargetsUiState,
    input: TextFieldState,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val submit: () -> Unit = { if (state.canMutatePolicy()) onSubmit(input.text.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        Text("Edit website", style = MaterialTheme.typography.titleMedium)
        PosatoTextField(
            state = input,
            label = "Website domain",
            enabled = state.canMutatePolicy(),
            errorMessage = state.domainInputFailure?.let { stringResource(it.domainMessage()) },
            supportingText = "This host and its www variant. Other subdomains are separate entries.",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            onSubmit = submit,
        )
        PosatoActionRow {
            PosatoButton(submit, enabled = state.canMutatePolicy()) { Text("Save changes") }
            PosatoButton(onCancel, style = PosatoButtonStyle.Quiet, enabled = !state.isSaving) { Text("Cancel") }
        }
    }
}

@Composable
private fun WebsiteRow(
    domain: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val counterpart = ExactDomain.restore(domain)?.wwwCounterpart()?.canonicalValue
    PosatoItemRow(
        headlineContent = { Text(domain, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = counterpart?.let { covered ->
            { PosatoCaption("Also pauses $covered") }
        },
        leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onEdit() },
        trailingContent = {
            if (enabled) {
                PosatoItemMenu("Actions for $domain") { dismiss ->
                    PosatoItemMenuAction(onClick = {
                        dismiss()
                        onEdit()
                    }, leadingContent = { PosatoIcon(PosatoIcons.Edit, null) }) {
                        Text("Edit")
                    }
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
