package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSpace
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.MacSetupAction
import app.posato.feature.onboarding.MacSetupOverview
import app.posato.feature.onboarding.MacSetupPresentation
import app.posato.feature.onboarding.MacSetupSection
import app.posato.feature.onboarding.needsSetup
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_unified_body
import app.posato.generated.resources.mac_unified_start_without
import app.posato.generated.resources.mac_unified_title
import app.posato.generated.resources.onboarding_action_continue
import org.jetbrains.compose.resources.stringResource

internal fun SessionUiState.showsMacSetup(macSetup: MacSetupPresentation): Boolean {
    val entered = macSetup.needsSetup() || macSetup.setup?.running == true || macSetup.setupOpen
    return (isSettingUp || isReviewing) && !isStarting && entered
}

/** Every Mac setup action Session offers, so screens pass one value instead of a dozen callbacks. */
@Immutable
internal class MacSetupCallbacks(
    val check: () -> Unit = {},
    val enable: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val announce: (String) -> Unit = {},
    val remove: () -> Unit = {},
    val loginItemChange: (Boolean) -> Unit = {},
    val standingGrantChange: (Boolean) -> Unit = {},
    val setUp: () -> Unit = {},
    val dismissOffer: () -> Unit = {},
    val open: () -> Unit = {},
    val leave: () -> Unit = {},
)

internal fun MacHelperSetupUiState.callbacks(
    sessionBlocked: Boolean,
    onAnnouncement: (String) -> Unit,
): MacSetupCallbacks {
    return MacSetupCallbacks(
        check = ::check,
        enable = ::enable,
        openSettings = ::openSettings,
        announce = onAnnouncement,
        remove = { remove(sessionBlocked) },
        loginItemChange = { enabled -> loginItem?.setEnabled(enabled) },
        standingGrantChange = { enabled -> setStandingGrant(enabled, sessionBlocked) },
        setUp = { setUp(sessionBlocked) },
        dismissOffer = ::dismissOffer,
        open = { setupOpen = true },
        leave = {
            deferSetup()
            setupOpen = false
        },
    )
}

/**
 * The setup screen stays until the person leaves it: Back returns to Session, Continue follows a
 * verified finish, and Start without finishing keeps the manual pause available when blocking works.
 * Leaving stops a run before its next approval or password step.
 */
@Composable
internal fun SessionMacSetup(
    state: SessionUiState,
    macSetup: MacSetupPresentation,
    layout: PosatoLayout,
    actions: MacSetupCallbacks,
    onBack: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.open() }
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            stringResource(Res.string.mac_unified_title),
            eyebrow = "FINISH SETUP",
            description = stringResource(Res.string.mac_unified_body),
            layout = layout,
        )
        MacSetupOverview(run = macSetup.setup)
        MacSetupAction(macSetup, actions.setUp, sessionBlocks = state.blocksHelperRemoval())
        val idle = macSetup.setup?.running != true
        if (macSetup.setupComplete) {
            PosatoButton(onClick = actions.leave) { Text(stringResource(Res.string.onboarding_action_continue)) }
        } else if (idle && macSetup.readiness == MacHelperReadiness.READY) {
            PosatoButton(onClick = actions.leave, style = PosatoButtonStyle.Quiet) { Text(stringResource(Res.string.mac_unified_start_without)) }
        }
        MacSetupSection(
            presentation = macSetup,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            onCheck = actions.check,
            onEnable = actions.enable,
            onOpenSettings = actions.openSettings,
            onAnnouncement = actions.announce,
            sessionBlocksRemoval = state.blocksHelperRemoval(),
            onRemove = actions.remove,
        )
        PosatoButton(onClick = {
            actions.leave()
            onBack()
        }, style = PosatoButtonStyle.Quiet) { Text("Back to Session") }
    }
}
