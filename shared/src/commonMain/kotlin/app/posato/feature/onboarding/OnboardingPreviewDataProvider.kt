package app.posato.feature.onboarding

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.targets.data.LocalApplicationMappingsAccess

internal class OnboardingPreviewDataProvider : PreviewParameterProvider<OnboardingPreviewDataProvider.OnboardingPreviewState> {
    override val values: Sequence<OnboardingPreviewState> = sequenceOf(
        OnboardingPreviewState(
            "Purpose",
            OnboardingViewState(
                step = OnboardingStep.PURPOSE,
                accessResult = null,
                helperReadiness = null,
                savedWebsites = 0,
                permissionRunning = false,
                websiteSaving = false,
            ),
            AppleSyncState(),
            OnboardingPermissionPlatform.IOS,
        ),
        OnboardingPreviewState(
            "Waiting for key",
            OnboardingViewState(
                step = OnboardingStep.ICLOUD,
                accessResult = null,
                helperReadiness = null,
                savedWebsites = 0,
                permissionRunning = false,
                websiteSaving = false,
            ),
            AppleSyncState(status = SyncStatus.WAITING_FOR_KEY, linked = false),
            OnboardingPermissionPlatform.IOS,
        ),
        OnboardingPreviewState(
            "Permission denied",
            OnboardingViewState(
                step = OnboardingStep.PERMISSION,
                accessResult = ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.AUTHORIZATION_DENIED),
                helperReadiness = null,
                savedWebsites = 0,
                permissionRunning = false,
                websiteSaving = false,
            ),
            AppleSyncState(),
            OnboardingPermissionPlatform.IOS,
        ),
        OnboardingPreviewState(
            "Approval required",
            OnboardingViewState(
                step = OnboardingStep.PERMISSION,
                accessResult = null,
                helperReadiness = MacHelperReadiness.APPROVAL_REQUIRED,
                savedWebsites = 0,
                permissionRunning = false,
                websiteSaving = false,
            ),
            AppleSyncState(),
            OnboardingPermissionPlatform.MAC,
        ),
        OnboardingPreviewState(
            "Website added",
            OnboardingViewState(
                step = OnboardingStep.WEBSITE,
                accessResult = null,
                helperReadiness = null,
                savedWebsites = 1,
                permissionRunning = false,
                websiteSaving = false,
            ),
            AppleSyncState(),
            OnboardingPermissionPlatform.IOS,
        ),
        OnboardingPreviewState(
            "Summary",
            OnboardingViewState(
                step = OnboardingStep.SUMMARY,
                accessResult = ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.AUTHORIZATION_DENIED),
                helperReadiness = null,
                savedWebsites = 1,
                permissionRunning = false,
                websiteSaving = false,
            ),
            AppleSyncState(status = SyncStatus.COMPLETED, linked = true),
            OnboardingPermissionPlatform.IOS,
        ),
    )

    internal data class OnboardingPreviewState(
        val name: String,
        val state: OnboardingViewState,
        val sync: AppleSyncState,
        val platform: OnboardingPermissionPlatform,
    )
}
