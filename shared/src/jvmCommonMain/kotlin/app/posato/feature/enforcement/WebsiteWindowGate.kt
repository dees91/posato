package app.posato.feature.enforcement

import app.posato.feature.schedules.JavaScheduleZone
import java.time.ZoneId

/**
 * The shared window evaluation for an enforcer outside the shared core, such as Android's VPN (TARGETS-013): which
 * held websites are blocked now, until when an open one stays open, and the next instant at which that can change.
 * Invalid plans are ignored, so their websites stay blocked.
 */
public object WebsiteWindowGate {
    public fun blockedNow(
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): Set<String> {
        return request(domains, windows, endEpochMillis).blockedNow(epochMillis, JavaScheduleZone { zone }).toSet()
    }

    /** When [domain]'s window closes, if it is open at [epochMillis]; null when it is blocked or not held. */
    public fun openUntil(
        domain: String,
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): Long? {
        val plan = request(domains, windows, endEpochMillis).validPlans().firstOrNull { it.domain == domain } ?: return null
        return (plan.status(epochMillis, JavaScheduleZone { zone }) as? WindowStatus.OpenUntil)?.epochMillis
    }

    public fun nextBoundary(
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): Long? {
        return request(domains, windows, endEpochMillis).nextWindowBoundary(epochMillis, JavaScheduleZone { zone })
    }

    private fun request(
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
    ): EnforcementRequest {
        return EnforcementRequest(domains.toList(), emptyList(), "", 0L, endEpochMillis, windows = windows)
    }
}
