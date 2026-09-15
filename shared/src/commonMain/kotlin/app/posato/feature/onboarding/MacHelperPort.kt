package app.posato.feature.onboarding

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

public interface MacHelperPort {
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
