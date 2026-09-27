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
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoPanel
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.PosatoTone
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_unified_action
import app.posato.generated.resources.mac_unified_confirm_access
import app.posato.generated.resources.mac_unified_interrupted
import app.posato.generated.resources.mac_unified_next_approval
import app.posato.generated.resources.mac_unified_next_password
import app.posato.generated.resources.mac_unified_next_working
import app.posato.generated.resources.mac_unified_offer_body
import app.posato.generated.resources.mac_unified_overview_block
import app.posato.generated.resources.mac_unified_overview_label
import app.posato.generated.resources.mac_unified_overview_login
import app.posato.generated.resources.mac_unified_overview_password
import app.posato.generated.resources.mac_unified_overview_schedules
import app.posato.generated.resources.mac_unified_ready
import app.posato.generated.resources.mac_unified_running
import app.posato.generated.resources.mac_unified_session_blocks
import app.posato.generated.resources.mac_unified_status_approval
import app.posato.generated.resources.mac_unified_status_attention
import app.posato.generated.resources.mac_unified_status_done
import app.posato.generated.resources.mac_unified_status_password
import app.posato.generated.resources.mac_unified_status_working
import app.posato.generated.resources.mac_unified_title
import app.posato.generated.resources.mac_unified_try_again
import app.posato.generated.resources.onboarding_action_not_now
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MacSetupOverview(
    modifier: Modifier = Modifier,
    run: MacSetupRun? = null,
) {
    PosatoPanel(modifier = modifier.fillMaxWidth()) {
        PosatoCaption(stringResource(Res.string.mac_unified_overview_label))
        SetupEffect(Res.string.mac_unified_overview_block, run?.blocking)
        SetupEffect(Res.string.mac_unified_overview_login, run?.login)
        SetupEffect(Res.string.mac_unified_overview_password, run?.password)
        PosatoCaption(stringResource(Res.string.mac_unified_overview_schedules))
    }
}

@Composable
private fun SetupEffect(
    effect: StringResource,
    status: MacSetupStepStatus?,
) {
    PosatoBody(stringResource(effect))
    status?.statusLabel()?.let { label -> PosatoCaption(stringResource(label)) }
}

private fun MacSetupStepStatus.statusLabel(): StringResource? {
    return when (this) {
        MacSetupStepStatus.PENDING -> null
        MacSetupStepStatus.WORKING -> Res.string.mac_unified_status_working
        MacSetupStepStatus.WAITING_FOR_APPROVAL -> Res.string.mac_unified_status_approval
        MacSetupStepStatus.WAITING_FOR_PASSWORD -> Res.string.mac_unified_status_password
        MacSetupStepStatus.DONE -> Res.string.mac_unified_status_done
        MacSetupStepStatus.NEEDS_ATTENTION -> Res.string.mac_unified_status_attention
    }
}

/**
 * The one setup action. It names the single thing the person does next while it runs, offers Try
 * again after an interrupted run, and says the Mac is ready only once every step was verified.
 */
@Composable
internal fun MacSetupAction(
    presentation: MacSetupPresentation,
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
    sessionBlocks: Boolean = false,
    style: PosatoButtonStyle = PosatoButtonStyle.Primary,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        val run = presentation.setup
        val blocked = sessionBlocks || presentation.sessionBlocked
        when {
            presentation.setupComplete -> {
                PosatoNotice(tone = PosatoTone.Positive) {
                    Text(stringResource(Res.string.mac_unified_ready))
                }
            }

            run?.running == true -> {
                PosatoCaption(stringResource(run.nextStep()))
                PosatoButton(onClick = {}, enabled = false) { Text(stringResource(Res.string.mac_unified_running)) }
            }

            else -> {
                val interrupted = run != null && run.unfinished()
                if (interrupted) {
                    PosatoCaption(stringResource(Res.string.mac_unified_interrupted))
                }
                if (blocked) {
                    PosatoCaption(stringResource(Res.string.mac_unified_session_blocks))
                }
                PosatoButton(onClick = onSetUp, style = style, enabled = !blocked) {
                    Text(stringResource(if (interrupted) Res.string.mac_unified_try_again else Res.string.mac_unified_action))
                }
            }
        }
    }
}

private fun MacSetupRun.unfinished(): Boolean {
    return listOf(blocking, login, password).any { it != MacSetupStepStatus.DONE }
}

private fun MacSetupRun.nextStep(): StringResource {
    return when {
        blocking == MacSetupStepStatus.WAITING_FOR_APPROVAL -> Res.string.mac_unified_next_approval
        password == MacSetupStepStatus.WAITING_FOR_PASSWORD -> Res.string.mac_unified_next_password
        else -> Res.string.mac_unified_next_working
    }
}

@Composable
internal fun MacSetupOffer(
    presentation: MacSetupPresentation,
    onSetUp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Medium)) {
        PosatoHeading(
            stringResource(Res.string.mac_unified_title),
            description = stringResource(Res.string.mac_unified_offer_body),
            layout = PosatoLayout.Compact,
        )
        MacSetupOverview(run = presentation.setup)
        MacSetupAction(presentation, onSetUp, style = PosatoButtonStyle.Secondary)
        if (presentation.setup?.running != true) {
            PosatoButton(onClick = onDismiss, style = PosatoButtonStyle.Quiet) { Text(stringResource(Res.string.onboarding_action_not_now)) }
        }
    }
}

@Preview(name = "Mac setup upgrade offer", widthDp = 600, heightDp = 800)
@Composable
private fun MacSetupOfferPreview() {
    PosatoTheme { MacSetupOffer(MacSetupPresentation(), onSetUp = {}, onDismiss = {}) }
}

/** A ready helper whose login or password step is still open keeps the one setup action in This Mac. */
internal fun MacSetupPresentation.offersSetUp(): Boolean {
    return readiness == MacHelperReadiness.READY && !setupComplete && standingGrant != MacStandingGrantState.UNSUPPORTED
}
