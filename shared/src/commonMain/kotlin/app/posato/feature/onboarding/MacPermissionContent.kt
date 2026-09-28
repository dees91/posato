package app.posato.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoLayout
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_unified_blocking_settings
import app.posato.generated.resources.mac_unified_blocking_settings_caption
import app.posato.generated.resources.mac_unified_confirm_access
import app.posato.generated.resources.onboarding_action_continue
import app.posato.generated.resources.onboarding_action_not_now
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MacPermissionContent(
    state: OnboardingViewState,
    layout: PosatoLayout,
    onEnableHelper: () -> Unit,
    onRecheckHelper: () -> Unit,
    onOpenHelperSettings: () -> Unit,
) {
    var showingBlockingSettings by remember { mutableStateOf(false) }
    MacSetupOverview(run = state.macSetup?.setup)
    PosatoCaption(stringResource(Res.string.mac_unified_confirm_access))
    PosatoDisclosureRow(
        onClick = { showingBlockingSettings = !showingBlockingSettings },
        onClickLabel = stringResource(Res.string.mac_unified_blocking_settings),
        headlineContent = { Text(stringResource(Res.string.mac_unified_blocking_settings)) },
        supportingContent = { PosatoCaption(stringResource(Res.string.mac_unified_blocking_settings_caption)) },
        trailingContent = { PosatoIcon(if (showingBlockingSettings) PosatoIcons.ChevronUp else PosatoIcons.ChevronDown, null) },
    )
    if (showingBlockingSettings) {
        PermissionStatus(state, OnboardingPermissionPlatform.MAC)
        if (!state.hasDeviceAccess(OnboardingPermissionPlatform.MAC)) {
            MacPermissionActions(state, layout, onEnableHelper, onRecheckHelper, onOpenHelperSettings)
        }
    }
}

/**
 * The Mac permission step leads with the one setup action. Continue becomes primary once setup is
 * verified; before that a quiet button continues (blocking already works) or defers.
 */
@Composable
internal fun MacSetupStepActions(
    state: OnboardingViewState,
    macSetup: MacSetupPresentation,
    layout: PosatoLayout,
    onSetUp: () -> Unit,
    onContinue: () -> Unit,
) {
    MacSetupAction(macSetup, onSetUp)
    if (macSetup.setupComplete) {
        OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_continue), layout, onContinue)
    } else {
        val continues = state.hasDeviceAccess(OnboardingPermissionPlatform.MAC)
        PosatoButton(onClick = onContinue, style = PosatoButtonStyle.Quiet) {
            Text(stringResource(if (continues) Res.string.onboarding_action_continue else Res.string.onboarding_action_not_now))
        }
    }
}
