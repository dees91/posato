package app.posato.feature.onboarding

import app.posato.feature.targets.data.LocalApplicationMappingsAccess

public sealed interface ApplicationAccessResult {
    public data class Determined(
        public val access: LocalApplicationMappingsAccess,
    ) : ApplicationAccessResult

    public data object Unavailable : ApplicationAccessResult

    public data object Failed : ApplicationAccessResult
}

public interface ApplicationAccessPort {
    public suspend fun requestAuthorization(): ApplicationAccessResult

    /**
     * The access already given, read without asking for anything, or null where it can only be learned by asking. The
     * access step shows a result it reads here, so a device that already allowed everything is not asked again.
     */
    public suspend fun currentAccess(): ApplicationAccessResult? = null
}

internal object UnavailableApplicationAccess : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        return ApplicationAccessResult.Unavailable
    }
}
