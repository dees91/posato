package app.posato.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import app.posato.core.designsystem.PosatoActionRow
import app.posato.core.designsystem.PosatoBody
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoPrivacyPoint
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.ui.message
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
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
import app.posato.generated.resources.onboarding_privacy_accounts
import app.posato.generated.resources.onboarding_privacy_collection
import app.posato.generated.resources.onboarding_privacy_encryption
import app.posato.generated.resources.onboarding_privacy_local_apps
import app.posato.generated.resources.onboarding_privacy_title
import app.posato.generated.resources.onboarding_purpose_body
import app.posato.generated.resources.onboarding_purpose_title
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
internal fun PurposeStep(
    onContinue: () -> Unit,
    layout: PosatoLayout,
) {
    PosatoHeading(stringResource(Res.string.onboarding_purpose_title), layout = layout)
    PosatoBody(stringResource(Res.string.onboarding_purpose_body))
    PosatoActionRow {
        PosatoButton(onClick = onContinue) { Text(stringResource(Res.string.onboarding_action_continue)) }
    }
}

@Composable
internal fun PrivacyStep(
    onContinue: () -> Unit,
    layout: PosatoLayout,
) {
    PosatoHeading(stringResource(Res.string.onboarding_privacy_title), layout = layout)
    PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_accounts)) })
    PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_collection)) })
    PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_encryption)) })
    PosatoPrivacyPoint(headlineContent = { Text(stringResource(Res.string.onboarding_privacy_local_apps)) })
    PosatoActionRow {
        PosatoButton(onClick = onContinue) { Text(stringResource(Res.string.onboarding_action_continue)) }
    }
}

@Composable
internal fun IcloudStep(
    syncSnapshot: AppleSyncState,
    syncRunning: Boolean,
    onSync: () -> Unit,
    onDefer: () -> Unit,
    layout: PosatoLayout,
) {
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

@Composable
internal fun IosPermissionStep(
    accessResult: ApplicationAccessResult?,
    permissionRunning: Boolean,
    onRequestAccess: () -> Unit,
) {
    PosatoBody(stringResource(Res.string.onboarding_permission_ios_body))
    PosatoButton(onClick = onRequestAccess, enabled = !permissionRunning) {
        Text(stringResource(Res.string.onboarding_permission_ios_action))
    }
    accessResult?.let { result ->
        PosatoBody(result.accessMessage())
    }
}

@Composable
internal fun MacPermissionStep(
    helperReadiness: MacHelperReadiness?,
    permissionRunning: Boolean,
    onEnableHelper: () -> Unit,
    onRecheckHelper: () -> Unit,
    onOpenHelperSettings: () -> Unit,
) {
    PosatoBody(stringResource(Res.string.onboarding_permission_mac_body))
    PosatoButton(onClick = onEnableHelper, enabled = !permissionRunning) {
        Text(stringResource(Res.string.onboarding_permission_mac_action))
    }
    when (helperReadiness) {
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

@Composable
internal fun WebsiteStep(
    state: OnboardingViewState,
    browser: TargetsBrowserState,
    onSubmitWebsites: (String, Long) -> Unit,
    onContinue: () -> Unit,
    onDefer: () -> Unit,
    layout: PosatoLayout,
) {
    val focus = LocalFocusManager.current
    LaunchedEffect(state.savedWebsites) {
        if (state.savedWebsites > 0) {
            focus.clearFocus()
        }
    }
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

@Composable
internal fun SummaryStep(
    state: OnboardingViewState,
    permissionPlatform: OnboardingPermissionPlatform,
    deviceNoun: String,
    syncLinked: Boolean,
    onFinish: () -> Unit,
    layout: PosatoLayout,
) {
    PosatoHeading(stringResource(Res.string.onboarding_summary_title), layout = layout)
    PosatoBody(
        if (syncLinked) {
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

@Composable
internal fun ApplicationAccessResult.accessMessage(): String {
    return when (this) {
        is ApplicationAccessResult.Determined -> {
            when (access) {
                LocalApplicationMappingsAccess.READY -> {
                    stringResource(Res.string.onboarding_summary_access_on)
                }

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

        ApplicationAccessResult.Unavailable -> {
            stringResource(Res.string.application_mapping_access_unavailable)
        }

        ApplicationAccessResult.Failed -> {
            stringResource(Res.string.onboarding_permission_check_failed)
        }
    }
}

@Composable
internal fun OnboardingViewState.permissionSummary(permissionPlatform: OnboardingPermissionPlatform): String {
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

                ApplicationAccessResult.Failed -> {
                    stringResource(Res.string.onboarding_summary_access_unchecked)
                }

                null -> {
                    stringResource(Res.string.onboarding_summary_access_off)
                }
            }
        }

        OnboardingPermissionPlatform.MAC -> {
            when (helperReadiness) {
                MacHelperReadiness.READY -> {
                    stringResource(Res.string.onboarding_summary_helper_on)
                }

                else -> {
                    stringResource(Res.string.onboarding_summary_helper_off)
                }
            }
        }
    }
}
