package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoHeading
import app.posato.prototype.designsystem.PosatoIcon
import app.posato.prototype.designsystem.PosatoIcons
import app.posato.prototype.designsystem.PosatoIntervalArtwork
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoNotice
import app.posato.prototype.designsystem.PosatoPrivacyPoint
import app.posato.prototype.designsystem.PosatoSpace
import app.posato.prototype.designsystem.PosatoTone
import app.posato.prototype.model.PrototypeAction
import app.posato.prototype.model.PrototypePlatform
import app.posato.prototype.model.PrototypeState
import app.posato.prototype.model.PrototypeSurface
import app.posato.prototype.model.SetupAction

@Composable
internal fun PrototypeOnboarding(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        when (state.surface) {
            PrototypeSurface.Welcome -> {
                PosatoIntervalArtwork()
                PosatoHeading(
                    "A little space.\nFor what matters.",
                    eyebrow = "WELCOME TO POSATO",
                    layout = layout,
                    description = "Pause the websites and apps that pull you away. Choose when your pause ends, then get back to your day.",
                )
                PosatoButton(onClick = { onAction(SetupAction.ShowPrivacy) }) { Text("Make some space") }
            }

            PrototypeSurface.Privacy -> {
                PrototypePrivacy(layout, onAction)
            }

            PrototypeSurface.WorkspaceCheck -> {
                PosatoHeading(
                    "Finding your space.",
                    eyebrow = "CONNECTING WITH ICLOUD",
                    layout = layout,
                    description = "Checking your private iCloud storage for an existing Posato workspace.",
                )
                PosatoNotice { Text("Your workspace will only be created if one does not already exist.") }
                PosatoCaption("Prototype: long-press posato to choose a mock iCloud result in controls.")
            }

            PrototypeSurface.KeyWait -> {
                PosatoHeading(
                    "One more moment.",
                    eyebrow = "WORKSPACE FOUND",
                    layout = layout,
                    description = "Your space is here. We’re waiting for iCloud Keychain to make its key available on this device.",
                )
                PosatoNotice(tone = PosatoTone.Caution) {
                    Text("Check whether Apple is asking you to approve this device in Settings. Your existing workspace stays unchanged.")
                }
                PosatoButton(onClick = { onAction(SetupAction.KeyArrived) }, style = PosatoButtonStyle.Secondary) { Text("Check again") }
                PosatoCaption("In this study, checking again simulates the key arriving.")
            }

            PrototypeSurface.Permission -> {
                PrototypePermissionRationale(state, layout, onAction)
            }

            PrototypeSurface.Targets, PrototypeSurface.DomainEditor, PrototypeSurface.AppPicker,
            PrototypeSurface.Items, PrototypeSurface.Home, PrototypeSurface.SessionSetup, PrototypeSurface.SessionReview,
            PrototypeSurface.Active, PrototypeSurface.Blocked, PrototypeSurface.EarlyEnd, PrototypeSurface.Recovery -> {
                error("Not an onboarding surface.")
            }
        }
    }
}

@Composable
private fun PrototypePrivacy(
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "Your choices stay yours.",
            eyebrow = "YOURS, BY DESIGN",
            layout = layout,
            description = "No new account. No browsing history. Just a shared pause across your own Apple devices.",
        )
        PosatoPrivacyPoint(
            headlineContent = { Text("A private space") },
            leadingContent = { PosatoIcon(PosatoIcons.Check, null) },
            supportingContent = { PosatoCaption("Your settings use your private iCloud storage.") },
        )
        PosatoPrivacyPoint(
            headlineContent = { Text("Bring your pause along") },
            leadingContent = { PosatoIcon(PosatoIcons.Cloud, null) },
            supportingContent = { PosatoCaption("Share websites and session intent with your devices.") },
        )
        PosatoPrivacyPoint(
            headlineContent = { Text("Each device, its own apps") },
            leadingContent = { PosatoIcon(PosatoIcons.Apps, null) },
            supportingContent = { PosatoCaption("Choose locally. No usage scores or activity feed.") },
        )
        PosatoButton(onClick = { onAction(SetupAction.SyncWithICloud) }) { Text("Continue with iCloud") }
        PosatoCaption("This prototype uses independent mock data. It never connects to iCloud.")
    }
}

@Composable
private fun PrototypePermissionRationale(
    state: PrototypeState,
    layout: PosatoLayout,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = when (state.platform) {
        PrototypePlatform.IPhone -> "Screen Time access lets Posato pause the apps you choose on this iPhone."
        PrototypePlatform.Mac -> "Local access lets Posato pause the websites and apps you choose on this Mac."
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "A small permission.\nA useful pause.",
            eyebrow = "SET UP THIS ${state.platform.label.uppercase()}",
            description = description,
            layout = layout,
        )
        PosatoNotice { Text("You choose the items and the duration. No browsing history is collected.") }
        PosatoButton(onClick = { onAction(SetupAction.GrantPermission) }) { Text("Allow on this ${state.platform.label}") }
        PosatoCaption("This preview simulates permission. It does not open system settings.")
    }
}
