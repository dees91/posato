package app.posato.di

import app.posato.feature.notifications.SessionNotificationPlatform
import app.posato.feature.schedules.IosScheduleBridge
import app.posato.feature.schedules.IosScheduleClaims
import app.posato.feature.schedules.IosScheduleMonitorPublisher
import app.posato.feature.schedules.IosScheduleStartGate
import app.posato.feature.schedules.IosScheduleZone
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.ScheduleHost
import app.posato.feature.schedules.host.ScheduleHostPorts
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.session.domain.LocalSessionStatus
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
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

    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleHost(
        schedules: ScheduleDependencies,
        clock: SessionClock,
        bridge: IosScheduleBridge,
        policyStore: LocalTargetPolicyStore,
        applicationMappings: LocalApplicationMappings,
        notifications: SessionNotificationPlatform,
        sessions: SessionTransitionOwner,
    ): ScheduleHost {
        val manualEnd = { (sessions.status.value as? LocalSessionStatus.Active)?.record?.endEpochMillis }
        val publisher = IosScheduleMonitorPublisher(bridge.monitor, schedules.zone, notifications, manualEnd)
        val ports = ScheduleHostPorts(
            claims = IosScheduleClaims(bridge.enforcement),
            gate = IosScheduleStartGate(bridge.enforcement),
            // Screen Time authorization is the consent here, and the Schedules screen asks for it.
            hadConsent = { false },
            targets = { loadSessionTargets(policyStore, applicationMappings) },
            maintenanceClosed = { false },
            // The monitor extension announces starts, including while the app is closed.
            announcesStarts = false,
            announcedElsewhere = { publisher.startedOccurrences() },
            publish = publisher::publish,
        )
        return ScheduleHost(schedules.store, schedules.zone, clock, ports)
    }

    @Provides
    fun provideScheduledPauses(host: ScheduleHost): ScheduledPauses {
        return host
    }
}
