package app.posato.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import app.posato.generated.resources.mac_setup_not_enabled
import app.posato.generated.resources.mac_setup_recovery
import app.posato.generated.resources.mac_setup_uncertain
import app.posato.generated.resources.mac_unified_body
import app.posato.generated.resources.mac_unified_defer
import app.posato.generated.resources.mac_unified_title
import app.posato.generated.resources.onboarding_action_continue
import app.posato.generated.resources.onboarding_action_not_now
import app.posato.generated.resources.onboarding_permission_android_action
import app.posato.generated.resources.onboarding_permission_android_body
import app.posato.generated.resources.onboarding_permission_android_denied
import app.posato.generated.resources.onboarding_permission_android_required
import app.posato.generated.resources.onboarding_permission_check_failed
import app.posato.generated.resources.onboarding_permission_control
import app.posato.generated.resources.onboarding_permission_defer
import app.posato.generated.resources.onboarding_permission_denied
import app.posato.generated.resources.onboarding_permission_ios_action
import app.posato.generated.resources.onboarding_permission_ios_body
import app.posato.generated.resources.onboarding_permission_linux_action
import app.posato.generated.resources.onboarding_permission_linux_body
import app.posato.generated.resources.onboarding_permission_linux_denied
import app.posato.generated.resources.onboarding_permission_linux_required
import app.posato.generated.resources.onboarding_permission_mac_action
import app.posato.generated.resources.onboarding_permission_mac_approval
import app.posato.generated.resources.onboarding_permission_mac_check_again
import app.posato.generated.resources.onboarding_permission_mac_open_settings
import app.posato.generated.resources.onboarding_permission_mac_unavailable
import app.posato.generated.resources.onboarding_permission_required
import app.posato.generated.resources.onboarding_permission_title
import app.posato.generated.resources.onboarding_permission_unavailable
import app.posato.generated.resources.onboarding_summary_access_off
import app.posato.generated.resources.onboarding_summary_access_on
import app.posato.generated.resources.onboarding_summary_access_unchecked
import app.posato.generated.resources.onboarding_summary_android_off
import app.posato.generated.resources.onboarding_summary_android_on
import app.posato.generated.resources.onboarding_summary_android_unchecked
import app.posato.generated.resources.onboarding_summary_helper_off
import app.posato.generated.resources.onboarding_summary_helper_on
import app.posato.generated.resources.onboarding_summary_linux_off
import app.posato.generated.resources.onboarding_summary_linux_on
import app.posato.generated.resources.onboarding_summary_linux_unchecked
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the access step says on a host where one access grant, not the Mac helper, lets Posato pause. */
internal class AccessCopy(
    val body: StringResource,
    val action: StringResource,
    val on: StringResource,
    val off: StringResource,
    val unchecked: StringResource,
    val denied: StringResource,
    val required: StringResource,
)

internal fun OnboardingPermissionPlatform.accessCopy(): AccessCopy {
    return when (this) {
        OnboardingPermissionPlatform.LINUX -> AccessCopy(
            Res.string.onboarding_permission_linux_body,
            Res.string.onboarding_permission_linux_action,
            Res.string.onboarding_summary_linux_on,
            Res.string.onboarding_summary_linux_off,
            Res.string.onboarding_summary_linux_unchecked,
            Res.string.onboarding_permission_linux_denied,
            Res.string.onboarding_permission_linux_required,
        )

        OnboardingPermissionPlatform.ANDROID -> AccessCopy(
            Res.string.onboarding_permission_android_body,
            Res.string.onboarding_permission_android_action,
            Res.string.onboarding_summary_android_on,
            Res.string.onboarding_summary_android_off,
            Res.string.onboarding_summary_android_unchecked,
            Res.string.onboarding_permission_android_denied,
            Res.string.onboarding_permission_android_required,
        )

        OnboardingPermissionPlatform.IOS, OnboardingPermissionPlatform.MAC -> AccessCopy(
            Res.string.onboarding_permission_ios_body,
            Res.string.onboarding_permission_ios_action,
            Res.string.onboarding_summary_access_on,
            Res.string.onboarding_summary_access_off,
            Res.string.onboarding_summary_access_unchecked,
            Res.string.onboarding_permission_denied,
            Res.string.onboarding_permission_required,
        )
    }
}
