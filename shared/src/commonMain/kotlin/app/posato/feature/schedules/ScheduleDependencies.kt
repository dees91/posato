package app.posato.feature.schedules

import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.domain.ScheduleZone

/** The schedule store the UI uses and this device's clock and time zone, provided by each platform graph. */
public class ScheduleDependencies internal constructor(
    internal val store: LocalScheduleStore,
    internal val zone: ScheduleZone,
)
