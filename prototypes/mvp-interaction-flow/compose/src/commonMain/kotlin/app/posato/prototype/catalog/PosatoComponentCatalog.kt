package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import app.posato.prototype.designsystem.PosatoAppScaffold
import app.posato.prototype.designsystem.PosatoChoiceGroup
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoNotice
import app.posato.prototype.designsystem.PosatoPrototypeTheme
import app.posato.prototype.designsystem.PosatoSize
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoToggleButton
import app.posato.prototype.designsystem.workbench.PrototypeFooter
import app.posato.prototype.designsystem.workbench.PrototypeHeader

@Composable
fun PosatoComponentCatalog(
    state: CatalogState,
    onStateChange: (CatalogState) -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    lastAction: String = "Choose a component to explore its behavior.",
) {
    val density = LocalDensity.current
    val fontScale = if (state.enlargedText) density.fontScale * ENLARGED_FONT_SCALE else density.fontScale
    PosatoPrototypeTheme(darkTheme = state.darkTheme, highContrast = state.highContrast) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    PosatoAppScaffold(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        navigationContent = { CatalogNavigation(state, onStateChange, it) },
                    ) { layout ->
                        CatalogPage(state, onStateChange, onAction, layout)
                    }
                    PosatoNotice(modifier = Modifier.fillMaxWidth(), announceChanges = true) {
                        Text(lastAction, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogPage(
    state: CatalogState,
    onStateChange: (CatalogState) -> Unit,
    onAction: (String) -> Unit,
    layout: PosatoLayout,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        key(state.section) {
            Column(
                Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas),
                verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious),
            ) {
                PrototypeHeader(
                    title = state.section.label,
                    studyLabel = "COMPOSE COMPONENT STUDY",
                    description = state.section.description,
                    layout = layout,
                )
                CatalogAppearanceControls(state, onStateChange)
                CatalogSamples(state.section, layout, onAction)
                PrototypeFooter(
                    note = "Prototype components. No blocking, permissions, storage, or iCloud integration.",
                    detail = "Exact geometry is a design study, not an accepted production UI contract.",
                )
            }
        }
    }
}

@Composable
private fun CatalogAppearanceControls(
    state: CatalogState,
    onStateChange: (CatalogState) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoChoiceGroup(modifier = modifier) {
        PosatoToggleButton(state.darkTheme, onClick = { onStateChange(state.copy(darkTheme = !state.darkTheme)) }) { Text("Dark") }
        PosatoToggleButton(state.highContrast, onClick = { onStateChange(state.copy(highContrast = !state.highContrast)) }) { Text("More contrast") }
        PosatoToggleButton(state.enlargedText, onClick = { onStateChange(state.copy(enlargedText = !state.enlargedText)) }) { Text("Larger text") }
    }
}

@Composable
private fun CatalogSamples(
    section: CatalogSection,
    layout: PosatoLayout,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        when (section) {
            CatalogSection.Foundations -> FoundationSamples()
            CatalogSection.Controls -> ControlSamples(onAction)
            CatalogSection.Forms -> FormSamples(onAction)
            CatalogSection.Feedback -> FeedbackSamples(onAction)
            CatalogSection.Patterns -> PatternSamples(layout, onAction)
            CatalogSection.Workbench -> WorkbenchSamples(onAction)
        }
    }
}

@Preview(name = "Compact catalog", widthDp = 390, heightDp = 1000)
@Composable
private fun CatalogCompactPreview(
    @PreviewParameter(CatalogPreviewDataProvider::class) state: CatalogState
) {
    PosatoComponentCatalog(state, onStateChange = {}, onAction = {})
}

@Preview(name = "Expanded catalog", widthDp = 1200, heightDp = 900)
@Composable
private fun CatalogExpandedPreview(
    @PreviewParameter(CatalogPreviewDataProvider::class) state: CatalogState
) {
    PosatoComponentCatalog(state, onStateChange = {}, onAction = {})
}

private const val ENLARGED_FONT_SCALE = 1.6f
