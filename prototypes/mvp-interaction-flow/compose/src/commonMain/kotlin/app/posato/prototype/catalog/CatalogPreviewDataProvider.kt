package app.posato.prototype.catalog

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class CatalogPreviewDataProvider : PreviewParameterProvider<CatalogState> {
    override val values: Sequence<CatalogState> = CatalogSection.entries.asSequence().flatMap { section ->
        sequenceOf(
            CatalogState(section = section),
            CatalogState(section = section, darkTheme = true),
            CatalogState(section = section, highContrast = true, enlargedText = true),
        )
    }

    override fun getDisplayName(index: Int): String {
        val state = values.elementAt(index)
        val appearance = if (state.darkTheme) "dark" else "light"
        val contrast = if (state.highContrast) " · high contrast and enlarged text" else ""

        return "${state.section.label} · $appearance$contrast"
    }
}
