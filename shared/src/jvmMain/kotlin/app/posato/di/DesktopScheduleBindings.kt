package app.posato.di

import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.schedules.JavaScheduleZone
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.MacScheduleStartGate
import app.posato.feature.schedules.host.ScheduleHost
import app.posato.feature.schedules.host.ScheduleHostPorts
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.update.MaintenanceAdmission
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.merge

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
        retention: SqlPartRetentionStore,
        admission: MaintenanceAdmission,
        platform: OnboardingPermissionPlatform,
        @Named("helper") enforcement: EnforcementPort,
    ): ScheduleHost {
        val gate = if (platform == OnboardingPermissionPlatform.MAC) {
            MacScheduleStartGate(macHelper) { macHelper.console?.isOurs() }
        } else {
            // Linux needs no consent beyond the installed service, which answers status once it runs.
            ScheduleStartGate { if (enforcement.status() == EnforcementOutcome.UNAVAILABLE) StartGate.SETUP_REQUIRED else StartGate.READY }
        }
        val ports = ScheduleHostPorts(
            claims = claims,
            gate = gate,
            hadConsent = { macHelper.automaticStartConsent?.given?.value == true },
            targets = { setId -> loadSessionTargets(policyStore, applicationMappings, setId) },
            retention = retention,
            keptApplications = applicationMappings::keptApplications,
            occupied = claims::manualItems,
            maintenanceClosed = { admission.closed.value == true },
            targetChanges = merge(policyStore.policyChanges, applicationMappings.invalidations),
        )
        return ScheduleHost(schedules.store, schedules.zone, clock, ports)
    }

    @Provides
    fun provideScheduledPauses(host: ScheduleHost): ScheduledPauses {
        return host
    }
}
