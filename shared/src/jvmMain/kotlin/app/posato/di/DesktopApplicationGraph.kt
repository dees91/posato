package app.posato.di

import app.cash.sqldelight.db.SqlDriver
import app.posato.core.database.PosatoDatabase
import app.posato.core.database.createDesktopDatabaseDriver
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
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

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides applicationMappings: LocalApplicationMappings
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
    fun provideDatabaseDriver(): SqlDriver {
        return createDesktopDatabaseDriver()
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
}

fun createDesktopApplicationGraph(applicationMappings: LocalApplicationMappings): ApplicationGraph {
    return createGraphFactory<DesktopApplicationGraph.Factory>().create(applicationMappings)
}
