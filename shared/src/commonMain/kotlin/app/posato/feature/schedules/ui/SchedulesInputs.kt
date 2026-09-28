package app.posato.feature.schedules.ui

import app.posato.feature.notifications.SessionNotifier
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.schedules.data.LocalScheduleStore
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.targets.data.LocalApplicationMappings

/** What the Schedules destination needs from the application graph. */
internal class SchedulesInputs(
    val store: LocalScheduleStore,
    val zone: ScheduleZone,
    val clock: SessionClock,
    val timeFormat: SessionTimeFormat,
    val notifier: SessionNotifier?,
    val sync: AppleSync,
    val applicationMappings: LocalApplicationMappings,
    val applicationAccess: ApplicationAccessPort,
)
