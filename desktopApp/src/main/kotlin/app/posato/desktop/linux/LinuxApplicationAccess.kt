package app.posato.desktop.linux

import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.ApplicationAccessResult
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Onboarding's access step on Linux: installing the pausing service, which asks for the administrator password once. */
internal class LinuxApplicationAccess(
    private val installer: LinuxHelperInstaller,
) : ApplicationAccessPort {
    override suspend fun requestAuthorization(): ApplicationAccessResult {
        val installed = withContext(Dispatchers.IO) { installer.install() }
        val access = if (installed) LocalApplicationMappingsAccess.READY else LocalApplicationMappingsAccess.AUTHORIZATION_DENIED
        return ApplicationAccessResult.Determined(access)
    }

    /** A service that already answers reads as allowed, so reinstalling Posato does not ask for the password again. */
    override suspend fun currentAccess(): ApplicationAccessResult? {
        val installed = withContext(Dispatchers.IO) { installer.installed() }
        return ApplicationAccessResult.Determined(LocalApplicationMappingsAccess.READY).takeIf { installed }
    }
}
