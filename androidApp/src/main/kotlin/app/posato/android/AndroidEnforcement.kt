package app.posato.android

import android.content.Context
import app.posato.feature.enforcement.EnforcementApplyReport
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.EnforcementRequest
import app.posato.feature.targets.data.CatalogApplicationMappings

/**
 * Pauses on Android (ADR 0010): websites through the DNS-only VPN, applications through the guard's usage watch and
 * block screen. The guard clears a pause by itself at its end and keeps that expiry until the app reads it.
 */
internal class AndroidEnforcement(
    private val context: Context,
    private val state: PauseState,
    private val mappings: CatalogApplicationMappings,
) : EnforcementPort {
    override val reapplyRequiresPrompt: Boolean = false

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        if (request.domains.isEmpty() && request.mappingIds.isEmpty()) return report(EnforcementOutcome.NOTHING_TO_ENFORCE)
        if (!Permissions.blockingReady(context)) return report(EnforcementOutcome.AUTHORIZATION_REQUIRED)
        val packages = mappings.targets(request.mappingIds).toSet()
        state.apply(AppliedPause(request.sessionId, request.sessionEndEpochMillis, request.domains.toSet(), packages))
        DnsVpnService.refresh(context)
        GuardService.start(context)
        return report(EnforcementOutcome.APPLIED)
    }

    override suspend fun clear(): EnforcementOutcome {
        state.clear(expired = false)
        DnsVpnService.refresh(context)
        return EnforcementOutcome.CLEARED
    }

    override suspend fun status(): EnforcementOutcome {
        if (!Permissions.blockingReady(context)) return EnforcementOutcome.UNAVAILABLE
        return if (state.applied() != null) EnforcementOutcome.APPLIED else EnforcementOutcome.CLEARED
    }

    override suspend fun holdsSession(sessionId: String): Boolean {
        return state.applied()?.sessionId == sessionId
    }

    override suspend fun peekSuspendedExpiry(sessionId: String): Boolean {
        return state.expiredSessionId() == sessionId
    }

    override suspend fun acknowledgeSuspendedExpiry(sessionId: String): Boolean {
        state.acknowledge(sessionId)
        return true
    }

    private fun report(outcome: EnforcementOutcome): EnforcementApplyReport {
        return EnforcementApplyReport(outcome, belowPlatformMinimum = false, repeatsSystemPrompt = false)
    }
}
