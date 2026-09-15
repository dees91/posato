package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.generated.resources.Res
import app.posato.generated.resources.mac_setup_approval_needed
import app.posato.generated.resources.mac_setup_attention
import app.posato.generated.resources.mac_setup_check
import app.posato.generated.resources.mac_setup_description
import app.posato.generated.resources.mac_setup_keep_helper
import app.posato.generated.resources.mac_setup_needed
import app.posato.generated.resources.mac_setup_recovery
import app.posato.generated.resources.mac_setup_recovery_summary
import app.posato.generated.resources.mac_setup_remove
import app.posato.generated.resources.mac_setup_remove_again
import app.posato.generated.resources.mac_setup_remove_approval
import app.posato.generated.resources.mac_setup_remove_blocked
import app.posato.generated.resources.mac_setup_remove_check_again
import app.posato.generated.resources.mac_setup_remove_confirmation
import app.posato.generated.resources.mac_setup_remove_not_enabled
import app.posato.generated.resources.mac_setup_remove_proxy
import app.posato.generated.resources.mac_setup_remove_uncertain
import app.posato.generated.resources.mac_setup_removed
import app.posato.generated.resources.mac_setup_title
import app.posato.generated.resources.mac_setup_unavailable
import app.posato.generated.resources.mac_setup_uncertain_summary
import app.posato.generated.resources.mac_setup_unchanged
import app.posato.generated.resources.mac_setup_unchecked
import app.posato.generated.resources.onboarding_permission_mac_action
import app.posato.generated.resources.onboarding_permission_mac_check_again
import app.posato.generated.resources.onboarding_permission_mac_open_settings
import app.posato.generated.resources.onboarding_summary_helper_on
import app.posato.generated.resources.setup_hide_options
import app.posato.generated.resources.setup_show_options
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val DISABLED_CONTENT_ALPHA = 0.5f

private val removableReadiness = setOf(
    MacHelperReadiness.READY,
    MacHelperReadiness.APPROVAL_REQUIRED,
    MacHelperReadiness.UNAVAILABLE,
    MacHelperReadiness.UNCERTAIN,
)

@Composable
internal fun MacSetupSection(
    presentation: MacSetupPresentation,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCheck: () -> Unit,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onAnnouncement: (String) -> Unit = {},
    sessionBlocksRemoval: Boolean = false,
    onRemove: () -> Unit = {},
) {
    val running = presentation.activity != null
    MacSetupAnnouncements(presentation, onAnnouncement)
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
            MacSetupStateNotice(presentation, running)
            if (!running && presentation.repeatedResult && presentation.readiness.escalatesRepeat()) {
                PosatoCaption(stringResource(Res.string.mac_setup_unchanged))
            }
            MacSetupActions(presentation.readiness, running, onCheck, onEnable, onOpenSettings)
            if (presentation.readiness in removableReadiness) {
                RemoveFromMacAction(running, sessionBlocksRemoval, onRemove)
            }
        }
    }
}

@Composable
private fun MacSetupStateNotice(
    presentation: MacSetupPresentation,
    running: Boolean,
) {
    if (running) {
        return
    }
    val removal = presentation.removal
    val readiness = presentation.readiness
    if (removal != null) {
        PosatoNotice(tone = if (removal == MacHelperRemoval.REMOVED) PosatoTone.Positive else PosatoTone.Caution) {
            Text(stringResource(removal.message()))
        }
    } else if (readiness != null && readiness != MacHelperReadiness.READY) {
        MacHelperReadinessNotice(readiness, Res.string.mac_setup_unavailable)
    }
}

internal fun MacHelperRemoval.message(): StringResource {
    return when (this) {
        MacHelperRemoval.REMOVED -> Res.string.mac_setup_removed
        MacHelperRemoval.REMOVE_AGAIN -> Res.string.mac_setup_remove_again
        MacHelperRemoval.UNCERTAIN -> Res.string.mac_setup_remove_uncertain
        MacHelperRemoval.CHECK_AGAIN -> Res.string.mac_setup_remove_check_again
        MacHelperRemoval.CANNOT_START -> Res.string.mac_setup_recovery
        MacHelperRemoval.APPROVAL_REQUIRED -> Res.string.mac_setup_remove_approval
        MacHelperRemoval.NOT_ENABLED -> Res.string.mac_setup_remove_not_enabled
        MacHelperRemoval.PROXY_ATTENTION -> Res.string.mac_setup_remove_proxy
    }
}

private fun MacHelperReadiness?.summary(): StringResource {
    return when (this) {
        null -> Res.string.mac_setup_unchecked
        MacHelperReadiness.READY -> Res.string.onboarding_summary_helper_on
        MacHelperReadiness.NOT_ENABLED -> Res.string.mac_setup_needed
        MacHelperReadiness.APPROVAL_REQUIRED -> Res.string.mac_setup_approval_needed
        MacHelperReadiness.UNCERTAIN -> Res.string.mac_setup_uncertain_summary
        MacHelperReadiness.RECOVERY_REQUIRED -> Res.string.mac_setup_recovery_summary
        MacHelperReadiness.UNAVAILABLE -> Res.string.mac_setup_attention
    }
}

/**
 * States whose action cannot change the answer. An unfinished request is excluded: its retry really
 * does reconcile the original request, and approval and not-enabled both still have a real action.
 */
private fun MacHelperReadiness?.escalatesRepeat(): Boolean {
    return this == MacHelperReadiness.RECOVERY_REQUIRED || this == MacHelperReadiness.UNAVAILABLE
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
            PosatoButton(onClick = onEnable, style = PosatoButtonStyle.Secondary, enabled = !running) {
                Text(stringResource(Res.string.onboarding_permission_mac_action))
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
private fun RemoveFromMacAction(
    running: Boolean,
    sessionBlocksRemoval: Boolean,
    onRemove: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val blocked by rememberUpdatedState(sessionBlocksRemoval)
    val remove by rememberUpdatedState(onRemove)
    LaunchedEffect(sessionBlocksRemoval) {
        if (sessionBlocksRemoval) {
            confirming = false
        }
    }
    val enabled = !running && !sessionBlocksRemoval
    val error = MaterialTheme.colorScheme.error
    PosatoButton(onClick = { confirming = true }, style = PosatoButtonStyle.Quiet, enabled = enabled) {
        Text(stringResource(Res.string.mac_setup_remove), color = if (enabled) error else error.copy(alpha = DISABLED_CONTENT_ALPHA))
    }
    if (sessionBlocksRemoval) {
        PosatoCaption(stringResource(Res.string.mac_setup_remove_blocked))
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.mac_setup_remove)) },
            text = { Text(stringResource(Res.string.mac_setup_remove_confirmation)) },
            confirmButton = {
                PosatoButton(onClick = {
                    confirming = false
                    if (!blocked) {
                        remove()
                    }
                }, style = PosatoButtonStyle.Destructive) { Text(stringResource(Res.string.mac_setup_remove)) }
            },
            dismissButton = {
                PosatoButton(onClick = { confirming = false }, style = PosatoButtonStyle.Quiet) {
                    Text(stringResource(Res.string.mac_setup_keep_helper))
                }
            },
        )
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
        ?: presentation.removal?.let { stringResource(it.message()) }
        ?: presentation.readiness?.message(Res.string.mac_setup_unavailable)?.let { state ->
            if (presentation.repeatedResult && presentation.readiness.escalatesRepeat()) {
                "$state ${stringResource(Res.string.mac_setup_unchanged)}"
            } else {
                state
            }
        }
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
            MacSetupPresentation(readiness = MacHelperReadiness.RECOVERY_REQUIRED, repeatedResult = true),
            MacSetupPresentation(readiness = MacHelperReadiness.READY, activity = MacSetupActivity.REMOVING),
            MacSetupPresentation(readiness = MacHelperReadiness.NOT_ENABLED, removal = MacHelperRemoval.REMOVED),
            MacSetupPresentation(readiness = MacHelperReadiness.READY, removal = MacHelperRemoval.REMOVE_AGAIN),
            MacSetupPresentation(readiness = MacHelperReadiness.READY, removal = MacHelperRemoval.PROXY_ATTENTION),
        ).forEach { presentation ->
            MacSetupSection(presentation, expanded = true, onToggle = {}, onCheck = {}, onEnable = {}, onOpenSettings = {})
        }
        MacSetupSection(
            MacSetupPresentation(readiness = MacHelperReadiness.READY),
            expanded = true,
            onToggle = {},
            onCheck = {},
            onEnable = {},
            onOpenSettings = {},
            sessionBlocksRemoval = true,
        )
    }
}
