package app.posato.feature.onboarding

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.bootstrap.SyncStatus
import app.posato.feature.targets.data.LocalApplicationMappingsAccess

internal class OnboardingPreviewDataProvider : PreviewParameterProvider<OnboardingPreviewDataProvider.OnboardingPreviewState> {
    private val initial = OnboardingViewState(
        step = OnboardingStep.PURPOSE,
        accessResult = null,
        helperReadiness = null,
        savedWebsites = 0,
        permissionRunning = false,
        websiteSaving = false,
    )
    override val values: Sequence<OnboardingPreviewState> = sequenceOf(
        OnboardingPreviewState("Welcome", initial),
        OnboardingPreviewState("Privacy", initial.copy(step = OnboardingStep.PRIVACY)),
        OnboardingPreviewState("iCloud optional", initial.copy(step = OnboardingStep.ICLOUD)),
        OnboardingPreviewState(
            "iCloud connected",
            initial.copy(step = OnboardingStep.ICLOUD),
            sync = AppleSyncState(status = SyncStatus.COMPLETED, linked = true),
        ),
        OnboardingPreviewState(
            "Waiting for key",
            initial.copy(step = OnboardingStep.ICLOUD),
            sync = AppleSyncState(status = SyncStatus.WAITING_FOR_KEY, joinPending = true),
        ),
        OnboardingPreviewState(
            "Syncing",
            initial.copy(step = OnboardingStep.ICLOUD),
            sync = AppleSyncState(status = SyncStatus.SYNCING),
        ),
        OnboardingPreviewState(
            "Retry sync",
            initial.copy(step = OnboardingStep.ICLOUD),
            sync = AppleSyncState(status = SyncStatus.RETRYABLE),
        ),
        OnboardingPreviewState("Screen Time", initial.copy(step = OnboardingStep.PERMISSION)),
        OnboardingPreviewState(
            "Access allowed",
            initial.copy(
                step = OnboardingStep.PERMISSION,
                accessResult = ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.READY),
            ),
        ),
        OnboardingPreviewState(
            "Access denied",
            initial.copy(
                step = OnboardingStep.PERMISSION,
                accessResult = ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.AUTHORIZATION_DENIED),
            ),
        ),
        OnboardingPreviewState(
            "Access unavailable",
            initial.copy(step = OnboardingStep.PERMISSION, accessResult = ApplicationAccessResult.Unavailable),
        ),
        OnboardingPreviewState(
            "Mac access",
            initial.copy(step = OnboardingStep.PERMISSION),
            platform = OnboardingPermissionPlatform.MAC,
        ),
        OnboardingPreviewState(
            "Mac not enabled",
            initial.copy(step = OnboardingStep.PERMISSION, helperReadiness = MacHelperReadiness.NOT_ENABLED),
            platform = OnboardingPermissionPlatform.MAC,
        ),
        OnboardingPreviewState(
            "Mac approval required",
            initial.copy(step = OnboardingStep.PERMISSION, helperReadiness = MacHelperReadiness.APPROVAL_REQUIRED),
            platform = OnboardingPermissionPlatform.MAC,
        ),
        OnboardingPreviewState(
            "Mac enabled",
            initial.copy(step = OnboardingStep.PERMISSION, helperReadiness = MacHelperReadiness.READY),
            platform = OnboardingPermissionPlatform.MAC,
        ),
        OnboardingPreviewState(
            "Mac unavailable",
            initial.copy(step = OnboardingStep.PERMISSION, helperReadiness = MacHelperReadiness.UNAVAILABLE),
            platform = OnboardingPermissionPlatform.MAC,
        ),
        OnboardingPreviewState("First website", initial.copy(step = OnboardingStep.WEBSITE)),
        OnboardingPreviewState("Website saved", initial.copy(step = OnboardingStep.WEBSITE, savedWebsites = 1)),
        OnboardingPreviewState(
            "Summary waiting for key",
            initial.copy(step = OnboardingStep.SUMMARY, savedWebsites = 1),
            sync = AppleSyncState(status = SyncStatus.WAITING_FOR_KEY, joinPending = true),
        ),
        OnboardingPreviewState("Summary empty", initial.copy(step = OnboardingStep.SUMMARY)),
        OnboardingPreviewState("Summary needs access", initial.copy(step = OnboardingStep.SUMMARY, savedWebsites = 1)),
        OnboardingPreviewState(
            "Summary with access",
            initial.copy(
                step = OnboardingStep.SUMMARY,
                savedWebsites = 1,
                accessResult = ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.READY),
            ),
            sync = AppleSyncState(status = SyncStatus.COMPLETED, linked = true),
        ),
    )

    internal data class OnboardingPreviewState(
        val name: String,
        val state: OnboardingViewState,
        val sync: AppleSyncState = AppleSyncState(),
        val platform: OnboardingPermissionPlatform = OnboardingPermissionPlatform.IOS,
    )
}
