package app.posato.di

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createIosDatabaseDriver
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.IosEnforcement
import app.posato.feature.enforcement.IosEnforcementProvider
import app.posato.feature.enforcement.IosSessionEnforcement
import app.posato.feature.enforcement.IosSuspendedExpiry
import app.posato.feature.enforcement.IosSuspendedExpiryProvider
import app.posato.feature.session.IosSessionTimeFormat
import app.posato.feature.session.data.LocalSessionStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.domain.RandomSessionIdGenerator
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.bootstrap.AppleBootstrap
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.SqlBootstrapStore
import app.posato.feature.sync.data.IosBootstrapCloudAdapter
import app.posato.feature.sync.data.IosBootstrapKeychainAdapter
import app.posato.feature.sync.data.IosCloudKitMailboxProvider
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosKeychainProvider
import app.posato.feature.sync.data.IosSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.targets.data.IosApplicationMappingsProvider
import app.posato.feature.targets.data.IosLocalApplicationMappings
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.posix.time

@DependencyGraph(AppScope::class)
internal interface IosApplicationGraph : ApplicationGraph {
    val localTargetPolicyStore: LocalTargetPolicyStore
    val syncReplicaStore: SyncReplicaStore
    val appleBootstrap: AppleBootstrap

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides applicationMappings: LocalApplicationMappings,
            @Provides enforcement: EnforcementPort,
            @Provides keychainProvider: IosKeychainProvider,
            @Provides mailboxProvider: IosCloudKitMailboxProvider,
            @Provides cryptoProvider: IosCryptoProvider,
        ): IosApplicationGraph
    }

    @Provides
    @Named("database")
    @SingleIn(AppScope::class)
    fun provideDatabaseDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default.limitedParallelism(1, "PosatoDatabase")
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseDriver(): SqlDriver {
        return createIosDatabaseDriver()
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(driver: SqlDriver): PosatoDatabase {
        return PosatoDatabase(driver)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePolicyStore(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): LocalTargetPolicyStore {
        return SqlLocalTargetPolicyStore(
            database = database,
            databaseDispatcher = databaseDispatcher,
        )
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
    fun provideAppleBootstrap(
        keychainProvider: IosKeychainProvider,
        mailboxProvider: IosCloudKitMailboxProvider,
        cryptoProvider: IosCryptoProvider,
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): AppleBootstrap {
        val keys = IosBootstrapKeychainAdapter(keychainProvider)
        return AppleBootstrap(
            coordinator = BootstrapCoordinator(
                account = keys,
                cloud = IosBootstrapCloudAdapter(mailboxProvider),
                keys = keys,
                store = SqlBootstrapStore(
                    database = database,
                    databaseDispatcher = databaseDispatcher,
                ),
                crypto = IosSyncCryptoProvider(cryptoProvider),
            ),
            backgroundDispatcher = Dispatchers.IO,
        )
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
    )
    val syncOperationCore = SyncOperationCore(
        store = graph.syncReplicaStore,
        cryptoProvider = IosSyncCryptoProvider(cryptoProvider),
        wallClock = SyncWallClock { time(null) * MILLIS_PER_SECOND },
    )

    return IosApplicationRuntime(graph, syncOperationCore)
}

private const val MILLIS_PER_SECOND = 1_000
