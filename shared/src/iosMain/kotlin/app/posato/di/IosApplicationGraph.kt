package app.posato.di

import app.posato.persistence.DatabaseDispatcher
import app.posato.persistence.IosLocalPolicyStoreFactory
import app.posato.persistence.LocalExactDomainPolicyStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.Dispatchers

@DependencyGraph(AppScope::class)
internal interface IosApplicationGraph : ApplicationGraph {
    val localExactDomainPolicyStoreFactory: LocalExactDomainPolicyStoreFactory

    @Provides
    fun provideDatabaseDispatcher(): DatabaseDispatcher {
        return DatabaseDispatcher(Dispatchers.Default.limitedParallelism(1, "PosatoDatabase"))
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePolicyStoreFactory(databaseDispatcher: DatabaseDispatcher): LocalExactDomainPolicyStoreFactory {
        return IosLocalPolicyStoreFactory(databaseDispatcher = databaseDispatcher)
    }
}

fun createIosApplicationGraph(): ApplicationGraph {
    return createGraph<IosApplicationGraph>()
}
