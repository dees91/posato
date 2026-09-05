package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoEmptyState
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoItemList
import app.posato.prototype.designsystem.PosatoItemRow
import app.posato.prototype.designsystem.PosatoItemSymbol
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.model.ItemAction
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.SetupAction

@Composable
internal fun PrototypeItems(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val setup = state.surface == PrototypeSurface.Targets
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            if (setup) "What pulls you away?" else "A little less distraction.",
            eyebrow = if (setup) "MAKE IT YOURS" else "YOUR SELECTION",
            layout = layout,
            description = if (setup) {
                "Choose a website and an app for your first pause. You can change these after setup."
            } else {
                "These items will pause when you start a session. Nothing is paused right now."
            },
        )
        PrototypeWebsiteSection(state, onAction)
        PrototypeApplicationsSection(state, onAction)
        val canFinish = state.policy.domains.isNotEmpty() && state.localApplications().isNotEmpty()
        PosatoButton(onClick = { onAction(if (setup) SetupAction.FinishOnboarding else ItemAction.CloseItems) }, enabled = !setup || canFinish) {
            Text(if (setup) "Finish setup" else "Done")
        }
        if (setup && !canFinish) PosatoCaption("For this walkthrough, add at least one website and one app.")
    }
}

@Composable
private fun PrototypeWebsiteSection(
    state: PrototypeState,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoSection(
        modifier = modifier,
        titleContent = { Text("Websites") },
        descriptionContent = { PosatoCaption("Exact domains shared with your workspace.") },
        actionContent = {
            PosatoButton(onClick = { onAction(ItemAction.OpenDomain()) }, style = PosatoButtonStyle.Secondary) { Text("Add website") }
        },
    ) {
        if (state.policy.domains.isEmpty()) PosatoEmptyState("No websites chosen", description = "Add a website you would like a little space from.")
        PosatoItemList {
            state.policy.domains.forEach { domain ->
                PosatoItemRow(
                    headlineContent = { Text(domain) },
                    supportingContent = {
                        PosatoActionRow {
                            PosatoButton(onClick = {
                                onAction(ItemAction.OpenDomain(domain))
                            }, style = PosatoButtonStyle.Quiet) { Text("Edit $domain") }
                            PosatoButton(onClick = {
                                onAction(ItemAction.RemoveDomain(domain))
                            }, style = PosatoButtonStyle.Quiet) { Text("Remove $domain") }
                        }
                    },
                    leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
                )
            }
        }
    }
}

@Composable
private fun PrototypeApplicationsSection(
    state: PrototypeState,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoSection(
        modifier = modifier,
        titleContent = { Text("Applications") },
        descriptionContent = {
            PosatoCaption("${state.policy.applicationGroup ?: "Application group"} · choices stay on this ${state.platform.label}.")
        },
        actionContent = {
            PosatoButton(onClick = { onAction(ItemAction.OpenApplications) }, style = PosatoButtonStyle.Secondary) { Text("Choose apps") }
        },
    ) {
        if (state.localApplications().isEmpty()) PosatoEmptyState("No applications chosen", description = "Choose a local mapping for this device.")
        PosatoItemList {
            state.localApplications().forEach { name ->
                PosatoItemRow(
                    headlineContent = { Text(name) },
                    leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
                    supportingContent = {
                        PosatoButton(onClick = {
                            onAction(ItemAction.RemoveApplication(name))
                        }, style = PosatoButtonStyle.Quiet) { Text("Remove $name") }
                    },
                )
            }
        }
        PosatoCaption("Prototype application names are synthetic. This is not the system picker.")
    }
}

@Composable
internal fun PrototypeSelectedItems(
    state: PrototypeState,
    modifier: Modifier = Modifier
) {
    PosatoSection(
        modifier = modifier,
        titleContent = { Text(if (state.session.active) "Selected for this pause" else "What will be paused") },
        descriptionContent = { PosatoCaption("On this ${state.platform.label}") },
    ) {
        if (state.effectiveItemCount() == 0) PosatoEmptyState("Nothing chosen yet", description = "Start with one website or app.")
        PosatoItemList {
            state.policy.domains.forEach { domain ->
                PosatoItemRow(
                    headlineContent = { Text(domain) },
                    supportingContent = { PosatoCaption("Exact website · shared") },
                    leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
                )
            }
            state.localApplications().forEach { name ->
                PosatoItemRow(
                    headlineContent = { Text(name) },
                    supportingContent = { PosatoCaption("Application · this device") },
                    leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
                )
            }
        }
    }
}
