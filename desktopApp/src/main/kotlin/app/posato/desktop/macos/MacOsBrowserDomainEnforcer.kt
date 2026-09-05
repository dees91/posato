package app.posato.desktop.macos

internal sealed interface BrowserDomainEnforcementResult {
    data class Active(
        val port: UShort,
    ) : BrowserDomainEnforcementResult {
        override fun toString(): String {
            return "BrowserDomainEnforcementResult.Active(redacted)"
        }
    }

    data class Failed(
        val result: HelperResult,
    ) : BrowserDomainEnforcementResult {
        override fun toString(): String {
            return "BrowserDomainEnforcementResult.Failed(redacted)"
        }
    }
}

internal class MacOsBrowserDomainEnforcer(
    private val commands: MacOsBrowserDomainCommands,
) {
    fun start(
        domains: List<String>,
        sessionEndEpochMilliseconds: Long? = null,
    ): BrowserDomainEnforcementResult {
        if (domains.isEmpty() || domains.any { domain -> domain.isBlank() }) {
            return BrowserDomainEnforcementResult.Failed(
                HelperResult(
                    outcome = HelperResult.Outcome.Failure,
                    serviceState = HelperResult.State.Ready,
                    ownershipPhase = HelperResult.Phase.Idle,
                    requiredAction = HelperResult.RequiredAction.None,
                    failure = HelperResult.Failure.InvalidInput,
                ),
            )
        }
        val configured = commands.configureBrowserDomains(domains, sessionEndEpochMilliseconds)
        if (configured.result.outcome != HelperResult.Outcome.Success || configured.port == 0.toUShort()) {
            return BrowserDomainEnforcementResult.Failed(configured.result)
        }
        var applied = commands.apply(configured.port)
        if (applied.outcome == HelperResult.Outcome.UnknownOutcome) {
            applied = commands.reconcileUnknown()
        }
        val verifiedActive = applied.outcome == HelperResult.Outcome.Success &&
            applied.ownershipPhase == HelperResult.Phase.Applied
        if (!verifiedActive) {
            clear()
            return BrowserDomainEnforcementResult.Failed(applied)
        }
        return BrowserDomainEnforcementResult.Active(configured.port)
    }

    fun clear(): HelperResult {
        var restored = commands.restore()
        if (restored.outcome == HelperResult.Outcome.UnknownOutcome) {
            restored = commands.reconcileUnknown()
        }
        return restored
    }
}
