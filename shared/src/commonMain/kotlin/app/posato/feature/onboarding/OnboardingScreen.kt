package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoPrivacyPoint
import app.posato.core.designsystem.PosatoSetupStep
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.sync.ui.message
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsBrowserState
import app.posato.feature.targets.ui.WebsiteEntry
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.action_sync_with_icloud
import app.posato.generated.resources.application_mapping_access_denied
import app.posato.generated.resources.application_mapping_access_required
import app.posato.generated.resources.application_mapping_access_restricted
import app.posato.generated.resources.application_mapping_access_unavailable
import app.posato.generated.resources.onboarding_action_continue
import app.posato.generated.resources.onboarding_action_later
import app.posato.generated.resources.onboarding_action_not_now
import app.posato.generated.resources.onboarding_action_open_session
import app.posato.generated.resources.onboarding_action_skip
import app.posato.generated.resources.onboarding_icloud_body
import app.posato.generated.resources.onboarding_icloud_title
import app.posato.generated.resources.onboarding_permission_check_failed
import app.posato.generated.resources.onboarding_permission_ios_action
import app.posato.generated.resources.onboarding_permission_ios_body
import app.posato.generated.resources.onboarding_permission_mac_action
import app.posato.generated.resources.onboarding_permission_mac_approval
import app.posato.generated.resources.onboarding_permission_mac_body
import app.posato.generated.resources.onboarding_permission_mac_check_again
import app.posato.generated.resources.onboarding_permission_mac_open_settings
import app.posato.generated.resources.onboarding_permission_mac_unavailable
import app.posato.generated.resources.onboarding_permission_title
import app.posato.generated.resources.onboarding_privacy_accounts
import app.posato.generated.resources.onboarding_privacy_collection
import app.posato.generated.resources.onboarding_privacy_encryption
import app.posato.generated.resources.onboarding_privacy_local_apps
import app.posato.generated.resources.onboarding_privacy_title
import app.posato.generated.resources.onboarding_purpose_body
import app.posato.generated.resources.onboarding_purpose_title
import app.posato.generated.resources.onboarding_step_device
import app.posato.generated.resources.onboarding_step_icloud
import app.posato.generated.resources.onboarding_step_privacy
import app.posato.generated.resources.onboarding_step_purpose
import app.posato.generated.resources.onboarding_step_summary
import app.posato.generated.resources.onboarding_step_website
import app.posato.generated.resources.onboarding_summary_access_off
import app.posato.generated.resources.onboarding_summary_access_on
import app.posato.generated.resources.onboarding_summary_access_unchecked
import app.posato.generated.resources.onboarding_summary_helper_off
import app.posato.generated.resources.onboarding_summary_helper_on
import app.posato.generated.resources.onboarding_summary_local_only
import app.posato.generated.resources.onboarding_summary_no_websites
import app.posato.generated.resources.onboarding_summary_one_website
import app.posato.generated.resources.onboarding_summary_sync_on
import app.posato.generated.resources.onboarding_summary_title
import app.posato.generated.resources.onboarding_summary_websites
import app.posato.generated.resources.onboarding_website_body
import app.posato.generated.resources.onboarding_website_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberOnboardingUiState(
    setupStore: LocalSetupStore,
    policyStore: LocalTargetPolicyStore,
    applicationAccess: ApplicationAccessPort,
    macHelper: MacHelperPort,
): OnboardingUiState {
    val scope = rememberCoroutineScope()
    return remember(setupStore, policyStore, applicationAccess, macHelper) {
        OnboardingUiState(setupStore, policyStore, applicationAccess, macHelper, scope)
    }
}

@Composable
internal fun OnboardingScreen(
    holder: OnboardingUiState,
    syncState: SyncBootstrapUiState,
    permissionPlatform: OnboardingPermissionPlatform,
    deviceNoun: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Compact,
) {
    val browser = remember { TargetsBrowserState() }
    val syncSnapshot by syncState.syncState.collectAsState()
    OnboardingScreen(
        state = holder.snapshot(),
        syncSnapshot = syncSnapshot,
        syncRunning = syncState.running,
        browser = browser,
        permissionPlatform = permissionPlatform,
        deviceNoun = deviceNoun,
        onContinue = holder::advance,
        onDefer = holder::advance,
        onSync = syncState::sync,
        onRequestAccess = holder::requestAccess,
        onEnableHelper = holder::enableHelper,
        onRecheckHelper = holder::recheckHelper,
        onOpenHelperSettings = holder::openHelperSettings,
        onSubmitWebsites = { input, submissionId -> holder.submitWebsites(input, submissionId, browser::accept) },
        onFinish = { holder.finish(onFinished) },
        modifier = modifier,
        layout = layout,
    )
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingViewState,
    modifier: Modifier = Modifier,
    layout: PosatoLayout = PosatoLayout.Compact,
    syncSnapshot: AppleSyncState = AppleSyncState(),
    syncRunning: Boolean = false,
    browser: TargetsBrowserState = remember { TargetsBrowserState() },
    permissionPlatform: OnboardingPermissionPlatform = OnboardingPermissionPlatform.IOS,
    deviceNoun: String = "iPhone",
    onContinue: () -> Unit = {},
    onDefer: () -> Unit = {},
    onSync: () -> Unit = {},
    onRequestAccess: () -> Unit = {},
    onEnableHelper: () -> Unit = {},
    onRecheckHelper: () -> Unit = {},
    onOpenHelperSettings: () -> Unit = {},
    onSubmitWebsites: (String, Long) -> Unit = { _, _ -> },
    onFinish: () -> Unit = {},
) {
    val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
    ) {
        OnboardingProgress(current = state.step)
        when (state.step) {
            OnboardingStep.PURPOSE -> {
                PosatoHeading(stringResource(Res.string.onboarding_purpose_title), layout = layout)
                PosatoBody(stringResource(Res.string.onboarding_purpose_body))
                PosatoActionRow {
                    PosatoButton(onClick = onContinue) { Text(stringResource(Res.string.onboarding_action_continue)) }
                }
            }

            OnboardingStep.PRIVACY -> {
                PosatoHeading(stringResource(Res.string.onboarding_privacy_title), layout = layout)
                PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_accounts)) })
                PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_collection)) })
                PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_encryption)) })
                PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_local_apps)) })
                PosatoActionRow {
                    PosatoButton(onClick = onContinue) { Text(stringResource(Res.string.onboarding_action_continue)) }
                }
            }

            OnboardingStep.ICLOUD -> {
                PosatoHeading(stringResource(Res.string.onboarding_icloud_title), layout = layout)
                PosatoBody(stringResource(Res.string.onboarding_icloud_body))
                val running = syncRunning || syncSnapshot.status == SyncStatus.SYNCING
                PosatoCaption(stringResource(syncSnapshot.status.message(syncSnapshot.linked)))
                PosatoActionRow {
                    PosatoButton(onClick = onSync, enabled = !running) {
                        Text(
                            stringResource(
                                if (syncSnapshot.linked) Res.string.action_sync_now else Res.string.action_sync_with_icloud,
                            ),
                        )
                    }
                    PosatoButton(onClick = onDefer, style = PosatoButtonStyle.Quiet, enabled = !running) {
                        Text(stringResource(Res.string.onboarding_action_not_now))
                    }
                }
            }

            OnboardingStep.PERMISSION -> {
                PosatoHeading(stringResource(Res.string.onboarding_permission_title), layout = layout)
                when (permissionPlatform) {
                    OnboardingPermissionPlatform.IOS -> {
                        PosatoBody(stringResource(Res.string.onboarding_permission_ios_body))
                        PosatoButton(onClick = onRequestAccess, enabled = !state.permissionRunning) {
                            Text(stringResource(Res.string.onboarding_permission_ios_action))
                        }
                        state.accessResult?.let { result ->
                            PosatoBody(result.accessMessage())
                        }
                    }

                    OnboardingPermissionPlatform.MAC -> {
                        PosatoBody(stringResource(Res.string.onboarding_permission_mac_body))
                        PosatoButton(onClick = onEnableHelper, enabled = !state.permissionRunning) {
                            Text(stringResource(Res.string.onboarding_permission_mac_action))
                        }
                        when (state.helperReadiness) {
                            MacHelperReadiness.READY -> {
                                PosatoBody(stringResource(Res.string.onboarding_summary_helper_on))
                            }

                            MacHelperReadiness.APPROVAL_REQUIRED -> {
                                PosatoBody(stringResource(Res.string.onboarding_permission_mac_approval))
                                PosatoActionRow {
                                    PosatoButton(onClick = onOpenHelperSettings) {
                                        Text(stringResource(Res.string.onboarding_permission_mac_open_settings))
                                    }
                                    PosatoButton(onClick = onRecheckHelper, style = PosatoButtonStyle.Secondary) {
                                        Text(stringResource(Res.string.onboarding_permission_mac_check_again))
                                    }
                                }
                            }

                            MacHelperReadiness.UNAVAILABLE -> {
                                PosatoBody(stringResource(Res.string.onboarding_permission_mac_unavailable))
                            }

                            null -> {}
                        }
                    }
                }
                PosatoActionRow {
                    PosatoButton(onClick = onDefer, style = PosatoButtonStyle.Quiet) {
                        Text(stringResource(Res.string.onboarding_action_later))
                    }
                }
            }

            OnboardingStep.WEBSITE -> {
                PosatoHeading(stringResource(Res.string.onboarding_website_title), layout = layout)
                PosatoBody(stringResource(Res.string.onboarding_website_body))
                WebsiteEntry(browser = browser, enabled = !state.websiteSaving, onSubmit = onSubmitWebsites)
                PosatoActionRow {
                    if (state.savedWebsites > 0) {
                        PosatoButton(onClick = onContinue) { Text(stringResource(Res.string.onboarding_action_continue)) }
                    }
                    PosatoButton(onClick = onDefer, style = PosatoButtonStyle.Quiet) {
                        Text(stringResource(Res.string.onboarding_action_skip))
                    }
                }
            }

            OnboardingStep.SUMMARY -> {
                PosatoHeading(stringResource(Res.string.onboarding_summary_title), layout = layout)
                PosatoBody(
                    if (syncSnapshot.linked) {
                        stringResource(Res.string.onboarding_summary_sync_on)
                    } else {
                        stringResource(Res.string.onboarding_summary_local_only, deviceNoun)
                    },
                )
                PosatoBody(state.permissionSummary(permissionPlatform))
                PosatoBody(
                    when {
                        state.savedWebsites <= 0 -> stringResource(Res.string.onboarding_summary_no_websites)
                        state.savedWebsites == 1 -> stringResource(Res.string.onboarding_summary_one_website)
                        else -> stringResource(Res.string.onboarding_summary_websites, state.savedWebsites)
                    },
                )
                PosatoActionRow {
                    PosatoButton(onClick = onFinish) { Text(stringResource(Res.string.onboarding_action_open_session)) }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgress(current: OnboardingStep) {
    Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
        PosatoSetupStep("1", stringResource(Res.string.onboarding_step_purpose), current = current == OnboardingStep.PURPOSE)
        PosatoSetupStep("2", stringResource(Res.string.onboarding_step_privacy), current = current == OnboardingStep.PRIVACY)
        PosatoSetupStep("3", stringResource(Res.string.onboarding_step_icloud), current = current == OnboardingStep.ICLOUD)
        PosatoSetupStep("4", stringResource(Res.string.onboarding_step_device), current = current == OnboardingStep.PERMISSION)
        PosatoSetupStep("5", stringResource(Res.string.onboarding_step_website), current = current == OnboardingStep.WEBSITE)
        PosatoSetupStep("6", stringResource(Res.string.onboarding_step_summary), current = current == OnboardingStep.SUMMARY)
    }
}

@Composable
private fun ApplicationAccessResult.accessMessage(): String {
    return when (this) {
        is ApplicationAccessResult.Determined -> {
            when (access) {
                LocalApplicationMappingsAccess.READY -> stringResource(Res.string.onboarding_summary_access_on)
                LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED -> {
                    stringResource(Res.string.application_mapping_access_required)
                }

                LocalApplicationMappingsAccess.AUTHORIZATION_DENIED -> {
                    stringResource(Res.string.application_mapping_access_denied)
                }

                LocalApplicationMappingsAccess.RESTRICTED -> {
                    stringResource(Res.string.application_mapping_access_restricted)
                }
            }
        }

        ApplicationAccessResult.Unavailable -> stringResource(Res.string.application_mapping_access_unavailable)
        ApplicationAccessResult.Failed -> stringResource(Res.string.onboarding_permission_check_failed)
    }
}

@Composable
private fun OnboardingViewState.permissionSummary(permissionPlatform: OnboardingPermissionPlatform): String {
    return when (permissionPlatform) {
        OnboardingPermissionPlatform.IOS -> {
            when (val result = accessResult) {
                is ApplicationAccessResult.Determined -> {
                    if (result.access == LocalApplicationMappingsAccess.READY) {
                        stringResource(Res.string.onboarding_summary_access_on)
                    } else {
                        stringResource(Res.string.onboarding_summary_access_off)
                    }
                }

                ApplicationAccessResult.Unavailable -> {
                    stringResource(Res.string.application_mapping_access_unavailable)
                }

                ApplicationAccessResult.Failed -> stringResource(Res.string.onboarding_summary_access_unchecked)
                null -> stringResource(Res.string.onboarding_summary_access_off)
            }
        }

        OnboardingPermissionPlatform.MAC -> {
            when (helperReadiness) {
                MacHelperReadiness.READY -> stringResource(Res.string.onboarding_summary_helper_on)
                else -> stringResource(Res.string.onboarding_summary_helper_off)
            }
        }
    }
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingPhonePreview(
    @PreviewParameter(OnboardingPreviewDataProvider::class) previewState: OnboardingPreviewDataProvider.OnboardingPreviewState,
) {
    PosatoTheme {
        OnboardingScreen(
            previewState.state,
            syncSnapshot = previewState.sync,
            permissionPlatform = previewState.platform,
        )
    }
}

@Preview(name = "Desktop", widthDp = 1060, heightDp = 780)
@Composable
private fun OnboardingDesktopPreview(
    @PreviewParameter(OnboardingPreviewDataProvider::class) previewState: OnboardingPreviewDataProvider.OnboardingPreviewState,
) {
    PosatoTheme {
        OnboardingScreen(
            previewState.state,
            layout = PosatoLayout.Expanded,
            syncSnapshot = previewState.sync,
            permissionPlatform = previewState.platform,
        )
    }
}
