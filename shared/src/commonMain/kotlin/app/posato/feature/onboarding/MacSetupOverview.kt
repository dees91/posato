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
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_unified_action
import app.posato.generated.resources.mac_unified_confirm_access
import app.posato.generated.resources.mac_unified_overview_block
import app.posato.generated.resources.mac_unified_overview_label
import app.posato.generated.resources.mac_unified_overview_login
import app.posato.generated.resources.mac_unified_overview_password
import app.posato.generated.resources.mac_unified_overview_schedules
import app.posato.generated.resources.mac_unified_preview_notice
import app.posato.generated.resources.mac_unified_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MacSetupOverview(modifier: Modifier = Modifier) {
    PosatoPanel(modifier = modifier.fillMaxWidth()) {
        PosatoCaption(stringResource(Res.string.mac_unified_overview_label))
        PosatoBody(stringResource(Res.string.mac_unified_overview_block))
        PosatoBody(stringResource(Res.string.mac_unified_overview_login))
        PosatoBody(stringResource(Res.string.mac_unified_overview_password))
        PosatoCaption(stringResource(Res.string.mac_unified_overview_schedules))
    }
}

@Composable
internal fun MacSetupPreviewAction(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption(stringResource(Res.string.mac_unified_preview_notice))
        PosatoButton(onClick = {}, enabled = false) { Text(stringResource(Res.string.mac_unified_action)) }
    }
}

@Composable
internal fun MacSetupOffer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoHeading(
            stringResource(Res.string.mac_unified_title),
            description = stringResource(Res.string.mac_unified_confirm_access),
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
