package app.posato.di

import app.posato.persistence.DatabaseDispatcher
import app.posato.persistence.JvmLocalPolicyStoreFactory
import app.posato.persistence.LocalExactDomainPolicyStoreFactory
import app.posato.persistence.defaultDesktopPolicyDatabasePath
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.Dispatchers

@DependencyGraph(AppScope::class)
internal interface DesktopApplicationGraph : ApplicationGraph {
    val localExactDomainPolicyStoreFactory: LocalExactDomainPolicyStoreFactory

    @Provides
    fun provideDatabaseDispatcher(): DatabaseDispatcher {
        return DatabaseDispatcher(Dispatchers.IO.limitedParallelism(1, "PosatoDatabase"))
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePolicyStoreFactory(databaseDispatcher: DatabaseDispatcher): LocalExactDomainPolicyStoreFactory {
        return JvmLocalPolicyStoreFactory(
            databasePath = defaultDesktopPolicyDatabasePath(),
            databaseDispatcher = databaseDispatcher,
        )
    }
}

fun createDesktopApplicationGraph(): ApplicationGraph {
    return createGraph<DesktopApplicationGraph>()
}
