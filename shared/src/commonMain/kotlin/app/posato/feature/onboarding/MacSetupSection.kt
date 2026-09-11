package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoComponentPreview
import app.posato.core.designsystem.PosatoDisclosureRow
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoSpace
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_approval_needed
import app.posato.generated.resources.mac_setup_attention
import app.posato.generated.resources.mac_setup_check
import app.posato.generated.resources.mac_setup_description
import app.posato.generated.resources.mac_setup_needed
import app.posato.generated.resources.mac_setup_title
import app.posato.generated.resources.mac_setup_unavailable
import app.posato.generated.resources.mac_setup_unchecked
import app.posato.generated.resources.onboarding_permission_mac_action
import app.posato.generated.resources.onboarding_permission_mac_check_again
import app.posato.generated.resources.onboarding_permission_mac_open_settings
import app.posato.generated.resources.onboarding_summary_helper_on
import app.posato.generated.resources.setup_hide_options
import app.posato.generated.resources.setup_show_options
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MacSetupSection(
    presentation: MacSetupPresentation,
    onCheck: () -> Unit,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onAnnouncement: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    MacSetupAnnouncements(presentation, onAnnouncement)
    MacSetupSection(presentation, expanded, { expanded = !expanded }, onCheck, onEnable, onOpenSettings, modifier)
}

@Composable
internal fun MacSetupSection(
    presentation: MacSetupPresentation,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCheck: () -> Unit,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = presentation.activity != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoDisclosureRow(
            onClick = onToggle,
            onClickLabel = stringResource(if (expanded) Res.string.setup_hide_options else Res.string.setup_show_options),
            headlineContent = { Text(stringResource(Res.string.mac_setup_title)) },
            supportingContent = {
                PosatoCaption(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    text = stringResource(presentation.activity?.label() ?: presentation.readiness.summary()),
                )
            },
            leadingContent = { PosatoIcon(PosatoIcons.Mac, null) },
            trailingContent = { PosatoIcon(if (expanded) PosatoIcons.ChevronUp else PosatoIcons.ChevronDown, null) },
        )
        if (expanded) {
            PosatoCaption(stringResource(Res.string.mac_setup_description))
            if (!running && presentation.readiness != MacHelperReadiness.READY) {
                presentation.readiness?.let { readiness ->
                    MacHelperReadinessNotice(readiness, Res.string.mac_setup_unavailable)
                }
            }
            MacSetupActions(presentation.readiness, running, onCheck, onEnable, onOpenSettings)
        }
    }
}

private fun MacHelperReadiness?.summary(): StringResource {
    return when (this) {
        null -> Res.string.mac_setup_unchecked

        MacHelperReadiness.READY -> Res.string.onboarding_summary_helper_on

        MacHelperReadiness.NOT_ENABLED -> Res.string.mac_setup_needed

        MacHelperReadiness.APPROVAL_REQUIRED -> Res.string.mac_setup_approval_needed

        MacHelperReadiness.UNAVAILABLE,
        MacHelperReadiness.UNCERTAIN,
        MacHelperReadiness.RECOVERY_REQUIRED -> Res.string.mac_setup_attention
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
            PosatoButton(onClick = onCheck, style = PosatoButtonStyle.Secondary, enabled = !running) {
                Text(stringResource(Res.string.mac_setup_check))
            }
        }

        MacHelperReadiness.READY -> {
            CheckAgainButton(running, onCheck)
        }

        MacHelperReadiness.NOT_ENABLED -> {
            PosatoActionRow {
                PosatoButton(onClick = onEnable, style = PosatoButtonStyle.Secondary, enabled = !running) {
                    Text(stringResource(Res.string.onboarding_permission_mac_action))
                }
                CheckAgainButton(running, onCheck)
            }
        }

        MacHelperReadiness.APPROVAL_REQUIRED -> {
            PosatoActionRow {
                PosatoButton(onClick = onOpenSettings, style = PosatoButtonStyle.Secondary, enabled = !running) {
                    Text(stringResource(Res.string.onboarding_permission_mac_open_settings))
                }
                CheckAgainButton(running, onCheck)
            }
        }

        MacHelperReadiness.UNAVAILABLE,
        MacHelperReadiness.UNCERTAIN,
        MacHelperReadiness.RECOVERY_REQUIRED -> {
            CheckAgainButton(running, onCheck)
        }
    }
}

@Composable
private fun MacSetupAnnouncements(
    presentation: MacSetupPresentation,
    onAnnouncement: (String) -> Unit,
) {
    val initialCompletion = remember { presentation.completedOperations }
    val announce by rememberUpdatedState(onAnnouncement)
    val message = presentation.activity?.let { stringResource(it.label()) }
        ?: presentation.readiness?.message(Res.string.mac_setup_unavailable)
    LaunchedEffect(presentation.activity, presentation.completedOperations) {
        if (message != null && (presentation.activity != null || presentation.completedOperations != initialCompletion)) {
            announce(message)
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
            MacSetupPresentation(readiness = MacHelperReadiness.UNCERTAIN),
            MacSetupPresentation(readiness = MacHelperReadiness.RECOVERY_REQUIRED),
        ).forEach { presentation ->
            MacSetupSection(presentation, expanded = true, onToggle = {}, onCheck = {}, onEnable = {}, onOpenSettings = {})
        }
    }
}
