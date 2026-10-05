package app.posato

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.posato.core.designsystem.PosatoDevice
import app.posato.core.designsystem.PosatoLayout
import app.posato.core.designsystem.PosatoNavigationPlacement
import app.posato.core.designsystem.PosatoSize
import app.posato.core.designsystem.PosatoSpace
import app.posato.core.designsystem.PosatoTheme
import app.posato.core.designsystem.platformDevice
import app.posato.core.designsystem.platformUsesCupertinoChrome
import app.posato.core.designsystem.windowNavigationPlacement
import app.posato.core.navigation.PosatoNavStack
import app.posato.feature.about.AboutScreen
import app.posato.feature.about.ApplicationUpdates
import app.posato.feature.licenses.LicenseDocument
import app.posato.feature.licenses.LicensesScreen
import app.posato.feature.notifications.SessionNotifier
import app.posato.feature.onboarding.MacHelperSetupUiState
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.OnboardingScreen
import app.posato.feature.onboarding.OnboardingUiState
import app.posato.feature.onboarding.data.LocalSetupStore
import app.posato.feature.onboarding.data.SetupCompletion
import app.posato.feature.onboarding.rememberMacHelperSetupUiState
import app.posato.feature.onboarding.rememberOnboardingUiState
import app.posato.feature.presence.SessionWindowRequest
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.schedules.ui.SchedulesDestination
import app.posato.feature.schedules.ui.SchedulesInputs
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionComposition
import app.posato.feature.session.ui.SessionScreen
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.recompose
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.ui.SyncAnnouncements
import app.posato.feature.sync.ui.SyncBootstrapUiState
import app.posato.feature.sync.ui.rememberSyncBootstrapUiState
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.ui.PauseSetsDestination
import app.posato.feature.targets.ui.PauseSetsInputs
import app.posato.feature.targets.ui.PauseSetsNavigation
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

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
    private val notifier: SessionNotifier,
    private val schedules: ScheduleDependencies,
    private val scheduledPauses: ScheduledPauses,
    private val sessionComposition: SessionComposition,
) {
    private val scheduleInputs = SchedulesInputs(
        schedules.store,
        schedules.zone,
        clock,
        timeFormat,
        notifier.takeIf { it.available },
        bootstrap,
        applicationMappings,
        onboardingDependencies.applicationAccess,
        store,
    )
    private val pauseSetsInputs = PauseSetsInputs(
        store = store,
        applicationMappings = applicationMappings,
        schedules = schedules.store,
        sessionStatus = sessionOwner.status,
        scheduledPause = scheduledPauses.pause,
        setupStore = onboardingDependencies.setupStore,
        linked = { bootstrap.state.value.linked },
    )

    @Composable
    fun Content(
        modifier: Modifier = Modifier,
        highContrast: Boolean? = null,
        onAnnouncement: (String) -> Unit = {},
        updates: ApplicationUpdates? = null,
        hostsSession: Boolean = true,
        windowRequests: Flow<SessionWindowRequest> = emptyFlow(),
        navigation: ApplicationNavigation = remember { ApplicationNavigation() },
    ) {
        var setupDone by remember { mutableStateOf(false) }
        val syncState = rememberSyncBootstrapUiState(bootstrap)
        val helperSetup = rememberMacHelperSetupUiState(onboardingDependencies.macHelper) {
            sessionOwner.status.value is LocalSessionStatus.Active || sessionOwner.view.value.busy
        }
        val onboarding = rememberOnboardingUiState(
            onboardingDependencies.setupStore,
            store,
            onboardingDependencies.applicationAccess,
            helperSetup,
        )
        LaunchedEffect(onboarding) { onboarding.loadCompletion() }
        LaunchedEffect(helperSetup) {
            // A scheduled pause holds the helper like a session, so setup waits for it too.
            combine(sessionOwner.blockingSetup(), scheduledPauses.pause) { blocked, pause -> blocked || pause?.restricts == true }
                .collect { helperSetup.sessionBlocked = it }
        }
        if (hostsSession) {
            LaunchedEffect(sessionOwner) { sessionOwner.runWhileHosted() }
            // An edit to a set a running session uses pauses its additions at once.
            LaunchedEffect(sessionOwner) {
                merge(store.policyChanges, applicationMappings.invalidations).collect { sessionOwner.recompose(sessionComposition) }
            }
            LaunchedEffect(notifier) { notifier.run() }
            // A process that hosts sessions also starts schedules; the Mac's resident process does both itself.
            LaunchedEffect(scheduledPauses) { scheduledPauses.run() }
        }
        val device = remember { platformDevice() }
        PosatoTheme(highContrast = highContrast) {
            ForegroundEffects(helperSetup)
            SyncAnnouncements(syncState, onAnnouncement)
            val completion = onboarding.completion
            if (completion == null) {
                Box(
                    modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.surface,
                    ).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            } else if (completion == SetupCompletion.INCOMPLETE && !setupDone) {
                LaunchedEffect(windowRequests) { windowRequests.collect {} }
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
                    navigation = navigation,
                    windowRequests = windowRequests,
                    modifier = modifier,
                )
            }
        }
    }

    @Composable
    private fun ForegroundEffects(helperSetup: MacHelperSetupUiState) {
        // Session reconciliation runs on every foreground, even when the
        // Session screen is not subscribed: subscriptions do not own the work.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = sessionOwner::onForeground)
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { helperSetup.readQuietly(refresh = true) }
        // A schedule that came due while the app was away is caught up on return.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = scheduledPauses::refresh)
    }

    @Composable
    private fun DestinationsHost(
        syncState: SyncBootstrapUiState,
        macSetupState: MacHelperSetupUiState?,
        onMacSetupAnnouncement: (String) -> Unit,
        device: PosatoDevice,
        updates: ApplicationUpdates?,
        navigation: ApplicationNavigation,
        windowRequests: Flow<SessionWindowRequest>,
        modifier: Modifier = Modifier,
    ) {
        var pendingRequest by remember { mutableStateOf<SessionWindowRequest?>(null) }
        WindowRequestsEffect(windowRequests, navigation) { pendingRequest = it }
        val deviceLabel = "On this ${device.noun} only"
        if (platformUsesCupertinoChrome && windowNavigationPlacement(device) == PosatoNavigationPlacement.Bottom) {
            CupertinoDestinationsHost(
                navigation,
                device,
                deviceLabel,
                syncState,
                macSetupState,
                onMacSetupAnnouncement,
                pendingRequest,
                updates,
                onConsumeWindowRequest = { pendingRequest = null },
                modifier = modifier,
            )
        } else {
            ApplicationNavigationScaffold(
                device = device,
                destination = navigation.destination,
                showingInformation = navigation.informationPage != null,
                onSelect = navigation::select,
                onOpenAbout = { navigation.showInformation(ApplicationInformationPage.ABOUT) },
                modifier = modifier,
            ) { layout ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val contentModifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth()
                    PosatoNavStack(
                        navigation.shellStack(),
                        onBack = navigation::back,
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) { route ->
                        when (route) {
                            ShellRoute.Destinations -> {
                                DestinationContent(
                                    navigation = navigation,
                                    layout = layout,
                                    deviceLabel = deviceLabel,
                                    syncState = syncState,
                                    macSetupState = macSetupState,
                                    onMacSetupAnnouncement = onMacSetupAnnouncement,
                                    windowRequest = pendingRequest,
                                    onConsumeWindowRequest = { pendingRequest = null },
                                    device = device,
                                    modifier = contentModifier,
                                )
                            }

                            ShellRoute.About, ShellRoute.Licenses, is ShellRoute.License -> {
                                InformationContent(route, navigation, updates, contentModifier.padding(layout.inset))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CupertinoDestinationsHost(
        navigation: ApplicationNavigation,
        device: PosatoDevice,
        deviceLabel: String,
        syncState: SyncBootstrapUiState,
        macSetupState: MacHelperSetupUiState?,
        onMacSetupAnnouncement: (String) -> Unit,
        windowRequest: SessionWindowRequest?,
        updates: ApplicationUpdates?,
        onConsumeWindowRequest: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        BoxWithConstraints(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            val layout = if (maxWidth < PosatoSize.CompactBreakpoint) PosatoLayout.Compact else PosatoLayout.Expanded
            val contentModifier = Modifier.widthIn(max = PosatoSize.Content).fillMaxWidth()
            PosatoNavStack(
                navigation.shellStack(),
                onBack = navigation::back,
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) { route ->
                when (route) {
                    ShellRoute.Destinations -> {
                        CupertinoTabFrame(device, navigation.destination, onSelect = navigation::select) {
                            DestinationContent(
                                navigation = navigation,
                                layout = layout,
                                deviceLabel = deviceLabel,
                                syncState = syncState,
                                macSetupState = macSetupState,
                                onMacSetupAnnouncement = onMacSetupAnnouncement,
                                windowRequest = windowRequest,
                                onConsumeWindowRequest = onConsumeWindowRequest,
                                device = device,
                                modifier = contentModifier,
                                onOpenAbout = { navigation.showInformation(ApplicationInformationPage.ABOUT) },
                            )
                        }
                    }

                    ShellRoute.About, ShellRoute.Licenses, is ShellRoute.License -> {
                        val backLabel = when (route) {
                            ShellRoute.About -> navigation.destination.title
                            ShellRoute.Licenses -> "About"
                            else -> "Licenses"
                        }
                        InformationContent(
                            route,
                            navigation,
                            updates,
                            contentModifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                            cupertinoBackLabel = backLabel,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DestinationContent(
        navigation: ApplicationNavigation,
        layout: PosatoLayout,
        deviceLabel: String,
        syncState: SyncBootstrapUiState,
        macSetupState: MacHelperSetupUiState?,
        onMacSetupAnnouncement: (String) -> Unit,
        windowRequest: SessionWindowRequest?,
        onConsumeWindowRequest: () -> Unit,
        device: PosatoDevice,
        modifier: Modifier = Modifier,
        onOpenAbout: (() -> Unit)? = null,
    ) {
        var destination by navigation::destination
        when (destination) {
            ApplicationDestination.SESSION -> SessionScreen(
                store,
                applicationMappings,
                sessionIds,
                clock,
                timeFormat,
                sessionOwner,
                onOpenPausedItems = { setId ->
                    destination = ApplicationDestination.TARGETS
                    setId?.let(navigation.pauseSets::open)
                },
                onEditPausedItems = { setId, category ->
                    destination = ApplicationDestination.TARGETS
                    setId?.let { navigation.pauseSets.open(it, category) }
                },
                modifier = modifier,
                layout = layout,
                deviceLabel = deviceLabel,
                syncState = syncState,
                macSetupState = macSetupState,
                onMacSetupAnnouncement = onMacSetupAnnouncement,
                windowRequest = windowRequest,
                onConsumeWindowRequest = onConsumeWindowRequest,
                scheduledPauses = scheduledPauses,
                notPausedYet = sessionComposition.notPausedYet,
                deviceNoun = device.noun,
                overviewHeader = onOpenAbout?.let { open -> { ApplicationNavigationHeader(device, onOpenAbout = open, inset = false) } },
            )

            ApplicationDestination.SCHEDULES -> SchedulesDestination(scheduleInputs, device, layout, macSetupState, modifier)

            ApplicationDestination.TARGETS -> PauseSetsDestination(
                pauseSetsInputs,
                navigation.pauseSets,
                device.noun,
                if (onOpenAbout !=
                    null
                ) {
                    modifier
                } else {
                    modifier.padding(start = layout.inset, end = layout.inset, top = layout.inset, bottom = PosatoSpace.Medium)
                },
                entryModifier = Modifier,
            )
        }
    }

    @Composable
    private fun InformationContent(
        route: ShellRoute,
        navigation: ApplicationNavigation,
        updates: ApplicationUpdates?,
        modifier: Modifier = Modifier,
        cupertinoBackLabel: String? = null,
    ) {
        if (route == ShellRoute.About) {
            AboutScreen(
                onOpenLicenses = { navigation.showInformation(ApplicationInformationPage.LICENSES) },
                onBack = navigation::back,
                modifier = modifier,
                updates = updates,
                notifications = notifier.takeIf { it.available },
                barBackLabel = cupertinoBackLabel,
            )
        } else {
            LicensesScreen(
                document = (route as? ShellRoute.License)?.document,
                onSelect = { navigation.licenseDocument = it },
                onBack = navigation::back,
                modifier = modifier,
                barBackLabel = cupertinoBackLabel,
            )
        }
    }

    @Composable
    private fun WindowRequestsEffect(
        windowRequests: Flow<SessionWindowRequest>,
        navigation: ApplicationNavigation,
        onRequest: (SessionWindowRequest) -> Unit,
    ) {
        val latestOnRequest by rememberUpdatedState(onRequest)
        LaunchedEffect(windowRequests) {
            windowRequests.collect { request ->
                navigation.destination = ApplicationDestination.SESSION
                navigation.showInformation(null)
                latestOnRequest(request)
            }
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

@Stable
public class ApplicationNavigation {
    internal val pauseSets: PauseSetsNavigation = PauseSetsNavigation()
    internal var destination: ApplicationDestination by mutableStateOf(ApplicationDestination.SESSION)
    internal var informationPage: ApplicationInformationPage? by mutableStateOf(null)
        private set
    internal var licenseDocument: LicenseDocument? by mutableStateOf(null)

    internal fun select(selected: ApplicationDestination) {
        destination = selected
        showInformation(null)
        if (selected == ApplicationDestination.TARGETS) pauseSets.openSet?.let { pauseSets.browserFor(it).showingWebsiteEditor = true }
    }

    internal fun showInformation(page: ApplicationInformationPage?) {
        informationPage = page
        licenseDocument = null
    }

    internal fun shellStack(): List<ShellRoute> {
        return buildList {
            add(ShellRoute.Destinations)
            if (informationPage != null) add(ShellRoute.About)
            if (informationPage == ApplicationInformationPage.LICENSES) add(ShellRoute.Licenses)
            licenseDocument?.let { add(ShellRoute.License(it)) }
        }
    }

    internal fun back() {
        when {
            licenseDocument != null -> licenseDocument = null
            informationPage == ApplicationInformationPage.LICENSES -> showInformation(ApplicationInformationPage.ABOUT)
            else -> showInformation(null)
        }
    }
}

/** The shell stack: the selected destination, with About Posato, Licenses, and a license text above it. */
internal sealed interface ShellRoute {
    data object Destinations : ShellRoute

    data object About : ShellRoute

    data object Licenses : ShellRoute

    data class License(
        val document: LicenseDocument
    ) : ShellRoute
}

private val PosatoLayout.inset: Dp
    get() {
        return if (this == PosatoLayout.Compact) PosatoSpace.Section else PosatoSpace.Canvas
    }

internal enum class ApplicationDestination(
    val title: String,
) {
    SESSION("Session"),
    TARGETS("Pause sets"),
    SCHEDULES("Schedules"),
}

internal enum class ApplicationInformationPage {
    ABOUT,
    LICENSES,
}
