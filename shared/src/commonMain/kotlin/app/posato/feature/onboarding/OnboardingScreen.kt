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
import app.posato.core.designsystem.PosatoButton
import app.posato.core.designsystem.PosatoButtonStyle
import app.posato.core.designsystem.PosatoHeading
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSetupStep
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.sync.bootstrap.AppleSyncState
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsBrowserState
import app.posato.generated.resources.Res
import app.posato.generated.resources.onboarding_action_later
import app.posato.generated.resources.onboarding_permission_title
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
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset),
        verticalArrangement = Arrangement.spacedBy(PosatoSpace.Section),
    ) {
        OnboardingProgress(current = state.step)
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
                PosatoHeading(stringResource(Res.string.onboarding_permission_title), layout = layout)
                when (permissionPlatform) {
                    OnboardingPermissionPlatform.IOS -> {
                        IosPermissionStep(state.accessResult, state.permissionRunning, onRequestAccess)
                    }

                    OnboardingPermissionPlatform.MAC -> {
                        MacPermissionStep(
                            state.helperReadiness,
                            state.permissionRunning,
                            onEnableHelper,
                            onRecheckHelper,
                            onOpenHelperSettings,
                        )
                    }
                }
                PosatoActionRow {
                    PosatoButton(onClick = onDefer, style = PosatoButtonStyle.Quiet) {
                        Text(stringResource(Res.string.onboarding_action_later))
                    }
                }
            }

            OnboardingStep.WEBSITE -> {
                WebsiteStep(state, browser, onSubmitWebsites, onContinue, onDefer, layout)
            }

            OnboardingStep.SUMMARY -> {
                SummaryStep(state, permissionPlatform, deviceNoun, syncSnapshot.linked, onFinish, layout)
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
