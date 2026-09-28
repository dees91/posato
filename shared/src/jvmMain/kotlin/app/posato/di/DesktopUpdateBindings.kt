package app.posato.di

import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.sync.macos.MaintenanceCompanionTransport
import app.posato.feature.sync.macos.defaultSyncCompanionTransport
import app.posato.feature.update.DesktopUpdateMaintenance
import app.posato.feature.update.GatedEnforcementPort
import app.posato.feature.update.MaintenanceAdmission
import app.posato.feature.update.data.SqlUpdateMaintenanceStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

internal interface DesktopUpdateBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideMaintenanceAdmission(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
        clock: SessionClock,
    ): MaintenanceAdmission {
        return MaintenanceAdmission(SqlUpdateMaintenanceStore(database, databaseDispatcher), clock::currentEpochMillis)
    }

    /** Manual sessions and scheduled pauses share the gated helper through their claims. */
    @Provides
    @SingleIn(AppScope::class)
    fun providePauseClaims(
        @Named("helper") helper: EnforcementPort,
        admission: MaintenanceAdmission,
    ): PauseClaims {
        return PauseClaims(GatedEnforcementPort(helper, admission))
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideEnforcement(claims: PauseClaims): EnforcementPort {
        return claims.manual
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideCompanionTransport(): MaintenanceCompanionTransport {
        return MaintenanceCompanionTransport(createDelegate = ::defaultSyncCompanionTransport)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideUpdateMaintenance(
        admission: MaintenanceAdmission,
        companion: MaintenanceCompanionTransport,
    ): DesktopUpdateMaintenance {
        return DesktopUpdateMaintenance(admission, companion)
    }
}
