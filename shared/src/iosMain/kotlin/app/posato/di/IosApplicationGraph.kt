package app.posato.di

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createIosDatabaseDriver
import app.posato.feature.sync.data.IosCryptoProvider
import app.posato.feature.sync.data.IosSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.data.SyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.UnavailableLocalApplicationMappings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.posix.time

@DependencyGraph(AppScope::class)
internal interface IosApplicationGraph : ApplicationGraph {
    val localTargetPolicyStore: LocalTargetPolicyStore
    val syncReplicaStore: SyncReplicaStore

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
    fun provideApplicationMappings(): LocalApplicationMappings {
        return UnavailableLocalApplicationMappings
    }
}

internal data class IosApplicationRuntime(
    val applicationGraph: ApplicationGraph,
    val syncOperationCore: SyncOperationCore,
)

internal fun createIosApplicationRuntime(cryptoProvider: IosCryptoProvider): IosApplicationRuntime {
    val graph = createGraph<IosApplicationGraph>()
    val syncOperationCore = SyncOperationCore(
        store = graph.syncReplicaStore,
        cryptoProvider = IosSyncCryptoProvider(cryptoProvider),
        wallClock = SyncWallClock { time(null) * MILLIS_PER_SECOND },
    )

    return IosApplicationRuntime(graph, syncOperationCore)
}

private const val MILLIS_PER_SECOND = 1_000
