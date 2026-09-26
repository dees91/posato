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
internal fun MacOptionalSetup(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoCaption("Make Posato ready when you need it")
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = false,
            onCheckedChange = {},
            enabled = false,
            supportingContent = { PosatoCaption("Keep Posato ready after you sign in. You can change this later in This Mac.") },
        ) { Text("Open Posato at login") }
        PosatoSelectionRow(
            modifier = Modifier.fillMaxWidth(),
            checked = false,
            onCheckedChange = {},
            enabled = false,
            supportingContent = { PosatoCaption("Approve once with an administrator password, then start sessions without another prompt.") },
        ) { Text("Start sessions without the password") }
        PosatoCaption("These optional setup shortcuts are coming soon. For now, you can change both settings in This Mac.")
    }
}

@Composable
internal fun MacSetupOffer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosatoPanel(modifier = modifier) {
        PosatoHeading("A little less setup next time.", layout = PosatoLayout.Compact)
        MacOptionalSetup()
        PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text("Not now") }
    }
}

@Preview(name = "Mac setup upgrade offer", widthDp = 600, heightDp = 600)
@Composable
private fun MacSetupOfferPreview() {
    PosatoTheme { MacSetupOffer(onDismiss = {}) }
}
