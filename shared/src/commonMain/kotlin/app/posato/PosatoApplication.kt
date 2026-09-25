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
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.platformDevice
import app.posato.feature.about.AboutScreen
import app.posato.feature.about.ApplicationUpdates
import app.posato.feature.licenses.LicensesScreen
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
import app.posato.feature.targets.ui.TargetsCategory
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
        updates: ApplicationUpdates? = null,
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
        val device = remember { platformDevice() }
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
                    device = device,
                    onSetupComplete = { setupDone = true },
                    modifier = modifier,
                )
            } else {
                LaunchedEffect(updates) { updates?.askForAutomaticChecksOnce() }
                DestinationsHost(
                    syncState = syncState,
                    onMacSetupAnnouncement = onAnnouncement,
                    macSetupState = helperSetup.takeIf { onboardingDependencies.permissionPlatform == OnboardingPermissionPlatform.MAC },
                    device = device,
                    updates = updates,
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
        device: PosatoDevice,
        updates: ApplicationUpdates?,
        modifier: Modifier = Modifier,
    ) {
        val browser = remember { TargetsBrowserState() }
        var showingSession by remember { mutableStateOf(true) }
        var informationPage by remember { mutableStateOf<ApplicationInformationPage?>(null) }
        val deviceLabel = "On this ${device.noun} only"
        ApplicationNavigationScaffold(
            device = device,
            showingSession = showingSession,
            showingInformation = informationPage != null,
            onSelect = {
                showingSession = it
                informationPage = null
                if (!it) browser.showingWebsiteEditor = true
            },
            onOpenAbout = { informationPage = ApplicationInformationPage.ABOUT },
            modifier = modifier,
        ) { layout ->
            val inset = if (layout == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val contentModifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth()
                val currentInformationPage = informationPage
                when {
                    currentInformationPage != null -> {
                        ApplicationInformationHost(currentInformationPage, { informationPage = it }, updates, contentModifier.padding(inset))
                    }

                    showingSession -> {
                        SessionScreen(
                            store,
                            applicationMappings,
                            sessionIds,
                            clock,
                            timeFormat,
                            sessionOwner,
                            onOpenPausedItems = { showingSession = false },
                            onEditPausedItems = browser.editRoute { showingSession = false },
                            modifier = contentModifier,
                            layout = layout,
                            deviceLabel = deviceLabel,
                            syncState = syncState,
                            macSetupState = macSetupState,
                            onMacSetupAnnouncement = onMacSetupAnnouncement,
                        )
                    }

                    else -> {
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

    private fun TargetsBrowserState.editRoute(onOpen: () -> Unit): (TargetsCategory) -> Unit {
        return { category ->
            this.category = category
            if (category == TargetsCategory.WEBSITES) {
                searching = false
                showingWebsiteEditor = false
            }
            onOpen()
        }
    }

    @Composable
    private fun OnboardingHost(
        onboarding: OnboardingUiState,
        syncState: SyncBootstrapUiState,
        device: PosatoDevice,
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
                if (device == PosatoDevice.Mac || !keyboardVisible) {
                    ApplicationNavigationHeader(device)
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    OnboardingScreen(
                        holder = onboarding,
                        syncState = syncState,
                        permissionPlatform = onboardingDependencies.permissionPlatform,
                        deviceNoun = device.noun,
                        onComplete = onSetupComplete,
                        modifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth(),
                        layout = layout,
                    )
                }
            }
        }
    }
}

private enum class ApplicationInformationPage {
    ABOUT,
    LICENSES,
}

@Composable
private fun ApplicationInformationHost(
    page: ApplicationInformationPage,
    onNavigate: (ApplicationInformationPage?) -> Unit,
    updates: ApplicationUpdates?,
    modifier: Modifier = Modifier,
) {
    when (page) {
        ApplicationInformationPage.ABOUT -> AboutScreen(
            onOpenLicenses = { onNavigate(ApplicationInformationPage.LICENSES) },
            onBack = { onNavigate(null) },
            modifier = modifier,
            updates = updates,
        )

        ApplicationInformationPage.LICENSES -> LicensesScreen(
            onBack = { onNavigate(ApplicationInformationPage.ABOUT) },
            modifier = modifier,
        )
    }
}
