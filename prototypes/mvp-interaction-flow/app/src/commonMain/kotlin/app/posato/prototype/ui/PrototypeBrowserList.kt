package app.posato.prototype.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoEmptyState
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeState
import kotlinx.collections.immutable.PersistentList

@Composable
internal fun PrototypeBrowserList(
    state: PrototypeState,
    section: PrototypeItemSection,
    websites: PersistentList<String>,
    scroll: LazyListState,
    onAction: ((PrototypeAction) -> Unit)?,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, state = scroll) {
        when (section) {
            PrototypeItemSection.Websites -> {
                if (websites.isEmpty()) {
                    item(key = "empty-websites") {
                        PosatoEmptyState(
                            title = if (state.policy.domains.isEmpty()) "No websites chosen" else "No matching websites",
                            description = if (state.policy.domains.isEmpty()) {
                                "Add a website you would like a little space from."
                            } else {
                                "Try another search or clear it to see every website."
                            },
                        )
                    }
                }
                items(websites, key = { it }, contentType = { "website" }) { domain -> PrototypeWebsiteRow(domain, onAction) }
            }

            PrototypeItemSection.Applications -> {
                item(key = "application-scope") {
                    PosatoCaption("${state.policy.applicationGroup ?: "Application group"} · synthetic names, not the system picker.")
                }
                if (state.localApplications().isEmpty()) {
                    item(key = "empty-applications") {
                        PosatoEmptyState("No applications chosen", description = "Choose a local mapping for this device.")
                    }
                }
                items(state.localApplications(), key = { it }, contentType = { "application" }) { name ->
                    PrototypeApplicationRow(name, onAction)
                }
            }
        }
    }
}
