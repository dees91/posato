package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoChoiceGroup
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoNavigationItem
import app.posato.prototype.designsystem.PosatoSidebar

@Composable
internal fun CatalogNavigation(
    state: CatalogState,
    onStateChange: (CatalogState) -> Unit,
    layout: PosatoLayout,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (layout == PosatoLayout.Expanded) {
            PosatoSidebar(footerContent = { PosatoCaption("Prototype library\nCompose Multiplatform") }) {
                CatalogNavigationEntries(state, onStateChange)
            }
        } else {
            PosatoActionRow {
                CatalogSection.entries.forEach { section ->
                    PosatoNavigationItem(
                        selected = state.section == section,
                        onClick = { onStateChange(state.copy(section = section)) },
                    ) { Text(section.label, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun CatalogNavigationEntries(
    state: CatalogState,
    onStateChange: (CatalogState) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoChoiceGroup(modifier = modifier) {
        CatalogSection.entries.forEach { section ->
            PosatoNavigationItem(
                modifier = Modifier.fillMaxWidth(),
                selected = state.section == section,
                onClick = { onStateChange(state.copy(section = section)) },
            ) { Text(section.label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
