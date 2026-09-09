package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoIcon
import app.posato.core.designsystem.PosatoIcons
import app.posato.core.designsystem.PosatoIntervalArtwork
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoPrivacyPoint
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.sync.ui.message
import app.posato.feature.targets.ui.TargetsBrowserState
import app.posato.feature.targets.ui.WebsiteEntry
import app.posato.generated.resources.Res
import app.posato.generated.resources.action_sync_now
import app.posato.generated.resources.action_sync_with_icloud
import app.posato.generated.resources.onboarding_action_begin
import app.posato.generated.resources.onboarding_action_continue
import app.posato.generated.resources.onboarding_action_not_now
import app.posato.generated.resources.onboarding_action_open_session
import app.posato.generated.resources.onboarding_icloud_body
import app.posato.generated.resources.onboarding_icloud_optional
import app.posato.generated.resources.onboarding_icloud_title
import app.posato.generated.resources.onboarding_permission_control
import app.posato.generated.resources.onboarding_privacy_collection
import app.posato.generated.resources.onboarding_privacy_collection_detail
import app.posato.generated.resources.onboarding_privacy_encryption
import app.posato.generated.resources.onboarding_privacy_encryption_detail
import app.posato.generated.resources.onboarding_privacy_intro
import app.posato.generated.resources.onboarding_privacy_local_apps
import app.posato.generated.resources.onboarding_privacy_local_apps_detail
import app.posato.generated.resources.onboarding_privacy_title
import app.posato.generated.resources.onboarding_purpose_body
import app.posato.generated.resources.onboarding_purpose_title
import app.posato.generated.resources.onboarding_summary_access_note
import app.posato.generated.resources.onboarding_summary_body
import app.posato.generated.resources.onboarding_summary_empty_body
import app.posato.generated.resources.onboarding_summary_helper_note
import app.posato.generated.resources.onboarding_summary_local_only
import app.posato.generated.resources.onboarding_summary_no_websites
import app.posato.generated.resources.onboarding_summary_one_website
import app.posato.generated.resources.onboarding_summary_scope
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
    OnboardingPage(
        layout = layout,
        actions = {
            OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_begin), layout, onContinue)
        },
    ) {
        PosatoIntervalArtwork()
        PosatoHeading(
            stringResource(Res.string.onboarding_purpose_title),
            description = stringResource(Res.string.onboarding_purpose_body),
            layout = layout,
        )
        PosatoCaption(stringResource(Res.string.onboarding_permission_control))
    }
}

@Composable
internal fun PrivacyStep(
    onContinue: () -> Unit,
    layout: PosatoLayout,
) {
    OnboardingPage(
        layout = layout,
        actions = {
            OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_continue), layout, onContinue)
        },
    ) {
        PosatoHeading(
            stringResource(Res.string.onboarding_privacy_title),
            description = stringResource(Res.string.onboarding_privacy_intro),
            layout = layout,
        )
        Column {
            PosatoPrivacyPoint(
                headlineContent = { Text(stringResource(Res.string.onboarding_privacy_collection)) },
                leadingContent = { PosatoIcon(PosatoIcons.Check, null) },
                supportingContent = { PosatoCaption(stringResource(Res.string.onboarding_privacy_collection_detail)) },
            )
            PosatoPrivacyPoint(
                headlineContent = { Text(stringResource(Res.string.onboarding_privacy_encryption)) },
                leadingContent = { PosatoIcon(PosatoIcons.Cloud, null) },
                supportingContent = { PosatoCaption(stringResource(Res.string.onboarding_privacy_encryption_detail)) },
            )
            PosatoPrivacyPoint(
                headlineContent = { Text(stringResource(Res.string.onboarding_privacy_local_apps)) },
                leadingContent = { PosatoIcon(PosatoIcons.Apps, null) },
                supportingContent = { PosatoCaption(stringResource(Res.string.onboarding_privacy_local_apps_detail)) },
            )
        }
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
    val running = syncRunning || syncSnapshot.status == SyncStatus.SYNCING
    OnboardingPage(
        layout = layout,
        actions = {
            if (syncSnapshot.linked) {
                OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_continue), layout, onDefer, enabled = !running)
                PosatoButton(onClick = onSync, style = PosatoButtonStyle.Quiet, enabled = !running) {
                    Text(stringResource(Res.string.action_sync_now))
                }
            } else {
                OnboardingPrimaryAction(stringResource(Res.string.action_sync_with_icloud), layout, onSync, enabled = !running)
                PosatoButton(onClick = onDefer, style = PosatoButtonStyle.Quiet, enabled = !running) {
                    Text(stringResource(Res.string.onboarding_action_not_now))
                }
            }
        },
    ) {
        PosatoHeading(
            stringResource(Res.string.onboarding_icloud_title),
            description = stringResource(Res.string.onboarding_icloud_body),
            layout = layout,
        )
        PosatoPrivacyPoint(
            headlineContent = { Text(stringResource(Res.string.onboarding_privacy_local_apps)) },
            leadingContent = { PosatoIcon(PosatoIcons.Apps, null) },
            supportingContent = { PosatoCaption(stringResource(Res.string.onboarding_privacy_local_apps_detail)) },
        )
        if (syncSnapshot.linked || syncSnapshot.status != SyncStatus.LOCAL_ONLY) {
            PosatoNotice(announceChanges = true) {
                Text(
                    stringResource(
                        if (syncSnapshot.status == SyncStatus.LOCAL_ONLY) {
                            Res.string.onboarding_summary_sync_on
                        } else {
                            syncSnapshot.status.message(syncSnapshot.linked)
                        },
                    ),
                )
            }
        }
        PosatoCaption(stringResource(Res.string.onboarding_icloud_optional))
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
    OnboardingPage(
        layout = layout,
        actions = {
            if (state.savedWebsites > 0) {
                OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_continue), layout, onContinue)
            } else {
                PosatoButton(onClick = onDefer, style = PosatoButtonStyle.Quiet) {
                    Text(stringResource(Res.string.onboarding_action_not_now))
                }
            }
        },
    ) {
        PosatoHeading(
            stringResource(Res.string.onboarding_website_title),
            description = stringResource(Res.string.onboarding_website_body),
            layout = layout,
        )
        WebsiteEntry(browser = browser, enabled = !state.websiteSaving, onSubmit = onSubmitWebsites)
        PosatoCaption(stringResource(Res.string.onboarding_permission_control))
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
    OnboardingPage(
        layout = layout,
        actions = {
            OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_open_session), layout, onFinish)
            PosatoCaption(stringResource(Res.string.onboarding_summary_scope))
        },
    ) {
        PosatoHeading(
            stringResource(Res.string.onboarding_summary_title),
            description = stringResource(
                if (state.savedWebsites > 0) Res.string.onboarding_summary_body else Res.string.onboarding_summary_empty_body,
            ),
            layout = layout,
        )
        Column(verticalArrangement = Arrangement.spacedBy(PosatoSpace.Small)) {
            PosatoPrivacyPoint(
                headlineContent = { Text(state.websiteSummary()) },
                leadingContent = { PosatoIcon(PosatoIcons.Globe, null) },
            )
            PosatoPrivacyPoint(
                headlineContent = { Text(state.permissionSummary(permissionPlatform)) },
                leadingContent = { PosatoIcon(PosatoIcons.Pause, null) },
            )
            PosatoPrivacyPoint(
                headlineContent = {
                    Text(
                        if (syncLinked) {
                            stringResource(Res.string.onboarding_summary_sync_on)
                        } else {
                            stringResource(Res.string.onboarding_summary_local_only, deviceNoun)
                        },
                    )
                },
                leadingContent = { PosatoIcon(if (syncLinked) PosatoIcons.Cloud else PosatoIcons.Items, null) },
            )
        }
        if (!state.hasDeviceAccess(permissionPlatform) && state.accessResult != ApplicationAccessResult.Unavailable) {
            PosatoNotice(tone = PosatoTone.Caution) {
                Text(
                    stringResource(
                        if (permissionPlatform == OnboardingPermissionPlatform.IOS) {
                            Res.string.onboarding_summary_access_note
                        } else {
                            Res.string.onboarding_summary_helper_note
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingViewState.websiteSummary(): String {
    return when {
        savedWebsites <= 0 -> stringResource(Res.string.onboarding_summary_no_websites)
        savedWebsites == 1 -> stringResource(Res.string.onboarding_summary_one_website)
        else -> stringResource(Res.string.onboarding_summary_websites, savedWebsites)
    }
}
