package app.posato.feature.enforcement

public class IosSessionEnforcement(
    private val enforcement: IosEnforcement,
    private val expiry: IosSuspendedExpiry,
) : EnforcementPort {
    override val reapplyRequiresPrompt: Boolean = false

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        return if (request.domains.isEmpty() && request.mappingIds.isEmpty()) {
            EnforcementApplyReport(EnforcementOutcome.NOTHING_TO_ENFORCE, false, false)
        } else {
            applyBoth(request)
        }
    }

    override suspend fun clear(): EnforcementOutcome {
        expiry.cancel()
        return when (enforcement.clear()) {
            IosEnforcementOutcome.CLEARED -> EnforcementOutcome.CLEARED

            IosEnforcementOutcome.AUTHORIZATION_REQUIRED,
            IosEnforcementOutcome.AUTHORIZATION_DENIED,
            IosEnforcementOutcome.RESTRICTED -> EnforcementOutcome.AUTHORIZATION_REQUIRED

            IosEnforcementOutcome.UNAVAILABLE -> EnforcementOutcome.UNAVAILABLE

            IosEnforcementOutcome.APPLIED,
            IosEnforcementOutcome.NOTHING_TO_ENFORCE,
            IosEnforcementOutcome.SELECTION_MISSING,
            IosEnforcementOutcome.PLATFORM_FAILURE -> EnforcementOutcome.FAILED
        }
    }

    override suspend fun status(): EnforcementOutcome {
        return when (enforcement.status()) {
            IosEnforcementOutcome.APPLIED -> EnforcementOutcome.APPLIED

            IosEnforcementOutcome.CLEARED -> EnforcementOutcome.CLEARED

            IosEnforcementOutcome.AUTHORIZATION_REQUIRED,
            IosEnforcementOutcome.AUTHORIZATION_DENIED,
            IosEnforcementOutcome.RESTRICTED -> EnforcementOutcome.AUTHORIZATION_REQUIRED

            IosEnforcementOutcome.UNAVAILABLE -> EnforcementOutcome.UNAVAILABLE

            IosEnforcementOutcome.NOTHING_TO_ENFORCE,
            IosEnforcementOutcome.SELECTION_MISSING,
            IosEnforcementOutcome.PLATFORM_FAILURE -> EnforcementOutcome.UNKNOWN
        }
    }

    override suspend fun peekSuspendedExpiry(sessionId: String): Boolean {
        return expiry.readReconciliation(sessionId) == IosExpiryReconciliation.EXPIRED
    }

    override suspend fun acknowledgeSuspendedExpiry(sessionId: String): Boolean {
        return expiry.acknowledgeReconciliation(sessionId)
    }

    override suspend fun displacedSuspendedExpiry(currentSessionId: String): ExpiryDisplacement {
        return expiry.displacedClearedSessionId(currentSessionId)
    }

    private suspend fun applyBoth(request: EnforcementRequest): EnforcementApplyReport {
        return when (enforcement.apply(IosEnforcementRequest(request.domains, request.mappingIds))) {
            IosEnforcementOutcome.APPLIED -> {
                schedule(request)
            }

            IosEnforcementOutcome.NOTHING_TO_ENFORCE -> {
                EnforcementApplyReport(EnforcementOutcome.NOTHING_TO_ENFORCE, false, false)
            }

            IosEnforcementOutcome.AUTHORIZATION_REQUIRED,
            IosEnforcementOutcome.AUTHORIZATION_DENIED,
            IosEnforcementOutcome.RESTRICTED -> {
                EnforcementApplyReport(EnforcementOutcome.AUTHORIZATION_REQUIRED, false, false)
            }

            IosEnforcementOutcome.UNAVAILABLE -> {
                EnforcementApplyReport(EnforcementOutcome.UNAVAILABLE, false, false)
            }

            IosEnforcementOutcome.CLEARED,
            IosEnforcementOutcome.SELECTION_MISSING,
            IosEnforcementOutcome.PLATFORM_FAILURE -> {
                EnforcementApplyReport(EnforcementOutcome.FAILED, false, false)
            }
        }
    }

    private suspend fun schedule(request: EnforcementRequest): EnforcementApplyReport {
        val scheduleRequest = IosSuspendedExpiryRequest(
            request.sessionId,
            request.sessionStartEpochMillis / MILLIS_PER_SECOND,
            request.sessionEndEpochMillis / MILLIS_PER_SECOND,
        )
        return when (expiry.schedule(scheduleRequest)) {
            IosSuspendedExpiryOutcome.SCHEDULED -> {
                EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
            }

            IosSuspendedExpiryOutcome.BELOW_PLATFORM_MINIMUM -> {
                EnforcementApplyReport(EnforcementOutcome.APPLIED, true, false)
            }

            IosSuspendedExpiryOutcome.AUTHORIZATION_REQUIRED -> {
                enforcement.clear()
                EnforcementApplyReport(EnforcementOutcome.AUTHORIZATION_REQUIRED, false, false)
            }

            IosSuspendedExpiryOutcome.CANCELLED,
            IosSuspendedExpiryOutcome.UNAVAILABLE,
            IosSuspendedExpiryOutcome.PLATFORM_FAILURE -> {
                enforcement.clear()
                EnforcementApplyReport(EnforcementOutcome.FAILED, false, false)
            }
        }
    }
}

private const val MILLIS_PER_SECOND: Long = 1_000L
