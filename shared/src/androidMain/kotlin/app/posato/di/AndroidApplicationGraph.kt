package app.posato.di

import app.posato.PosatoApplication
import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.notifications.SessionNotificationPlatform
import app.posato.feature.notifications.SessionNotifier
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.UnavailableMacHelper
import app.posato.feature.onboarding.data.SqlLocalSetupStore
import app.posato.feature.schedules.JavaScheduleZone
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.data.ScheduleSyncStore
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.ScheduleHost
import app.posato.feature.schedules.host.ScheduleHostPorts
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.session.JvmSessionTimeFormat
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.domain.RandomSessionIdGenerator
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionComposition
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.session.ui.recompose
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.ScheduleSync
import app.posato.feature.sync.bootstrap.SqlBootstrapStore
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.folder.FolderSync
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.KeystoreKeyItemProtection
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.PauseSetPreparation
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import app.posato.feature.targets.data.retainSets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Posato for Android (ADR 0010): the shared application over this device's enforcement, app choices, and folder workspace. */
@DependencyGraph(AppScope::class)
internal interface AndroidApplicationGraph :
    ApplicationGraph,
    ScheduleBindings {
    val appleSync: AppleSync
    val pauseSetPreparation: PauseSetPreparation
    val scheduledPauses: ScheduledPauses
    val sessionOwner: SessionTransitionOwner
    val localTargetPolicyStore: LocalTargetPolicyStore
    val applicationMappings: LocalApplicationMappings
    val sessionComposition: SessionComposition
    val sessionNotifier: SessionNotifier

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides applicationMappings: LocalApplicationMappings,
            @Provides @Named("helper") enforcement: EnforcementPort,
            @Provides database: PosatoDatabase,
            @Provides notifications: SessionNotificationPlatform,
            @Provides applicationAccess: ApplicationAccessPort,
            @Provides folderSync: FolderSync,
        ): AndroidApplicationGraph
    }

    @Provides
    fun provideFolderSyncControls(folderSync: FolderSync): FolderSyncControls {
        return folderSync
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePauseClaims(
        @Named("helper") helper: EnforcementPort,
    ): PauseClaims {
        return PauseClaims(helper)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideEnforcement(
        claims: PauseClaims,
        composition: SessionComposition,
    ): EnforcementPort {
        composition.occupied = claims::scheduleItems
        return claims.manual
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboarding(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        applicationAccess: ApplicationAccessPort,
    ): OnboardingDependencies {
        return OnboardingDependencies(
            setupStore = SqlLocalSetupStore(database, databaseDispatcher),
            applicationAccess = applicationAccess,
            macHelper = UnavailableMacHelper,
            permissionPlatform = OnboardingPermissionPlatform.ANDROID,
        )
    }

    @Provides
    @Named("database")
    @SingleIn(AppScope::class)
    fun provideDatabaseDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(1, "PosatoDatabase")
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePolicyStore(
        sync: AppleSync,
        policySync: LocalPolicySyncStore,
    ): LocalTargetPolicyStore {
        return SyncTargetPolicyStore(policySync, sync)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePolicySyncStore(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): LocalPolicySyncStore {
        return SqlLocalTargetPolicyStore(database, databaseDispatcher)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionStore(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): LocalSessionSyncStore {
        return SqlLocalSessionStore(database = database, databaseDispatcher = databaseDispatcher)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionOwner(
        sync: AppleSync,
        sessions: LocalSessionSyncStore,
        enforcement: EnforcementPort,
        clock: SessionClock,
        policyStore: LocalTargetPolicyStore,
        applicationMappings: LocalApplicationMappings,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        composition: SessionComposition,
    ): SessionTransitionOwner {
        val owner = SessionTransitionOwner(
            backgroundDispatcher = databaseDispatcher,
            store = sessions,
            clock = clock,
            enforcement = enforcement,
            loadTargets = { setId -> loadSessionTargets(policyStore, applicationMappings, setId) },
            triggers = sync.sessionTriggers,
            composition = composition,
        )
        sync.sessionObserver = owner
        return owner
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionIds(): SessionIdGenerator {
        return RandomSessionIdGenerator
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionClock(): SessionClock {
        return SessionClock { System.currentTimeMillis() }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionTimeFormat(): SessionTimeFormat {
        return JvmSessionTimeFormat()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleZone(): ScheduleZone {
        return JavaScheduleZone()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleHost(
        schedules: ScheduleDependencies,
        clock: SessionClock,
        claims: PauseClaims,
        @Named("helper") enforcement: EnforcementPort,
        policyStore: LocalTargetPolicyStore,
        applicationMappings: LocalApplicationMappings,
        retention: SqlPartRetentionStore,
    ): ScheduleHost {
        val ports = ScheduleHostPorts(
            claims = claims,
            gate = ScheduleStartGate { if (enforcement.status() == EnforcementOutcome.UNAVAILABLE) StartGate.SETUP_REQUIRED else StartGate.READY },
            hadConsent = { false },
            targets = { setId -> loadSessionTargets(policyStore, applicationMappings, setId) },
            maintenanceClosed = { false },
            targetChanges = merge(policyStore.policyChanges, applicationMappings.invalidations),
            limits = PauseLimits.MAC,
            occupied = claims::manualItems,
            retention = retention,
        )
        return ScheduleHost(schedules.store, schedules.zone, clock, ports)
    }

    @Provides
    fun provideScheduledPauses(host: ScheduleHost): ScheduledPauses {
        return host
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppleSync(
        folderSync: FolderSync,
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        policySync: LocalPolicySyncStore,
        sessions: LocalSessionSyncStore,
        schedules: ScheduleSyncStore,
        zone: ScheduleZone,
        clock: SessionClock,
        applicationMappings: LocalApplicationMappings,
    ): AppleSync {
        val ports = folderSync.ports
        val crypto = JdkSyncCryptoProvider()
        val store = SqlBootstrapStore(database, databaseDispatcher)
        val coordinator = BootstrapCoordinator(ports, ports, ports, store, crypto, ports)
        val core = SyncOperationCore(SqlSyncReplicaStore(database, databaseDispatcher), crypto, SyncWallClock { System.currentTimeMillis() })
        return AppleSync(
            coordinator,
            core,
            ports,
            ports,
            store,
            policySync,
            crypto,
            Dispatchers.IO,
            onWorkspaceRemoved = { sessions.dropRetainedMarkersExceptCurrent() },
            scheduleSync = ScheduleSync(schedules) { zone.localAt(clock.currentEpochMillis()).date },
            onSetsRemoved = applicationMappings::retainSets,
        ).also(folderSync::startPolling)
    }
}

/** What the Android host needs from the shared graph: the application, the one-time upgrade, and the schedule host. */
public class AndroidApplicationRuntime internal constructor(
    public val application: PosatoApplication,
    internal val graph: AndroidApplicationGraph,
) {
    public suspend fun preparePauseSets() {
        graph.pauseSetPreparation.prepare { emptyList() }
    }

    /** Hosts sessions, schedules, and notices for the life of the guard service, which keeps the process running. */
    public suspend fun host() {
        coroutineScope {
            launch { graph.sessionOwner.runWhileHosted() }
            launch {
                merge(graph.localTargetPolicyStore.policyChanges, graph.applicationMappings.invalidations)
                    .collect { graph.sessionOwner.recompose(graph.sessionComposition) }
            }
            launch { graph.sessionNotifier.run() }
            launch { graph.scheduledPauses.run() }
        }
    }

    /** Wakes the folder workspace and the schedules, such as from an alarm while the app is in the background. */
    public suspend fun wake() {
        graph.appleSync.onForeground()
    }
}

public fun createAndroidApplicationRuntime(
    applicationMappings: LocalApplicationMappings,
    enforcement: EnforcementPort,
    database: PosatoDatabase,
    notifications: SessionNotificationPlatform,
    applicationAccess: ApplicationAccessPort,
    localDirectory: String,
    browseFolder: suspend () -> String?,
): AndroidApplicationRuntime {
    val folderSync = FolderSync(
        localDirectory = localDirectory,
        crypto = JdkSyncCryptoProvider(),
        apple = null,
        files = app.posato.feature.sync.folder.NioFolderFileSystem,
        ioDispatcher = Dispatchers.IO,
        now = System::currentTimeMillis,
        protection = KeystoreKeyItemProtection(),
        browser = browseFolder,
    )
    val graph = createGraphFactory<AndroidApplicationGraph.Factory>().create(
        applicationMappings,
        enforcement,
        database,
        notifications,
        applicationAccess,
        folderSync,
    )
    return AndroidApplicationRuntime(graph.application, graph)
}
