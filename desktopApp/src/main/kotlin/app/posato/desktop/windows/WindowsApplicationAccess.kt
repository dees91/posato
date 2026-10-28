package app.posato.desktop.windows

import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.ApplicationAccessResult
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Onboarding's access step on Windows: the MSI installed the pausing service with its one administrator approval, so
 * Posato only checks that the service answers. A service that does not answer needs a restart or the installer again.
 */
internal class WindowsApplicationAccess(
    private val client: WindowsHelperClient,
) : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        val access = if (answers()) LocalApplicationMappingsAccess.READY else LocalApplicationMappingsAccess.AUTHORIZATION_DENIED
        return ApplicationAccessResult.Determined(access)
    }

    override suspend fun currentAccess(): ApplicationAccessResult? {
        return ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.READY).takeIf { answers() }
    }

    private suspend fun answers(): Boolean = withContext(Dispatchers.IO) { client.installed() }
}
