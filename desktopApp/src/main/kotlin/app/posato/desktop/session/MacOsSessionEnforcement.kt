package app.posato.desktop.session

import app.posato.desktop.macos.ApplicationEnforcementResult
import app.posato.desktop.macos.BrowserDomainEnforcementResult
import app.posato.desktop.macos.HelperResult
import app.posato.desktop.macos.MacOsApplicationEnforcer
import app.posato.desktop.macos.MacOsBrowserDomainEnforcer
import app.posato.desktop.macos.MacOsHelperClient
import app.posato.desktop.mappings.DesktopLocalApplicationMappings
import app.posato.feature.enforcement.ApplicationEnforcementLink
import app.posato.feature.enforcement.BrowserEnforcementLink
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.targets.data.LocalApplicationMappingId

internal class MacOsBrowserEnforcementLink(
    private val enforcer: MacOsBrowserDomainEnforcer,
    private val helper: MacOsHelperClient,
) : BrowserEnforcementLink {
    override fun start(
        domains: List<String>,
        sessionEndEpochMillis: Long,
    ): Boolean {
        return enforcer.start(domains, sessionEndEpochMillis) is BrowserDomainEnforcementResult.Active
    }

    override fun clear(): EnforcementOutcome {
        return enforcer.clear().toClearOutcome()
    }

    override fun isApplied(): Boolean? {
        return try {
            val status = helper.status()
            status.outcome == HelperResult.Outcome.Success && status.ownershipPhase == HelperResult.Phase.Applied
        } catch (_: Exception) {
            null
        }
    }
}

internal class MacOsApplicationEnforcementLink(
    private val enforcer: MacOsApplicationEnforcer,
    private val mappings: DesktopLocalApplicationMappings,
    private val helper: MacOsHelperClient,
) : ApplicationEnforcementLink {
    override suspend fun start(
        mappingIds: List<String>,
        sessionEndEpochMillis: Long,
    ): Boolean {
        val ids = mappingIds.map { canonical -> LocalApplicationMappingId.restore(canonical) ?: return false }
        return enforcer.start(ids, sessionEndEpochMillis) is ApplicationEnforcementResult.Active
    }

    override suspend fun clear(): EnforcementOutcome {
        return enforcer.clear().toClearOutcome()
    }

    override suspend fun isServiceReady(): Boolean? {
        return try {
            helper.status().serviceState == HelperResult.State.Ready
        } catch (_: Exception) {
            null
        }
    }
}

internal fun HelperResult.toClearOutcome(): EnforcementOutcome {
    return when {
        outcome == HelperResult.Outcome.Success -> EnforcementOutcome.CLEARED

        serviceState == HelperResult.State.NotRegistered ||
            serviceState == HelperResult.State.ApprovalRequired ||
            serviceState == HelperResult.State.UnavailableOrIncompatible -> EnforcementOutcome.UNAVAILABLE

        else -> EnforcementOutcome.FAILED
    }
}
