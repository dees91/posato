package app.posato.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoLayout

@Composable
internal fun MacPermissionContent(
    state: OnboardingViewState,
    layout: PosatoLayout,
    onEnableHelper: () -> Unit,
    onRecheckHelper: () -> Unit,
    onOpenHelperSettings: () -> Unit,
) {
    var showingBlockingSettings by remember { mutableStateOf(false) }
    MacSetupOverview()
    PosatoCaption("macOS may ask you to confirm access. You can review or revoke it later in This Mac settings.")
    PosatoDisclosureRow(
        onClick = { showingBlockingSettings = !showingBlockingSettings },
        headlineContent = { Text("Blocking settings") },
        supportingContent = { PosatoCaption("Existing helper controls remain available while unified setup is in preview.") },
        trailingContent = { PosatoIcon(if (showingBlockingSettings) PosatoIcons.ChevronUp else PosatoIcons.ChevronDown, null) },
    )
    if (showingBlockingSettings) {
        PermissionStatus(state, OnboardingPermissionPlatform.MAC)
        if (!state.hasDeviceAccess(OnboardingPermissionPlatform.MAC)) {
            MacPermissionActions(state, layout, onEnableHelper, onRecheckHelper, onOpenHelperSettings)
        }
    }
}
