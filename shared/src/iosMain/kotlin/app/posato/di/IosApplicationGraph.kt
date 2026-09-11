package app.posato.di

import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createIosDatabaseDriver
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosSessionEnforcement
import app.posato.feature.enforcement.IosSuspendedExpiry
import app.posato.feature.enforcement.IosSuspendedExpiryProvider
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.IosApplicationAccess
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.UnavailableMacHelper
import app.posato.feature.onboarding.data.SqlLocalSetupStore
import app.posato.feature.session.IosSessionTimeFormat
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.RandomSessionIdGenerator
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
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
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import app.posato.feature.targets.data.IosLocalApplicationMappings
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
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSRecursiveLock
import platform.posix.time

@DependencyGraph(AppScope::class)
internal interface IosApplicationGraph : ApplicationGraph {
    val localTargetPolicyStore: LocalTargetPolicyStore
    val syncReplicaStore: SyncReplicaStore
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
        ): IosApplicationGraph
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
    fun provideSyncReplicaStore(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): SyncReplicaStore {
        return SqlSyncReplicaStore(database, databaseDispatcher)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionStore(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): LocalSessionStore {
        return SqlLocalSessionStore(
            database = database,
            databaseDispatcher = databaseDispatcher,
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
        keychainProvider: IosKeychainProvider,
        mailboxProvider: IosCloudKitMailboxProvider,
        cryptoProvider: IosCryptoProvider,
        database: PosatoDatabase,
        replica: SyncReplicaStore,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        policySync: LocalPolicySyncStore,
    ): AppleSync {
        val keys = IosBootstrapKeychainAdapter(keychainProvider)
        val crypto = IosSyncCryptoProvider(cryptoProvider)
        val store = SqlBootstrapStore(database, databaseDispatcher)
        val coordinator = BootstrapCoordinator(keys, IosBootstrapCloudAdapter(mailboxProvider), keys, store, crypto)
        val core = SyncOperationCore(replica, crypto, SyncWallClock { time(null) * MILLIS_PER_SECOND })
        return AppleSync(coordinator, core, IosMailboxAdapter(mailboxProvider), keys, store, policySync, crypto, Dispatchers.IO)
    }
}

internal data class IosApplicationRuntime(
    val applicationGraph: ApplicationGraph,
    val syncOperationCore: SyncOperationCore,
)

internal fun createIosApplicationRuntime(
    cryptoProvider: IosCryptoProvider,
    applicationMappingsProvider: IosApplicationMappingsProvider,
    enforcementProvider: IosEnforcementProvider,
    suspendedExpiryProvider: IosSuspendedExpiryProvider,
    keychainProvider: IosKeychainProvider,
    mailboxProvider: IosCloudKitMailboxProvider,
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
    )
    return IosApplicationRuntime(graph, graph.appleSync.core)
}

private const val MILLIS_PER_SECOND = 1_000

private val runtimeLock = NSRecursiveLock()
private var processRuntime: IosApplicationRuntime? = null
