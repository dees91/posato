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
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoNavigationScaffold
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.platformNavigationPlacement
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.OnboardingScreen
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.onboarding.rememberOnboardingUiState
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionScreen
import app.posato.feature.sync.bootstrap.AppleSync
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
    private val sessionStore: LocalSessionStore,
    private val sessionIds: SessionIdGenerator,
    private val clock: SessionClock,
    private val timeFormat: SessionTimeFormat,
    private val enforcement: EnforcementPort,
    private val bootstrap: AppleSync,
    private val setupStore: LocalSetupStore,
    private val applicationAccess: ApplicationAccessPort,
    private val macHelper: MacHelperPort,
    private val permissionPlatform: OnboardingPermissionPlatform,
) {
    @Composable
    fun Content(
        modifier: Modifier = Modifier,
        highContrast: Boolean? = null
    ) {
        val browser = remember { TargetsBrowserState() }
        var showingSession by remember { mutableStateOf(true) }
        var setupDone by remember { mutableStateOf(false) }
        val syncState = rememberSyncBootstrapUiState(bootstrap)
        val onboarding = rememberOnboardingUiState(setupStore, store, applicationAccess, macHelper)
        LaunchedEffect(onboarding) { onboarding.loadCompletion() }
        val placement = platformNavigationPlacement()
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val hideNavigation = placement == PosatoNavigationPlacement.Bottom && keyboardVisible
        val deviceLabel = if (placement == PosatoNavigationPlacement.Sidebar) "On this Mac only" else "On this iPhone only"
        val deviceNoun = if (placement == PosatoNavigationPlacement.Sidebar) "Mac" else "iPhone"
        PosatoTheme(highContrast = highContrast) {
            val completion = onboarding.completion
            if (completion == null) {
                Box(
                    modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.surface,
                    ).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            } else if (completion == SetupCompletion.INCOMPLETE && !setupDone) {
                BoxWithConstraints(
                    modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.surface,
                    ).windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    val layout = if (maxWidth < PosatoSize.CompactBreakpoint) PosatoLayout.Compact else PosatoLayout.Expanded
                    Column(Modifier.fillMaxSize()) {
                        ApplicationNavigationHeader(placement)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            OnboardingScreen(
                                holder = onboarding,
                                syncState = syncState,
                                permissionPlatform = permissionPlatform,
                                deviceNoun = deviceNoun,
                                onFinished = { setupDone = true },
                                modifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth(),
                                layout = layout,
                            )
                        }
                    }
                }
            } else {
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
                                sessionStore,
                                store,
                                applicationMappings,
                                sessionIds,
                                clock,
                                timeFormat,
                                enforcement,
                                onOpenPausedItems = { showingSession = false },
                                modifier = contentModifier,
                                layout = layout,
                                deviceLabel = deviceLabel,
                                syncState = syncState,
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
        }
    }
}
