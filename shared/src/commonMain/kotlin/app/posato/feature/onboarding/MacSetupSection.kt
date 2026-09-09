package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoComponentPreview
import app.posato.core.designsystem.PosatoEyebrow
import app.posato.core.designsystem.PosatoSpace
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_check
import app.posato.generated.resources.mac_setup_checking
import app.posato.generated.resources.mac_setup_description
import app.posato.generated.resources.mac_setup_enabling
import app.posato.generated.resources.mac_setup_title
import app.posato.generated.resources.mac_setup_unavailable
import app.posato.generated.resources.onboarding_permission_mac_action
import app.posato.generated.resources.onboarding_permission_mac_check_again
import app.posato.generated.resources.onboarding_permission_mac_open_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MacSetupSection(
    presentation: MacSetupPresentation,
    onCheck: () -> Unit,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = presentation.activity != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoEyebrow(stringResource(Res.string.mac_setup_title))
        PosatoCaption(stringResource(presentation.activity?.label() ?: Res.string.mac_setup_description))
        presentation.readiness?.let { readiness ->
            MacHelperReadinessNotice(readiness, Res.string.mac_setup_unavailable)
        }
        MacSetupActions(presentation.readiness, running, onCheck, onEnable, onOpenSettings)
    }
}

@Composable
private fun MacSetupActions(
    readiness: MacHelperReadiness?,
    running: Boolean,
    onCheck: () -> Unit,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (readiness) {
        null -> {
            PosatoButton(onClick = onCheck, enabled = !running) { Text(stringResource(Res.string.mac_setup_check)) }
        }

        MacHelperReadiness.READY -> {
            CheckAgainButton(running, onCheck)
        }

        MacHelperReadiness.NOT_ENABLED -> {
            PosatoActionRow {
                PosatoButton(onClick = onEnable, enabled = !running) { Text(stringResource(Res.string.onboarding_permission_mac_action)) }
                CheckAgainButton(running, onCheck)
            }
        }

        MacHelperReadiness.APPROVAL_REQUIRED -> {
            PosatoActionRow {
                PosatoButton(onClick = onOpenSettings, enabled = !running) {
                    Text(stringResource(Res.string.onboarding_permission_mac_open_settings))
                }
                CheckAgainButton(running, onCheck)
            }
        }

        MacHelperReadiness.UNAVAILABLE -> {
            CheckAgainButton(running, onCheck)
        }
    }
}

@Composable
private fun CheckAgainButton(
    running: Boolean,
    onCheck: () -> Unit,
) {
    PosatoButton(onClick = onCheck, style = PosatoButtonStyle.Quiet, enabled = !running) {
        Text(stringResource(Res.string.onboarding_permission_mac_check_again))
    }
}

private fun MacSetupActivity.label(): StringResource {
    return when (this) {
        MacSetupActivity.CHECKING -> Res.string.mac_setup_checking
        MacSetupActivity.ENABLING -> Res.string.mac_setup_enabling
    }
}

@Preview
@Composable
private fun MacSetupSectionPreview() {
    PosatoComponentPreview {
        listOf(
            MacSetupPresentation(),
            MacSetupPresentation(activity = MacSetupActivity.CHECKING),
            MacSetupPresentation(readiness = MacHelperReadiness.READY),
            MacSetupPresentation(readiness = MacHelperReadiness.NOT_ENABLED),
            MacSetupPresentation(readiness = MacHelperReadiness.APPROVAL_REQUIRED),
            MacSetupPresentation(readiness = MacHelperReadiness.UNAVAILABLE),
        ).forEach { presentation ->
            MacSetupSection(presentation, onCheck = {}, onEnable = {}, onOpenSettings = {})
        }
    }
}
