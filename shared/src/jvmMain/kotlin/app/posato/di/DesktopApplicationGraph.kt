package app.posato.di

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createDesktopDatabaseDriver
import app.posato.core.database.defaultDesktopPolicyDatabasePath
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.session.JvmSessionTimeFormat
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
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.macos.MacOsBootstrapCloudAdapter
import app.posato.feature.sync.macos.MacOsBootstrapKeychainAdapter
import app.posato.feature.sync.macos.MacOsMailboxAdapter
import app.posato.feature.sync.macos.defaultSyncCompanionTransport
import app.posato.feature.targets.data.LocalApplicationMappings
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
    fun provideDatabaseDriver(databasePath: String): SqlDriver {
        return createDesktopDatabaseDriver(databasePath)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(driver: SqlDriver): PosatoDatabase {
        return PosatoDatabase(driver)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePolicyStore(
        sync: AppleSync,
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): LocalTargetPolicyStore {
        return SyncTargetPolicyStore(SqlLocalTargetPolicyStore(database, databaseDispatcher), sync)
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
    ): AppleSync {
        val transport = defaultSyncCompanionTransport()
        val keys = MacOsBootstrapKeychainAdapter(transport)
        val crypto = JdkSyncCryptoProvider()
        val store = SqlBootstrapStore(database, databaseDispatcher)
        val coordinator = BootstrapCoordinator(keys, MacOsBootstrapCloudAdapter(transport), keys, store, crypto)
        val core = SyncOperationCore(SqlSyncReplicaStore(database, databaseDispatcher), crypto, SyncWallClock { System.currentTimeMillis() })
        return AppleSync(coordinator, core, MacOsMailboxAdapter(transport), keys, store, crypto, Dispatchers.IO)
    }
}

fun createDesktopApplicationGraph(
    applicationMappings: LocalApplicationMappings,
    enforcement: EnforcementPort,
    databasePath: String = defaultDesktopPolicyDatabasePath(),
): ApplicationGraph {
    return synchronized(desktopGraphLock) {
        val existing = processDesktopGraph
        if (existing != null) {
            require(processDatabasePath == databasePath)
            existing
        } else {
            createGraphFactory<DesktopApplicationGraph.Factory>().create(applicationMappings, enforcement, databasePath).also {
                processDatabasePath = databasePath
                processDesktopGraph = it
            }
        }
    }
}

private val desktopGraphLock = Any()
private var processDesktopGraph: ApplicationGraph? = null
private var processDatabasePath: String? = null
