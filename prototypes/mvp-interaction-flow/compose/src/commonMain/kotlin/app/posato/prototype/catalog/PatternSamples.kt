package app.posato.prototype.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoActionRow
import app.posato.prototype.designsystem.PosatoBadge
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoEmptyState
import app.posato.prototype.designsystem.PosatoEndTime
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoHero
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoIntervalArtwork
import app.posato.prototype.designsystem.PosatoItemList
import app.posato.prototype.designsystem.PosatoItemRow
import app.posato.prototype.designsystem.PosatoItemSymbol
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoPanel
import app.posato.prototype.designsystem.PosatoPrivacyPoint
import app.posato.prototype.designsystem.PosatoSection
import app.posato.prototype.designsystem.PosatoSetupStep
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoSyncFooter

@Composable
internal fun PatternSamples(
    layout: PosatoLayout,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Spacious)) {
        SessionPattern(layout, onAction)
        ConfigurationPattern(onAction)
        OnboardingPattern()
        EarlyEndPattern(layout, onAction)
    }
}

@Composable
private fun SessionPattern(
    layout: PosatoLayout,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoPanel(modifier = modifier, headerContent = { Text("An active pause") }) {
        PosatoHero(
            layout = layout,
            headingContent = {
                PosatoHeading(
                    title = "A little room. Just for you.",
                    eyebrow = "SESSION ACTIVE",
                    description = "Your selected items are paused on this Mac.",
                    layout = layout,
                )
            },
            artworkContent = { PosatoIntervalArtwork() },
        )
        PosatoEndTime("Until 18:30", Modifier.fillMaxWidth(), supportingText = "A clear ending. You can also end early.")
        PosatoItemList(Modifier.fillMaxWidth()) {
            PosatoItemRow(
                headlineContent = { Text("news.example") },
                supportingContent = { PosatoCaption("Exact website domain") },
                leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Globe, null) } },
                trailingContent = { PosatoBadge("Shared") },
            )
            PosatoItemRow(
                headlineContent = { Text("Social feeds") },
                supportingContent = { PosatoCaption("Example Social") },
                leadingContent = { PosatoItemSymbol { PosatoIcon(PosatoIcons.Apps, null) } },
                trailingContent = { PosatoBadge("This Mac") },
            )
        }
        PosatoButton(onClick = { onAction("Early-end confirmation requested.") }, style = PosatoButtonStyle.Quiet) { Text("End session early") }
        PosatoSyncFooter("Last sync completed on this device at 17:45.")
    }
}

@Composable
private fun ConfigurationPattern(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoSection(
        modifier = modifier,
        titleContent = { Text("Websites", style = MaterialTheme.typography.titleSmall) },
        descriptionContent = { PosatoCaption("Shared across devices. Subdomains are separate.") },
        actionContent = { PosatoButton(onClick = { onAction("Add website requested.") }, style = PosatoButtonStyle.Compact) { Text("Add website") } },
    ) {
        PosatoItemList(Modifier.fillMaxWidth()) {
            PosatoItemRow(
                headlineContent = { Text("reading.example") },
                supportingContent = { PosatoCaption("Shared exact domain") },
                trailingContent = {
                    Column {
                        PosatoButton(onClick = { onAction("Edit website requested.") }, style = PosatoButtonStyle.Compact) { Text("Edit") }
                        PosatoButton(onClick = { onAction("Remove website requested.") }, style = PosatoButtonStyle.Quiet) { Text("Remove") }
                    }
                },
            )
        }
        PosatoEmptyState(
            title = "No apps selected here.",
            description = "Choose the apps you want to pause on this device.",
            actionContent = {
                PosatoButton(onClick = { onAction("Choose local apps requested.") }, style = PosatoButtonStyle.Secondary) { Text("Choose apps") }
            },
        )
    }
}

@Composable
private fun OnboardingPattern(modifier: Modifier = Modifier) {
    PosatoSection(modifier = modifier, titleContent = { Text("A simple start") }) {
        PosatoActionRow {
            PosatoSetupStep("01", "Welcome", current = false)
            PosatoSetupStep("02", "iCloud", current = true)
            PosatoSetupStep("03", "Permissions", current = false)
            PosatoSetupStep("04", "Your items", current = false)
        }
        PosatoPrivacyPoint(
            headlineContent = { Text("A private space") },
            leadingContent = { PosatoIcon(PosatoIcons.Check, null) },
            supportingContent = { PosatoCaption("Settings use your private iCloud storage.") },
        )
        PosatoPrivacyPoint(
            headlineContent = { Text("Each device, its own apps") },
            leadingContent = { PosatoIcon(PosatoIcons.Apps, null) },
            supportingContent = { PosatoCaption("Choose locally. No activity feed or usage score.") },
        )
    }
}

@Composable
private fun EarlyEndPattern(
    layout: PosatoLayout,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PosatoPanel(modifier = modifier, headerContent = { Text("A deliberate confirmation") }) {
        PosatoHeading(
            title = "Ready to end this pause?",
            eyebrow = "YOU’RE IN CONTROL",
            description = "Selected items will be available on this device again. Other devices apply the change when they sync.",
            layout = layout,
        )
        PosatoEndTime("Until 18:30", supportingText = "Your current session’s planned ending")
        PosatoActionRow {
            PosatoButton(onClick = { onAction("Keep-session callback invoked.") }) { Text("Keep session") }
            PosatoButton(onClick = { onAction("End-session callback invoked.") }, style = PosatoButtonStyle.Destructive) { Text("End session early") }
        }
    }
}
