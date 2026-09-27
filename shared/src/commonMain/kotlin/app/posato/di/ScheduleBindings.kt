package app.posato.di

import app.posato.core.database.PosatoDatabase
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.data.ScheduleSyncStore
import app.posato.feature.schedules.data.SqlScheduleStore
import app.posato.feature.schedules.data.SyncScheduleStore
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.scheduleLink
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

/** Schedule bindings shared by every platform graph; each platform graph adds its own [ScheduleZone]. */
internal interface ScheduleBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleSyncStore(
        database: PosatoDatabase,
        @Named("database") databaseDispatcher: CoroutineDispatcher,
    ): ScheduleSyncStore {
        return SqlScheduleStore(database, databaseDispatcher)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleDependencies(
        sync: AppleSync,
        schedules: ScheduleSyncStore,
        zone: ScheduleZone,
    ): ScheduleDependencies {
        return ScheduleDependencies(SyncScheduleStore(schedules, sync.scheduleLink()), zone)
    }
}
