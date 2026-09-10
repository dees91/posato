package app.posato.feature.onboarding

public enum class MacHelperReadiness {
    UNAVAILABLE,
    NOT_ENABLED,
    APPROVAL_REQUIRED,
    READY
}

public interface MacHelperPort {
    public suspend fun enable(): MacHelperReadiness

    public suspend fun recheck(): MacHelperReadiness

    public fun openApprovalSettings()
}

internal object UnavailableMacHelper : MacHelperPort {
    override suspend fun enable(): MacHelperReadiness {
        return MacHelperReadiness.UNAVAILABLE
    }

    override suspend fun recheck(): MacHelperReadiness {
        return MacHelperReadiness.UNAVAILABLE
    }

    override fun openApprovalSettings() = Unit
}
