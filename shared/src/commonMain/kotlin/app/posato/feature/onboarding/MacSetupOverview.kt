package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme

@Composable
internal fun MacSetupOverview(modifier: Modifier = Modifier) {
    PosatoPanel(modifier = modifier.fillMaxWidth()) {
        PosatoCaption("ONE-TIME SETUP")
        PosatoBody("Block your chosen websites and apps.")
        PosatoBody("Start quietly at login, without opening a window.")
        PosatoBody("Start manual pauses and schedules without repeated passwords.")
        PosatoCaption("Schedules can also start when you sign in or wake this Mac during a scheduled pause.")
    }
}

@Composable
internal fun MacSetupPreviewAction(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption("Unified setup is coming soon. This preview does not change your Mac settings.")
        PosatoButton(onClick = {}, enabled = false) { Text("Set up Posato") }
    }
}

@Composable
internal fun MacSetupOffer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoHeading(
            "Set up Posato on this Mac.",
            description = "One setup for manual pauses and schedules. You can review or revoke access in This Mac settings.",
            layout = PosatoLayout.Compact,
        )
        MacSetupOverview()
        MacSetupPreviewAction()
        PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text("Not now") }
    }
}

@Preview(name = "Mac setup upgrade offer", widthDp = 600, heightDp = 800)
@Composable
private fun MacSetupOfferPreview() {
    PosatoTheme { MacSetupOffer(onDismiss = {}) }
}
