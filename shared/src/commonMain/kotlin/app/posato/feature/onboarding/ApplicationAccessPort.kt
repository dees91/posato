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
}

internal object UnavailableApplicationAccess : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        return ApplicationAccessResult.Unavailable
    }
}
