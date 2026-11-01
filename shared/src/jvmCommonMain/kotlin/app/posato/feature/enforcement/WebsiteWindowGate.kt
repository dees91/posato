package app.posato.feature.enforcement

import java.time.ZoneId

/**
 * The window evaluation for an enforcer outside the shared core, such as Android's VPN: which held websites are
 * blocked now, until when an open one stays open, and the next instant at which that can change.
 */
public object WebsiteWindowGate {
    public fun blockedNow(
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): Set<String> {
        return domains.toSet()
    }

    public fun openUntil(
        domain: String,
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): Long? {
        return null
    }

    public fun nextBoundary(
        domains: Collection<String>,
        windows: List<DomainWindowPlan>,
        endEpochMillis: Long,
        epochMillis: Long,
        zone: ZoneId,
    ): Long? {
        return null
    }
}
