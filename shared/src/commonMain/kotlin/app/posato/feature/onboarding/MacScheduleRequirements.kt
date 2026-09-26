package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSelectionRow
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme

@Composable
internal fun MacScheduleRequirements(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoCaption("REQUIRED FOR SCHEDULES")
        Text("Before you create a schedule on this Mac, enable both settings.")
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = false,
            onCheckedChange = {},
            enabled = false,
            supportingContent = { PosatoCaption("Required so schedules can resume after you restart and sign in to this Mac.") },
        ) { Text("Open Posato at login") }
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = false,
            onCheckedChange = {},
            enabled = false,
            supportingContent = {
                PosatoCaption("Required so scheduled pauses can start automatically, including during an interval after sign-in or wake.")
            },
        ) { Text("Start sessions without the password") }
        PosatoCaption("Schedule setup is coming soon. These controls are inactive and do not show your current settings.")
    }
}

@Composable
internal fun MacSetupOffer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosatoPanel(modifier = modifier) {
        PosatoHeading("Prepare this Mac for schedules.", layout = PosatoLayout.Compact)
        MacScheduleRequirements()
        PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text("Not now") }
    }
}

@Preview(name = "Mac setup upgrade offer", widthDp = 600, heightDp = 600)
@Composable
private fun MacSetupOfferPreview() {
    PosatoTheme { MacSetupOffer(onDismiss = {}) }
}
