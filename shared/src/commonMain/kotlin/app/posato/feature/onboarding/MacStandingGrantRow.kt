package app.posato.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoSelectionRow
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_standing_grant
import app.posato.generated.resources.mac_standing_grant_blocked
import app.posato.generated.resources.mac_standing_grant_supporting
import app.posato.generated.resources.mac_standing_grant_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StandingGrantRow(
    presentation: MacSetupPresentation,
    running: Boolean,
    sessionBlocks: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val state = presentation.standingGrant
    if (state == null || state == MacStandingGrantState.UNSUPPORTED) {
        return
    }
    val known = state != MacStandingGrantState.UNKNOWN
    PosatoSelectionRow(
        checked = state == MacStandingGrantState.ON,
        onCheckedChange = onChange,
        enabled = known && !running && !sessionBlocks && !presentation.standingGrantChanging,
        supportingContent = {
            PosatoCaption(
                stringResource(
                    when {
                        !known -> Res.string.mac_standing_grant_unknown
                        sessionBlocks -> Res.string.mac_standing_grant_blocked
                        else -> Res.string.mac_standing_grant_supporting
                    },
                ),
            )
        },
    ) {
        Text(stringResource(Res.string.mac_standing_grant), style = MaterialTheme.typography.bodyLarge)
    }
}
