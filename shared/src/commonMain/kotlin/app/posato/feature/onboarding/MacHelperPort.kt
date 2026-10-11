package app.posato.feature.onboarding

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

public enum class MacHelperReadiness {
    UNAVAILABLE,
    NOT_ENABLED,
    APPROVAL_REQUIRED,
    READY,
    UNCERTAIN,
    RECOVERY_REQUIRED,
}

public enum class MacHelperRemoval {
    REMOVED,
    REMOVE_AGAIN,
    UNCERTAIN,
    CHECK_AGAIN,
    CANNOT_START,
    APPROVAL_REQUIRED,
    NOT_ENABLED,
    PROXY_ATTENTION,
}

@Stable
public interface MacLoginItem {
    public val enabled: StateFlow<Boolean>

    public fun setEnabled(enabled: Boolean)

    public fun refresh()
}

public enum class MacStandingGrantState {
    UNSUPPORTED,
    OFF,
    ON,
    UNKNOWN,
}

public interface MacStandingGrant {
    public suspend fun read(): MacStandingGrantState

    public suspend fun setEnabled(enabled: Boolean): MacStandingGrantState
}

/**
 * The consent to automatic starts, including schedules added on other devices. It is a local value of its
 * own, never derived from the offer marker, so a host without a window reads it too.
 */
public interface MacAutomaticStartConsent {
    public val given: StateFlow<Boolean>

    public fun record(given: Boolean)
}

/** Whether this account has the console: true, false for another account, null when it cannot be read. */
public fun interface MacConsole {
    public fun isOurs(): Boolean?
}

public interface MacHelperPort {
    public val loginItem: MacLoginItem?
        get() {
            return null
        }

    public val standingGrant: MacStandingGrant?
        get() {
            return null
        }

    public fun setupOfferDismissed(): Boolean {
        return true
    }

    public fun dismissSetupOffer() {
        return
    }

    /** The person's consent to automatic starts; null where automatic starts do not exist. */
    public val automaticStartConsent: MacAutomaticStartConsent?
        get() {
            return null
        }

    public val console: MacConsole?
        get() {
            return null
        }

    /** Operations in flight that an automatic start must wait for; null where none are tracked. */
    public val operations: MacHelperOperations?
        get() {
            return null
        }

    public suspend fun enable(): MacHelperReadiness

    public suspend fun recheck(): MacHelperReadiness

    /**
     * Reads the helper's state without changing it. Unlike [recheck], it never installs a missing
     * authorization rule, so an automatic read cannot perform what only a deliberate action may.
     */
    public suspend fun status(): MacHelperReadiness {
        return recheck()
    }

    public suspend fun remove(): MacHelperRemoval

    public fun openApprovalSettings()
}

internal object UnavailableMacHelper : MacHelperPort {
    override suspend fun enable(): MacHelperReadiness {
        return MacHelperReadiness.UNAVAILABLE
    }

    override suspend fun recheck(): MacHelperReadiness {
        return MacHelperReadiness.UNAVAILABLE
    }

    override suspend fun remove(): MacHelperRemoval {
        return MacHelperRemoval.CHECK_AGAIN
    }

    override fun openApprovalSettings() = Unit
}

/**
 * Ready for schedules: the unified setup is verified, the grant is confirmed on, and the person agreed
 * to automatic starts. Schedules and the host that starts them read this one answer.
 */
internal fun macReadyForSchedules(
    setupComplete: Boolean,
    grant: MacStandingGrantState?,
    consent: Boolean,
): Boolean {
    return setupComplete && grant == MacStandingGrantState.ON && consent
}

/** The Mac helper on a host that has none, such as Linux, whose own service is reached through its enforcement port. */
public fun unavailableMacHelper(): MacHelperPort {
    return UnavailableMacHelper
}
