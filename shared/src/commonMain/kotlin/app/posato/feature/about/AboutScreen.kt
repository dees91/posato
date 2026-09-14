package app.posato.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme

@Composable
internal fun AboutScreen(
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val version = remember { applicationVersion() }
    AboutScreen(version, onOpenLicenses, onBack, modifier)
}

@Composable
internal fun AboutScreen(
    version: String?,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoButton(onClick = onBack, style = PosatoButtonStyle.Quiet) { Text("Back") }
        Text("About Posato", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
        ) {
            PosatoCaption(version?.let { "Version $it" } ?: "Version unavailable")
            PosatoBody("Pause. Then choose.")
            PosatoBody("Posato helps you step away from selected websites and apps for a while. A quiet pause between impulse and action.")
            PosatoBody("Open source. No Posato account, analytics, or Posato-operated server.")
            PosatoDisclosureRow(
                onClick = onOpenLicenses,
                headlineContent = { Text("Licenses", style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { PosatoCaption("License and third-party notices") },
            )
        }
    }
}

@Preview(name = "About · iPhone", widthDp = 390, heightDp = 780)
@Composable
private fun AboutPhonePreview(
    @PreviewParameter(AboutScreenPreviewDataProvider::class) version: String?
) {
    PosatoTheme { AboutScreen(version, {}, {}, Modifier.padding(PosatoSpace.Section)) }
}

@Preview(name = "About · Mac", widthDp = 820, heightDp = 780, uiMode = 0x20)
@Composable
private fun AboutMacPreview(
    @PreviewParameter(AboutScreenPreviewDataProvider::class) version: String?
) {
    PosatoTheme { AboutScreen(version, {}, {}, Modifier.padding(PosatoSpace.Canvas)) }
}
