package app.posato.di

import app.posato.feature.schedules.JavaScheduleZone
import app.posato.feature.schedules.domain.ScheduleZone
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The schedule bindings with this platform's source of the current time zone. */
internal interface DesktopScheduleBindings : ScheduleBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleZone(): ScheduleZone {
        return JavaScheduleZone()
    }
}
