package app.posato.prototype.catalog

import androidx.compose.runtime.Immutable

@Immutable
data class CatalogState(
    val section: CatalogSection = CatalogSection.Foundations,
    val darkTheme: Boolean = false,
    val highContrast: Boolean = false,
    val enlargedText: Boolean = false,
)
