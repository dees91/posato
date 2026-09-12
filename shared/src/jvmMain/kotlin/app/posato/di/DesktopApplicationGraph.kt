package app.posato.di

import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createDesktopDatabaseDriver
import app.posato.core.database.defaultDesktopPolicyDatabasePath
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.UnavailableApplicationAccess
import app.posato.feature.onboarding.data.SqlLocalSetupStore
import app.posato.feature.session.JvmSessionTimeFormat
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.RandomSessionIdGenerator
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.SqlBootstrapStore
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.macos.MacOsBootstrapCloudAdapter
import app.posato.feature.sync.macos.MacOsBootstrapKeychainAdapter
import app.posato.feature.sync.macos.MacOsMailboxAdapter
import app.posato.feature.sync.macos.defaultSyncCompanionTransport
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@DependencyGraph(AppScope::class)
internal interface DesktopApplicationGraph : ApplicationGraph {
    val localTargetPolicyStore: LocalTargetPolicyStore
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
            @Provides databasePath: String,
            @Provides macHelper: MacHelperPort,
        ): DesktopApplicationGraph
    }

    @Provides
    @Named("database")
    @SingleIn(AppScope::class)
    fun provideDatabaseDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(databasePath: String): PosatoDatabase {
        return PosatoDatabase(createDesktopDatabaseDriver(databasePath))
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
    ): SessionTransitionOwner {
        val owner = SessionTransitionOwner(
            backgroundDispatcher = databaseDispatcher,
            store = sessions,
            clock = clock,
            enforcement = enforcement,
            loadTargets = { loadSessionTargets(policyStore, applicationMappings) },
            triggers = sync.sessionTriggers,
        )
        sync.sessionObserver = owner
        return owner
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboarding(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        macHelper: MacHelperPort,
    ): OnboardingDependencies {
        return OnboardingDependencies(
            setupStore = SqlLocalSetupStore(database, databaseDispatcher),
            applicationAccess = UnavailableApplicationAccess,
            macHelper = macHelper,
            permissionPlatform = OnboardingPermissionPlatform.MAC,
        )
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
    fun provideAppleSync(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        policySync: LocalPolicySyncStore,
    ): AppleSync {
        val transport = defaultSyncCompanionTransport()
        val keys = MacOsBootstrapKeychainAdapter(transport)
        val crypto = JdkSyncCryptoProvider()
        val store = SqlBootstrapStore(database, databaseDispatcher)
        val mailbox = MacOsMailboxAdapter(transport)
        val coordinator = BootstrapCoordinator(keys, MacOsBootstrapCloudAdapter(transport), keys, store, crypto, mailbox)
        val core = SyncOperationCore(SqlSyncReplicaStore(database, databaseDispatcher), crypto, SyncWallClock { System.currentTimeMillis() })
        return AppleSync(coordinator, core, MacOsMailboxAdapter(transport), keys, store, policySync, crypto, Dispatchers.IO)
    }
}

fun createDesktopApplicationGraph(
    applicationMappings: LocalApplicationMappings,
    enforcement: EnforcementPort,
    macHelper: MacHelperPort,
    databasePath: String = defaultDesktopPolicyDatabasePath(),
): ApplicationGraph {
    return synchronized(desktopGraphLock) {
        val existing = processDesktopGraph
        if (existing != null) {
            require(processDatabasePath == databasePath)
            existing
        } else {
            createGraphFactory<DesktopApplicationGraph.Factory>().create(
                applicationMappings,
                enforcement,
                databasePath,
                macHelper,
            ).also {
                processDatabasePath = databasePath
                processDesktopGraph = it
            }
        }
    }
}

private val desktopGraphLock = Any()
private var processDesktopGraph: ApplicationGraph? = null
private var processDatabasePath: String? = null
