package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.onboarding.MacSetupPreviewAction
import app.posato.feature.onboarding.MacSetupSection
import app.posato.feature.onboarding.needsSetup

internal fun SessionUiState.showsMacSetup(macSetup: MacSetupPresentation): Boolean {
    return (isSettingUp || isReviewing) && !isStarting && macSetup.needsSetup()
}

@Composable
internal fun SessionMacSetup(
    state: SessionUiState,
    macSetup: MacSetupPresentation,
    layout: PosatoLayout,
    onCheck: () -> Unit,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onAnnouncement: (String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "Set up Posato on this Mac.",
            eyebrow = "FINISH SETUP",
            description = "One setup to block distractions and run your schedules.",
            layout = layout,
        )
        MacSetupOverview()
        PosatoCaption("macOS may ask you to confirm access. You can review or revoke it later in This Mac settings.")
        MacSetupPreviewAction()
        PosatoCaption("Existing blocking controls are available in This Mac below.")
        MacSetupSection(
            presentation = macSetup,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            onCheck = onCheck,
            onEnable = onEnable,
            onOpenSettings = onOpenSettings,
            onAnnouncement = onAnnouncement,
            sessionBlocksRemoval = state.blocksHelperRemoval(),
            onRemove = onRemove,
        )
        PosatoCaption("The current helper setup lets you start manual pauses. It does not complete the new unified setup or enable schedules.")
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back to Session") }
    }
}
