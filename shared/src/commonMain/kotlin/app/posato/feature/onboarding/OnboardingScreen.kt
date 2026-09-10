package app.posato.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import app.posato.core.designsystem.PosatoCaption
import app.posato.core.designsystem.PosatoEyebrow
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsBrowserState
import app.posato.generated.resources.Res
import app.posato.generated.resources.onboarding_progress
import app.posato.generated.resources.onboarding_step_device
import app.posato.generated.resources.onboarding_step_icloud
import app.posato.generated.resources.onboarding_step_privacy
import app.posato.generated.resources.onboarding_step_purpose
import app.posato.generated.resources.onboarding_step_summary
import app.posato.generated.resources.onboarding_step_website
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberOnboardingUiState(
    setupStore: LocalSetupStore,
    policyStore: LocalTargetPolicyStore,
    applicationAccess: ApplicationAccessPort,
    helperSetup: MacHelperSetupUiState,
): OnboardingUiState {
    val scope = rememberCoroutineScope()
    return remember(setupStore, policyStore, applicationAccess, helperSetup) {
        OnboardingUiState(setupStore, policyStore, applicationAccess, helperSetup, scope)
    }
}

@Composable
internal fun OnboardingScreen(
    holder: OnboardingUiState,
    syncState: SyncBootstrapUiState,
    permissionPlatform: OnboardingPermissionPlatform,
    deviceNoun: String,
    onComplete: () -> Unit,
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
        onFinish = { holder.finish(onComplete) },
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
        modifier.fillMaxSize().padding(inset),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
    ) {
        OnboardingProgress(current = state.step)
        key(state.step) {
            when (state.step) {
                OnboardingStep.PURPOSE -> {
                    PurposeStep(onContinue, layout)
                }

                OnboardingStep.PRIVACY -> {
                    PrivacyStep(onContinue, layout)
                }

                OnboardingStep.ICLOUD -> {
                    IcloudStep(syncSnapshot, syncRunning, onSync, onDefer, layout)
                }

                OnboardingStep.PERMISSION -> {
                    PermissionStep(
                        state,
                        permissionPlatform,
                        layout,
                        onRequestAccess,
                        onEnableHelper,
                        onRecheckHelper,
                        onOpenHelperSettings,
                        onDefer,
                    )
                }

                OnboardingStep.WEBSITE -> {
                    WebsiteStep(state, browser, onSubmitWebsites, onContinue, onDefer, layout)
                }

                OnboardingStep.SUMMARY -> {
                    SummaryStep(state, permissionPlatform, deviceNoun, syncSnapshot, onFinish, layout)
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgress(current: OnboardingStep) {
    val label = when (current) {
        OnboardingStep.PURPOSE -> Res.string.onboarding_step_purpose
        OnboardingStep.PRIVACY -> Res.string.onboarding_step_privacy
        OnboardingStep.ICLOUD -> Res.string.onboarding_step_icloud
        OnboardingStep.PERMISSION -> Res.string.onboarding_step_device
        OnboardingStep.WEBSITE -> Res.string.onboarding_step_website
        OnboardingStep.SUMMARY -> Res.string.onboarding_step_summary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosatoSpace.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosatoEyebrow(stringResource(label), Modifier.weight(1f))
        PosatoCaption(stringResource(Res.string.onboarding_progress, current.ordinal + 1, OnboardingStep.entries.size))
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
