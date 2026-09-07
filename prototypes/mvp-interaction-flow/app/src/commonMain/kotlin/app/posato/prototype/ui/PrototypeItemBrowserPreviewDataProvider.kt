package app.posato.prototype.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.posato.prototype.model.PrototypeFixtures
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypePolicy
import app.posato.prototype.model.PrototypeState
import kotlinx.collections.immutable.persistentListOf

internal data class PrototypeItemBrowserPreviewCase(
    val name: String,
    val state: PrototypeState,
    val section: PrototypeItemSection = PrototypeItemSection.Websites,
    val query: String = "",
    val readOnly: Boolean = false
) {
    override fun toString(): String {
        return name
    }
}

internal class PrototypeItemBrowserPreviewDataProvider : PreviewParameterProvider<PrototypeItemBrowserPreviewCase> {
    private val longList = PrototypeFixtures.longList(PrototypePlatform.Mac)
    override val values: Sequence<PrototypeItemBrowserPreviewCase> = sequenceOf(
        PrototypeItemBrowserPreviewCase("Long websites", longList),
        PrototypeItemBrowserPreviewCase("Local applications", longList, PrototypeItemSection.Applications),
        PrototypeItemBrowserPreviewCase("Filtered website", longList, query = "50"),
        PrototypeItemBrowserPreviewCase("No matches", longList, query = "no-match"),
        PrototypeItemBrowserPreviewCase("No websites", longList.copy(policy = PrototypePolicy())),
        PrototypeItemBrowserPreviewCase("No applications", longList.withLocalApplications(persistentListOf()), PrototypeItemSection.Applications),
        PrototypeItemBrowserPreviewCase("Read-only websites", longList, readOnly = true),
        PrototypeItemBrowserPreviewCase("Read-only applications", longList, PrototypeItemSection.Applications, readOnly = true),
        PrototypeItemBrowserPreviewCase(
            "Long domain",
            longList.copy(policy = longList.policy.copy(domains = persistentListOf("a-long-reading-list-for-a-quiet-afternoon.reading.example"))),
        ),
    )
}
