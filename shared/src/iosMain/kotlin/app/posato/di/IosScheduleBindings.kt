package app.posato.di

import app.posato.feature.schedules.IosScheduleZone
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.NoScheduledPauses
import app.posato.feature.schedules.host.ScheduledPauses
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The schedule bindings with this platform's source of the current time zone. */
internal interface IosScheduleBindings : ScheduleBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleZone(): ScheduleZone {
        return IosScheduleZone()
    }

    /** The iPhone host arrives with slice 5; until then nothing starts here on its own. */
    @Provides
    fun provideScheduledPauses(): ScheduledPauses {
        return NoScheduledPauses
    }
}
