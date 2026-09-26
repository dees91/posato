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

public interface MacHelperPort {
    public val loginItem: MacLoginItem?
        get() {
            return null
        }

    public suspend fun enable(): MacHelperReadiness

    public suspend fun recheck(): MacHelperReadiness

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
