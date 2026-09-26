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
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.onboarding.MacSetupSection

internal fun SessionUiState.showsMacSetup(macSetup: MacSetupPresentation): Boolean {
    return (isSettingUp || isReviewing) && !isStarting && macSetup.readiness != MacHelperReadiness.READY
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
            "Enable blocking on this Mac.",
            eyebrow = "FINISH SETUP",
            description = "The background helper is required to block websites and apps. You can edit your saved choices during setup.",
            layout = layout,
        )
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
        PosatoCaption("Once the helper is ready, you can choose your session duration. Blocking starts only when you confirm Start.")
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back to Session") }
    }
}
