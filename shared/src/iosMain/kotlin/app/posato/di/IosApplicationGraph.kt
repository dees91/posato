package app.posato.di

import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createIosDatabaseDriver
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosSessionEnforcement
import app.posato.feature.enforcement.IosSuspendedExpiry
import app.posato.feature.enforcement.IosSuspendedExpiryProvider
import app.posato.feature.notifications.SessionNotificationPlatform
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.IosApplicationAccess
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.UnavailableMacHelper
import app.posato.feature.onboarding.data.SqlLocalSetupStore
import app.posato.feature.schedules.IosScheduleBridge
import app.posato.feature.schedules.data.ScheduleSyncStore
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.IosSessionTimeFormat
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.RandomSessionIdGenerator
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionComposition
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.IosSyncBackgroundTime
import app.posato.feature.sync.bootstrap.ScheduleSync
import app.posato.feature.sync.bootstrap.SqlBootstrapStore
import app.posato.feature.sync.data.IosBootstrapCloudAdapter
import app.posato.feature.sync.data.IosBootstrapKeychainAdapter
import app.posato.feature.sync.data.IosCloudKitMailboxProvider
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosKeychainProvider
import app.posato.feature.sync.data.IosMailboxAdapter
import app.posato.feature.sync.data.IosSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.folder.AppleSyncPorts
import app.posato.feature.sync.folder.BookmarkFolderAccess
import app.posato.feature.sync.folder.FolderSync
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.FoundationFolderFileSystem
import app.posato.feature.sync.folder.IosFolderPicker
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import app.posato.feature.targets.data.IosLocalApplicationMappings
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
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSRecursiveLock
import platform.posix.time
import kotlin.coroutines.resume

@DependencyGraph(AppScope::class)
internal interface IosApplicationGraph :
    ApplicationGraph,
    IosScheduleBindings {
    val localTargetPolicyStore: LocalTargetPolicyStore
    val pauseSetPreparation: PauseSetPreparation
    val appleSync: AppleSync
    val appleBootstrap: AppleBootstrap
        get() {
            return appleSync.bootstrap
        }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides applicationMappings: LocalApplicationMappings,
            @Provides enforcement: EnforcementPort,
            @Provides keychainProvider: IosKeychainProvider,
            @Provides mailboxProvider: IosCloudKitMailboxProvider,
            @Provides cryptoProvider: IosCryptoProvider,
            @Provides applicationAccess: ApplicationAccessPort,
            @Provides notifications: SessionNotificationPlatform,
            @Provides schedules: IosScheduleBridge,
            @Provides folderPicker: IosFolderPicker,
        ): IosApplicationGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideFolderSync(
        keychainProvider: IosKeychainProvider,
        mailboxProvider: IosCloudKitMailboxProvider,
        cryptoProvider: IosCryptoProvider,
        folderPicker: IosFolderPicker,
    ): FolderSync {
        val keys = IosBootstrapKeychainAdapter(keychainProvider)
        val apple = AppleSyncPorts(keys, IosBootstrapCloudAdapter(mailboxProvider), keys, IosMailboxAdapter(mailboxProvider))
        return FolderSync(
            localDirectory = iosApplicationSupportDirectory(),
            crypto = IosSyncCryptoProvider(cryptoProvider),
            apple = apple,
            files = FoundationFolderFileSystem,
            ioDispatcher = Dispatchers.IO,
            now = { time(null) * MILLIS_PER_SECOND },
            access = BookmarkFolderAccess(),
            browser = { pickFolder(folderPicker) },
            pollsWhileRunning = false,
            acceptsTypedPath = false,
        )
    }

    @Provides
    fun provideFolderSyncControls(folderSync: FolderSync): FolderSyncControls {
        return folderSync
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
            permissionPlatform = OnboardingPermissionPlatform.IOS,
        )
    }

    @Provides
    @Named("database")
    @SingleIn(AppScope::class)
    fun provideDatabaseDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default.limitedParallelism(1, "PosatoDatabase")
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(): PosatoDatabase {
        return PosatoDatabase(createIosDatabaseDriver())
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
        return SqlLocalSessionStore(
            database = database,
            databaseDispatcher = databaseDispatcher,
        )
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
        return SessionClock { time(null) * MILLIS_PER_SECOND }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionTimeFormat(): SessionTimeFormat {
        return IosSessionTimeFormat()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppleSync(
        cryptoProvider: IosCryptoProvider,
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
        val crypto = IosSyncCryptoProvider(cryptoProvider)
        val store = SqlBootstrapStore(database, databaseDispatcher)
        val coordinator = BootstrapCoordinator(ports, ports, ports, store, crypto, ports)
        val replica = SqlSyncReplicaStore(database, databaseDispatcher)
        val core = SyncOperationCore(replica, crypto, SyncWallClock { time(null) * MILLIS_PER_SECOND })
        return AppleSync(
            coordinator,
            core,
            ports,
            ports,
            store,
            policySync,
            crypto,
            Dispatchers.IO,
            onWorkspaceRemoved = {
                // Ownership ends with the discarded replica: drop transfer
                // obligations while keeping the occupant's local terminality.
                // Removal already succeeded, so a failed purge stays
                // best-effort instead of failing the removal itself.
                sessions.dropRetainedMarkersExceptCurrent()
            },
            scheduleSync = ScheduleSync(schedules) { zone.localAt(clock.currentEpochMillis()).date },
            backgroundTime = IosSyncBackgroundTime,
            onSetsRemoved = applicationMappings::retainSets,
        )
    }
}

internal data class IosApplicationRuntime(
    val applicationGraph: ApplicationGraph,
    val syncOperationCore: SyncOperationCore,
    val pauseSetPreparation: PauseSetPreparation,
)

internal fun createIosApplicationRuntime(
    cryptoProvider: IosCryptoProvider,
    applicationMappingsProvider: IosApplicationMappingsProvider,
    enforcementProvider: IosEnforcementProvider,
    suspendedExpiryProvider: IosSuspendedExpiryProvider,
    keychainProvider: IosKeychainProvider,
    mailboxProvider: IosCloudKitMailboxProvider,
    notifications: SessionNotificationPlatform,
    schedules: IosScheduleBridge,
    folderPicker: IosFolderPicker = NoFolderPicker,
): IosApplicationRuntime {
    runtimeLock.lock()
    try {
        processRuntime?.let { return it }
        return buildIosApplicationRuntime(
            cryptoProvider,
            applicationMappingsProvider,
            enforcementProvider,
            suspendedExpiryProvider,
            keychainProvider,
            mailboxProvider,
            notifications,
            schedules,
            folderPicker,
        ).also { processRuntime = it }
    } finally {
        runtimeLock.unlock()
    }
}

private fun buildIosApplicationRuntime(
    cryptoProvider: IosCryptoProvider,
    applicationMappingsProvider: IosApplicationMappingsProvider,
    enforcementProvider: IosEnforcementProvider,
    suspendedExpiryProvider: IosSuspendedExpiryProvider,
    keychainProvider: IosKeychainProvider,
    mailboxProvider: IosCloudKitMailboxProvider,
    notifications: SessionNotificationPlatform,
    schedules: IosScheduleBridge,
    folderPicker: IosFolderPicker,
): IosApplicationRuntime {
    val applicationMappings = IosLocalApplicationMappings(applicationMappingsProvider)
    val enforcement = IosSessionEnforcement(
        IosEnforcement(enforcementProvider),
        IosSuspendedExpiry(suspendedExpiryProvider),
    )
    val graph = createGraphFactory<IosApplicationGraph.Factory>().create(
        applicationMappings,
        enforcement,
        keychainProvider,
        mailboxProvider,
        cryptoProvider,
        IosApplicationAccess(applicationMappings),
        notifications,
        schedules,
        folderPicker,
    )
    return IosApplicationRuntime(graph, graph.appleSync.core, graph.pauseSetPreparation)
}

private const val MILLIS_PER_SECOND = 1_000

private val runtimeLock = NSRecursiveLock()
private var processRuntime: IosApplicationRuntime? = null

private object NoFolderPicker : IosFolderPicker {
    override fun pickFolder(completion: (String?) -> Unit) {
        completion(null)
    }
}

private suspend fun pickFolder(picker: IosFolderPicker): String? {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation -> picker.pickFolder { token -> continuation.resume(token) } }
    }
}

private fun iosApplicationSupportDirectory(): String {
    return "${NSHomeDirectory().trimEnd('/')}/Library/Application Support/Posato"
}
