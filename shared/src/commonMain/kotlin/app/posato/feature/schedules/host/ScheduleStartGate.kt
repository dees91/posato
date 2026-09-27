package app.posato.feature.schedules.host

import app.posato.feature.onboarding.MacAutomaticStartConsent
import app.posato.feature.onboarding.MacHelperPort
import app.posato.feature.onboarding.MacHelperReadiness
import app.posato.feature.onboarding.MacStandingGrantState
import app.posato.feature.onboarding.macSetupComplete

/** Whether a due occurrence may start on this device now. */
internal enum class StartGate {
    READY,
    SETUP_REQUIRED,
    OTHER_ACCOUNT,
    TRANSIENT,
}

/** Asked only when an attempt is due, so an automatic read never runs every minute. */
internal fun interface ScheduleStartGate {
    suspend fun check(): StartGate
}

/**
 * A Mac starts a schedule only while this account has the console, the person agreed to automatic
 * starts, the helper reads ready and a Status confirms the grant. Only a real answer that the grant is
 * off or unsupported, or that the helper is not enabled, withdraws the consent; no answer is transient.
 */
internal class MacScheduleStartGate(
    private val mac: MacHelperPort,
    private val consoleIsOurs: () -> Boolean?,
) : ScheduleStartGate {
    override suspend fun check(): StartGate {
        val consent = mac.automaticStartConsent
        return when {
            consoleIsOurs() == false -> StartGate.OTHER_ACCOUNT
            (mac.operations?.inFlight?.value ?: 0) > 0 -> StartGate.TRANSIENT
            consent == null || !consent.given.value -> StartGate.SETUP_REQUIRED
            else -> checkHelper(consent)
        }
    }

    private suspend fun checkHelper(consent: MacAutomaticStartConsent): StartGate {
        return when (mac.status()) {
            MacHelperReadiness.READY -> {
                checkGrant(consent)
            }

            MacHelperReadiness.NOT_ENABLED -> {
                consent.record(false)
                StartGate.SETUP_REQUIRED
            }

            MacHelperReadiness.APPROVAL_REQUIRED, MacHelperReadiness.RECOVERY_REQUIRED -> {
                StartGate.SETUP_REQUIRED
            }

            MacHelperReadiness.UNAVAILABLE, MacHelperReadiness.UNCERTAIN -> {
                StartGate.TRANSIENT
            }
        }
    }

    private suspend fun checkGrant(consent: MacAutomaticStartConsent): StartGate {
        val grant = mac.standingGrant ?: return StartGate.SETUP_REQUIRED
        mac.loginItem?.refresh()
        return when (grant.read()) {
            MacStandingGrantState.ON -> {
                val complete = macSetupComplete(MacHelperReadiness.READY, mac.loginItem?.enabled?.value, true, MacStandingGrantState.ON)
                if (complete) StartGate.READY else StartGate.SETUP_REQUIRED
            }

            MacStandingGrantState.OFF, MacStandingGrantState.UNSUPPORTED -> {
                consent.record(false)
                StartGate.SETUP_REQUIRED
            }

            MacStandingGrantState.UNKNOWN -> {
                StartGate.TRANSIENT
            }
        }
    }
}
