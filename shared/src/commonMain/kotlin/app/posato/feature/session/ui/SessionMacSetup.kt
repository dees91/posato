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
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.onboarding.MacSetupAction
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.onboarding.MacSetupSection
import app.posato.feature.onboarding.needsSetup

internal fun SessionUiState.showsMacSetup(macSetup: MacSetupPresentation): Boolean {
    return (isSettingUp || isReviewing) && !isStarting && (macSetup.needsSetup() || macSetup.setup?.running == true)
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
    onSetUp: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            "Set up Posato on this Mac.",
            eyebrow = "FINISH SETUP",
            description = "One setup to block distractions and run your schedules.",
            layout = layout,
        )
        MacSetupOverview(run = macSetup.setup)
        MacSetupAction(macSetup, onSetUp, sessionBlocks = state.blocksHelperRemoval())
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
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back to Session") }
    }
}
