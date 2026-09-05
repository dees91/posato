package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoDivider
import app.posato.prototype.designsystem.PosatoNavigationItem
import app.posato.prototype.designsystem.PosatoPrototypeTheme
import app.posato.prototype.designsystem.PosatoSearchField
import app.posato.prototype.designsystem.PosatoSectionHeader
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeState

@Composable
internal fun PrototypeItemBrowser(
    state: PrototypeState,
    onAction: ((PrototypeAction) -> Unit)?,
    modifier: Modifier = Modifier,
    browser: PrototypeItemBrowserState = rememberPrototypeItemBrowserState()
) {
    val section = browser.section
    val search = browser.search
    val websites = filterPrototypeWebsites(state.policy.domains, search.text.toString())
    val focus = LocalFocusManager.current
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PrototypeItemTabs(state, section, onSelect = {
            focus.clearFocus()
            browser.section = it
        })
        if (section == PrototypeItemSection.Websites) PosatoSearchField(state = search, label = "Search websites")
        PosatoSectionHeader(
            titleContent = {
                PosatoCaption(
                    if (section == PrototypeItemSection.Websites) {
                        "${websites.size} of ${state.policy.domains.size} · shared exact domains"
                    } else {
                        "On this ${state.platform.label} only"
                    },
                )
            },
            actionContent = onAction?.let { dispatch ->
                {
                    PosatoButton(onClick = {
                        focus.clearFocus()
                        dispatch(if (section == PrototypeItemSection.Websites) ItemAction.OpenDomain() else ItemAction.OpenApplications)
                    }, style = PosatoButtonStyle.Secondary) {
                        Text(if (section == PrototypeItemSection.Websites) "Add website" else "Choose apps")
                    }
                }
            },
        )
        PosatoDivider()
        PrototypeBrowserList(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = state,
            section = section,
            websites = websites,
            scroll = if (section == PrototypeItemSection.Websites) browser.websiteScroll else browser.applicationScroll,
            onAction = onAction?.let { dispatch ->
                { action ->
                    focus.clearFocus()
                    dispatch(action)
                }
            },
        )
    }
}

@Composable
private fun PrototypeItemTabs(
    state: PrototypeState,
    section: PrototypeItemSection,
    onSelect: (PrototypeItemSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PrototypeItemSection.entries.forEach { destination ->
            PosatoNavigationItem(
                modifier = Modifier.weight(1f),
                selected = section == destination,
                onClick = { onSelect(destination) },
            ) {
                val label = when (destination) {
                    PrototypeItemSection.Websites -> "Websites ${state.policy.domains.size}"
                    PrototypeItemSection.Applications -> "Apps ${state.localApplications().size}"
                }
                Text(label)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 650)
@Composable
private fun PrototypeItemBrowserCompactPreview(
    @PreviewParameter(PrototypeItemBrowserPreviewDataProvider::class) preview: PrototypeItemBrowserPreviewCase
) {
    PosatoPrototypeTheme {
        PrototypeItemBrowser(
            state = preview.state,
            onAction = if (preview.readOnly) null else { _ -> },
            browser = rememberPrototypeItemBrowserState(preview.section, preview.query),
        )
    }
}

@Preview(widthDp = 780, heightDp = 650)
@Composable
private fun PrototypeItemBrowserExpandedPreview(
    @PreviewParameter(PrototypeItemBrowserPreviewDataProvider::class) preview: PrototypeItemBrowserPreviewCase
) {
    PosatoPrototypeTheme {
        PrototypeItemBrowser(
            state = preview.state,
            onAction = if (preview.readOnly) null else { _ -> },
            browser = rememberPrototypeItemBrowserState(preview.section, preview.query),
        )
    }
}
