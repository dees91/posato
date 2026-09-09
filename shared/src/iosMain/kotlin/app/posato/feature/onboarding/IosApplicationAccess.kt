package app.posato.feature.onboarding

import app.posato.feature.targets.data.IosLocalApplicationMappings

internal class IosApplicationAccess(
    private val mappings: IosLocalApplicationMappings,
) : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        return mappings.requestAuthorization()
    }
}
