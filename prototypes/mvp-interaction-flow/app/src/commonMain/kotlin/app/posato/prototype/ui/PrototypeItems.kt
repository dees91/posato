package app.posato.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.posato.prototype.designsystem.PosatoButton
import app.posato.prototype.designsystem.PosatoButtonStyle
import app.posato.prototype.designsystem.PosatoCaption
import app.posato.prototype.designsystem.PosatoLayout
import app.posato.prototype.designsystem.PosatoSectionHeader
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
    browser: PrototypeItemBrowserState,
    onAction: (PrototypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val setup = state.surface == PrototypeSurface.Targets
    val canFinish = state.policy.domains.isNotEmpty() && state.localApplications().isNotEmpty()
    val compactInput = layout == PosatoLayout.Compact && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        if (!compactInput) {
            PosatoSectionHeader(
                titleContent = { Text(if (setup) "Make room for a pause." else "Paused items", style = MaterialTheme.typography.headlineSmall) },
                actionContent = {
                    if (!setup) {
                        PosatoButton(onClick = { onAction(ItemAction.CloseItems) }, style = PosatoButtonStyle.Quiet) { Text("Done") }
                    }
                },
            )
        }
        if (setup && !compactInput) PosatoCaption("Choose a website and an app. You can change these after setup.")
        PrototypeItemBrowser(
            modifier = Modifier.weight(1f),
            state = state,
            onAction = onAction,
            browser = browser,
        )
        if (setup) {
            PosatoButton(onClick = { onAction(SetupAction.FinishOnboarding) }, enabled = canFinish) { Text("Finish setup") }
            if (!canFinish) PosatoCaption("For this walkthrough, choose at least one website and one app.")
        }
    }
}
