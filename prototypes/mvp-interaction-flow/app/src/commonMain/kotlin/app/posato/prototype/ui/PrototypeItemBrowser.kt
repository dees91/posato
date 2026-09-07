package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.clearText
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
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoPrototypeTheme
import app.posato.prototype.designsystem.PosatoSearchField
import app.posato.prototype.designsystem.PosatoSectionHeader
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTab
import app.posato.prototype.designsystem.PosatoTabBar
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
        PrototypeBrowserInput(state, browser, onAction)
        PrototypeBrowserToolbar(
            modifier = Modifier.padding(top = PosatoSpace.Medium, bottom = PosatoSpace.Small),
            state = state,
            browser = browser,
            websiteCount = websites.size,
            onAction = onAction,
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
private fun PrototypeBrowserToolbar(
    state: PrototypeState,
    browser: PrototypeItemBrowserState,
    websiteCount: Int,
    onAction: ((PrototypeAction) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val section = browser.section
    val search = browser.search
    val totalLabel = if (websiteCount == 1) "1 website" else "$websiteCount websites"
    val focus = LocalFocusManager.current
    PosatoSectionHeader(
        modifier = modifier,
        titleContent = {
            PosatoCaption(
                if (section == PrototypeItemSection.Websites) {
                    if (search.text.isNotEmpty()) "$websiteCount of ${state.policy.domains.size}" else totalLabel
                } else {
                    "On this ${state.platform.label} only"
                },
            )
        },
        actionContent = onAction?.let { dispatch ->
            {
                PosatoButton(onClick = {
                    focus.clearFocus()
                    if (section == PrototypeItemSection.Websites) {
                        search.clearText()
                        browser.searching = !browser.searching
                    } else {
                        dispatch(ItemAction.OpenApplications)
                    }
                }, style = if (section == PrototypeItemSection.Websites) PosatoButtonStyle.Quiet else PosatoButtonStyle.Primary) {
                    if (section == PrototypeItemSection.Websites) {
                        PosatoIcon(if (browser.searching) PosatoIcons.Close else PosatoIcons.Search, contentDescription = null)
                        Spacer(Modifier.width(PosatoSpace.Tiny))
                        Text(if (browser.searching) "Back to adding" else "Search")
                    } else {
                        Text("Choose apps")
                    }
                }
            }
        },
    )
}

@Composable
private fun PrototypeBrowserInput(
    state: PrototypeState,
    browser: PrototypeItemBrowserState,
    onAction: ((PrototypeAction) -> Unit)?
) {
    if (browser.section != PrototypeItemSection.Websites) return
    if (onAction == null || browser.searching) {
        PosatoSearchField(state = browser.search, label = "Search websites")
    } else {
        PrototypeWebsiteEntry(browser = browser, result = state.websiteEntry, onAction = onAction)
    }
}

@Composable
private fun PrototypeItemTabs(
    state: PrototypeState,
    section: PrototypeItemSection,
    onSelect: (PrototypeItemSection) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoTabBar(
        modifier = modifier.fillMaxWidth().padding(bottom = PosatoSpace.Small),
    ) {
        PrototypeItemSection.entries.forEach { destination ->
            PosatoTab(
                modifier = Modifier.weight(1f),
                selected = section == destination,
                onClick = { onSelect(destination) },
                countContent = {
                    Text(
                        when (destination) {
                            PrototypeItemSection.Websites -> state.policy.domains.size
                            PrototypeItemSection.Applications -> state.localApplications().size
                        }.toString(),
                    )
                },
            ) {
                val label = when (destination) {
                    PrototypeItemSection.Websites -> "Websites"
                    PrototypeItemSection.Applications -> "Apps"
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
