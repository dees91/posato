package app.posato.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNotice
import app.posato.core.designsystem.PosatoTone
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.generated.resources.Res
import app.posato.generated.resources.application_mapping_access_restricted
import app.posato.generated.resources.onboarding_action_continue
import app.posato.generated.resources.onboarding_action_not_now
import app.posato.generated.resources.onboarding_permission_check_failed
import app.posato.generated.resources.onboarding_permission_control
import app.posato.generated.resources.onboarding_permission_defer
import app.posato.generated.resources.onboarding_permission_denied
import app.posato.generated.resources.onboarding_permission_ios_action
import app.posato.generated.resources.onboarding_permission_ios_body
import app.posato.generated.resources.onboarding_permission_mac_action
import app.posato.generated.resources.onboarding_permission_mac_admin
import app.posato.generated.resources.onboarding_permission_mac_approval
import app.posato.generated.resources.onboarding_permission_mac_body
import app.posato.generated.resources.onboarding_permission_mac_check_again
import app.posato.generated.resources.onboarding_permission_mac_open_settings
import app.posato.generated.resources.onboarding_permission_mac_unavailable
import app.posato.generated.resources.onboarding_permission_required
import app.posato.generated.resources.onboarding_permission_title
import app.posato.generated.resources.onboarding_permission_unavailable
import app.posato.generated.resources.onboarding_summary_access_off
import app.posato.generated.resources.onboarding_summary_access_on
import app.posato.generated.resources.onboarding_summary_access_unchecked
import app.posato.generated.resources.onboarding_summary_helper_off
import app.posato.generated.resources.onboarding_summary_helper_on
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ApplicationAccessResult.accessMessage(): String {
    return when (this) {
        is ApplicationAccessResult.Determined -> {
            when (access) {
                LocalApplicationMappingsAccess.READY -> {
                    stringResource(Res.string.onboarding_summary_access_on)
                }

                LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED -> {
                    stringResource(Res.string.onboarding_permission_required)
                }

                LocalApplicationMappingsAccess.AUTHORIZATION_DENIED -> {
                    stringResource(Res.string.onboarding_permission_denied)
                }

                LocalApplicationMappingsAccess.RESTRICTED -> {
                    stringResource(Res.string.application_mapping_access_restricted)
                }
            }
        }

        ApplicationAccessResult.Unavailable -> {
            stringResource(Res.string.onboarding_permission_unavailable)
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
                    stringResource(Res.string.onboarding_permission_unavailable)
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

@Composable
internal fun PermissionStep(
    state: OnboardingViewState,
    platform: OnboardingPermissionPlatform,
    layout: PosatoLayout,
    onRequestAccess: () -> Unit,
    onEnableHelper: () -> Unit,
    onRecheckHelper: () -> Unit,
    onOpenHelperSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val ready = state.hasDeviceAccess(platform)
    OnboardingPage(
        layout = layout,
        actions = {
            if (ready) {
                OnboardingPrimaryAction(stringResource(Res.string.onboarding_action_continue), layout, onContinue)
            } else {
                when (platform) {
                    OnboardingPermissionPlatform.IOS -> {
                        OnboardingPrimaryAction(
                            stringResource(Res.string.onboarding_permission_ios_action),
                            layout,
                            onRequestAccess,
                            enabled = !state.permissionRunning,
                        )
                    }

                    OnboardingPermissionPlatform.MAC -> {
                        MacPermissionActions(state, layout, onEnableHelper, onRecheckHelper, onOpenHelperSettings)
                    }
                }
                PosatoButton(onClick = onContinue, style = PosatoButtonStyle.Quiet, enabled = !state.permissionRunning) {
                    Text(stringResource(Res.string.onboarding_action_not_now))
                }
            }
        },
    ) {
        PosatoHeading(
            stringResource(Res.string.onboarding_permission_title),
            description = stringResource(
                if (platform == OnboardingPermissionPlatform.IOS) {
                    Res.string.onboarding_permission_ios_body
                } else {
                    Res.string.onboarding_permission_mac_body
                },
            ),
            layout = layout,
        )
        if (platform == OnboardingPermissionPlatform.MAC) {
            PosatoCaption(stringResource(Res.string.onboarding_permission_mac_admin))
        }
        PosatoNotice {
            Text(stringResource(Res.string.onboarding_permission_control))
        }
        PermissionStatus(state, platform)
        if (!ready && state.accessResult != ApplicationAccessResult.Unavailable) {
            PosatoCaption(stringResource(Res.string.onboarding_permission_defer))
        }
    }
}

@Composable
private fun PermissionStatus(
    state: OnboardingViewState,
    platform: OnboardingPermissionPlatform,
) {
    val message = when (platform) {
        OnboardingPermissionPlatform.IOS -> state.accessResult?.accessMessage()

        OnboardingPermissionPlatform.MAC -> when (state.helperReadiness) {
            MacHelperReadiness.READY -> stringResource(Res.string.onboarding_summary_helper_on)
            MacHelperReadiness.APPROVAL_REQUIRED -> stringResource(Res.string.onboarding_permission_mac_approval)
            MacHelperReadiness.UNAVAILABLE -> stringResource(Res.string.onboarding_permission_mac_unavailable)
            null -> null
        }
    }
    if (message != null) {
        PosatoNotice(
            tone = if (state.hasDeviceAccess(platform)) PosatoTone.Positive else PosatoTone.Caution,
            announceChanges = true,
        ) { Text(message) }
    }
}

internal fun OnboardingViewState.hasDeviceAccess(platform: OnboardingPermissionPlatform): Boolean {
    return when (platform) {
        OnboardingPermissionPlatform.IOS -> {
            (accessResult as? ApplicationAccessResult.Determined)?.access == LocalApplicationMappingsAccess.READY
        }

        OnboardingPermissionPlatform.MAC -> {
            helperReadiness == MacHelperReadiness.READY
        }
    }
}

@Composable
private fun MacPermissionActions(
    state: OnboardingViewState,
    layout: PosatoLayout,
    onEnableHelper: () -> Unit,
    onRecheckHelper: () -> Unit,
    onOpenHelperSettings: () -> Unit,
) {
    if (state.helperReadiness == MacHelperReadiness.APPROVAL_REQUIRED) {
        OnboardingPrimaryAction(
            stringResource(Res.string.onboarding_permission_mac_open_settings),
            layout,
            onOpenHelperSettings,
        )
        PosatoButton(
            onClick = onRecheckHelper,
            style = PosatoButtonStyle.Secondary,
            enabled = !state.permissionRunning,
        ) { Text(stringResource(Res.string.onboarding_permission_mac_check_again)) }
    } else {
        OnboardingPrimaryAction(
            stringResource(Res.string.onboarding_permission_mac_action),
            layout,
            onEnableHelper,
            enabled = !state.permissionRunning,
        )
    }
}
