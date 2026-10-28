package app.posato.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSpace
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_confirm_end_session
import app.posato.generated.resources.action_keep_this_pause
import app.posato.generated.resources.session_end_early_body
import app.posato.generated.resources.session_end_this_pause
import app.posato.generated.resources.session_nothing_restricted
import app.posato.generated.resources.session_ready_return
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionEarlyEndContent(
    state: SessionUiState,
    layout: PosatoLayout,
    onConfirmEarlyEnd: () -> Unit,
    onCancelEarlyEnd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section)) {
        PosatoHeading(
            stringResource(Res.string.session_ready_return),
            eyebrow = stringResource(Res.string.session_end_this_pause),
            description = if (state.nothingIsRestricted()) {
                stringResource(Res.string.session_nothing_restricted)
            } else {
                stringResource(Res.string.session_end_early_body)
            },
            layout = layout,
        )
        PosatoButton(
            onConfirmEarlyEnd,
            enabled = !state.isEnding,
            controlId = "action_confirm_end_session",
        ) { Text(stringResource(Res.string.action_confirm_end_session)) }
        PosatoButton(
            onCancelEarlyEnd,
            style = PosatoButtonStyle.Quiet,
            enabled = !state.isEnding,
            controlId = "action_keep_this_pause",
        ) { Text(stringResource(Res.string.action_keep_this_pause)) }
    }
}
