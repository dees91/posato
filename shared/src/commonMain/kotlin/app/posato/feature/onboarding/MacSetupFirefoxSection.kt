package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.generated.resources.Res
import app.posato.generated.resources.firefox_extension_active
import app.posato.generated.resources.firefox_extension_body
import app.posato.generated.resources.firefox_extension_install
import app.posato.generated.resources.firefox_extension_unverified
import app.posato.generated.resources.onboarding_permission_mac_check_again
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FirefoxExtensionRow(
    firefox: MacFirefoxUi?,
    onInstall: () -> Unit,
    onRecheck: () -> Unit,
) {
    val state = firefox?.takeIf { it.detected } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoCaption(stringResource(Res.string.firefox_extension_body))
        if (state.verified) {
            PosatoNotice(tone = PosatoTone.Positive) {
                Text(stringResource(Res.string.firefox_extension_active))
            }
        } else {
            PosatoCaption(stringResource(Res.string.firefox_extension_unverified))
            PosatoActionRow {
                PosatoButton(onClick = onInstall, style = PosatoButtonStyle.Secondary, enabled = !state.checking) {
                    Text(stringResource(Res.string.firefox_extension_install))
                }
                PosatoButton(onClick = onRecheck, style = PosatoButtonStyle.Quiet, enabled = !state.checking) {
                    Text(stringResource(Res.string.onboarding_permission_mac_check_again))
                }
            }
        }
    }
}
