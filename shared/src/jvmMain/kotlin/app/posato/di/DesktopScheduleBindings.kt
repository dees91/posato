package app.posato.di

import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.schedules.JavaScheduleZone
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.MacScheduleStartGate
import app.posato.feature.schedules.host.ScheduleHost
import app.posato.feature.schedules.host.ScheduleHostPorts
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.update.MaintenanceAdmission
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

    @Provides
    @SingleIn(AppScope::class)
    fun provideScheduleHost(
        schedules: ScheduleDependencies,
        clock: SessionClock,
        claims: PauseClaims,
        macHelper: MacHelperPort,
        policyStore: LocalTargetPolicyStore,
        applicationMappings: LocalApplicationMappings,
        admission: MaintenanceAdmission,
    ): ScheduleHost {
        val ports = ScheduleHostPorts(
            claims = claims,
            gate = MacScheduleStartGate(macHelper) { macHelper.console?.isOurs() },
            hadConsent = { macHelper.automaticStartConsent?.given?.value == true },
            targets = { loadSessionTargets(policyStore, applicationMappings) },
            maintenanceClosed = { admission.closed.value == true },
        )
        return ScheduleHost(schedules.store, schedules.zone, clock, ports)
    }

    @Provides
    fun provideScheduledPauses(host: ScheduleHost): ScheduledPauses {
        return host
    }
}
