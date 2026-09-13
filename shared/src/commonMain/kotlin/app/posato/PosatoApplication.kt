package app.posato

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoNavigationScaffold
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.platformNavigationPlacement
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.OnboardingScreen
import app.posato.feature.onboarding.OnboardingUiState
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.onboarding.rememberMacHelperSetupUiState
import app.posato.feature.onboarding.rememberOnboardingUiState
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionScreen
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.ui.SyncAnnouncements
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.sync.ui.rememberSyncBootstrapUiState
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.TargetsBrowserState
import app.posato.feature.targets.ui.TargetsScreen
import dev.zacsweers.metro.Inject

@Inject
class PosatoApplication internal constructor(
    private val store: LocalTargetPolicyStore,
    private val applicationMappings: LocalApplicationMappings,
    private val sessionIds: SessionIdGenerator,
    private val clock: SessionClock,
    private val timeFormat: SessionTimeFormat,
    private val sessionOwner: SessionTransitionOwner,
    private val bootstrap: AppleSync,
    private val onboardingDependencies: OnboardingDependencies,
) {
    @Composable
    fun Content(
        modifier: Modifier = Modifier,
        highContrast: Boolean? = null,
        onAnnouncement: (String) -> Unit = {},
    ) {
        var setupDone by remember { mutableStateOf(false) }
        val syncState = rememberSyncBootstrapUiState(bootstrap)
        val helperSetup = rememberMacHelperSetupUiState(onboardingDependencies.macHelper)
        val onboarding = rememberOnboardingUiState(
            onboardingDependencies.setupStore,
            store,
            onboardingDependencies.applicationAccess,
            helperSetup,
        )
        LaunchedEffect(onboarding) { onboarding.loadCompletion() }
        LaunchedEffect(sessionOwner) { sessionOwner.runWhileHosted() }
        val placement = platformNavigationPlacement()
        val deviceNoun = if (placement == PosatoNavigationPlacement.Sidebar) "Mac" else "iPhone"
        PosatoTheme(highContrast = highContrast) {
            // Session reconciliation runs on every foreground, even when the
            // Session screen is not subscribed: subscriptions do not own the work.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = sessionOwner::onForeground)
            SyncAnnouncements(syncState, onAnnouncement)
            val completion = onboarding.completion
            if (completion == null) {
                Box(
                    modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.surface,
                    ).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            } else if (completion == SetupCompletion.INCOMPLETE && !setupDone) {
                OnboardingHost(
                    onboarding = onboarding,
                    syncState = syncState,
                    placement = placement,
                    deviceNoun = deviceNoun,
                    onSetupComplete = { setupDone = true },
                    modifier = modifier,
                )
            } else {
                DestinationsHost(
                    syncState = syncState,
                    onMacSetupAnnouncement = onAnnouncement,
                    macSetupState = helperSetup.takeIf { onboardingDependencies.permissionPlatform == OnboardingPermissionPlatform.MAC },
                    placement = placement,
                    modifier = modifier,
                )
            }
        }
    }

    @Composable
    private fun DestinationsHost(
        syncState: SyncBootstrapUiState,
        macSetupState: MacHelperSetupUiState?,
        onMacSetupAnnouncement: (String) -> Unit,
        placement: PosatoNavigationPlacement,
        modifier: Modifier = Modifier,
    ) {
        val browser = remember { TargetsBrowserState() }
        var showingSession by remember { mutableStateOf(true) }
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val hideNavigation = placement == PosatoNavigationPlacement.Bottom && keyboardVisible
        val deviceLabel = if (placement == PosatoNavigationPlacement.Sidebar) "On this Mac only" else "On this iPhone only"
        PosatoNavigationScaffold(
            placement = placement,
            modifier = modifier.fillMaxSize().background(
                MaterialTheme.colorScheme.surface,
            ).windowInsetsPadding(WindowInsets.safeDrawing),
            headerContent = { if (!hideNavigation) ApplicationNavigationHeader(placement) },
            navigationContent = {
                if (!hideNavigation) {
                    ApplicationNavigation(placement, showingSession, { showingSession = it })
                }
            },
        ) { layout ->
            val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val contentModifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth()
                if (showingSession) {
                    SessionScreen(
                        store,
                        applicationMappings,
                        sessionIds,
                        clock,
                        timeFormat,
                        sessionOwner,
                        onOpenPausedItems = { showingSession = false },
                        modifier = contentModifier,
                        layout = layout,
                        deviceLabel = deviceLabel,
                        syncState = syncState,
                        macSetupState = macSetupState,
                        onMacSetupAnnouncement = onMacSetupAnnouncement,
                    )
                } else {
                    TargetsScreen(
                        store,
                        applicationMappings,
                        contentModifier.padding(horizontal = inset, vertical = PosatoSpace.Medium),
                        browser,
                        deviceLabel,
                    )
                }
            }
        }
    }

    @Composable
    private fun OnboardingHost(
        onboarding: OnboardingUiState,
        syncState: SyncBootstrapUiState,
        placement: PosatoNavigationPlacement,
        deviceNoun: String,
        onSetupComplete: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        BoxWithConstraints(
            modifier.fillMaxSize().background(
                MaterialTheme.colorScheme.surface,
            ).windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val layout = if (maxWidth < PosatoSize.CompactBreakpoint) PosatoLayout.Compact else PosatoLayout.Expanded
            Column(Modifier.fillMaxSize()) {
                val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                if (placement != PosatoNavigationPlacement.Bottom || !keyboardVisible) {
                    ApplicationNavigationHeader(placement)
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    OnboardingScreen(
                        holder = onboarding,
                        syncState = syncState,
                        permissionPlatform = onboardingDependencies.permissionPlatform,
                        deviceNoun = deviceNoun,
                        onComplete = onSetupComplete,
                        modifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth(),
                        layout = layout,
                    )
                }
            }
        }
    }
}
