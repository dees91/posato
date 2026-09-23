package app.posato.desktop.update

import app.posato.desktop.macos.MacOsHelperClient
import app.posato.desktop.macos.processHelperMaintenance
import app.posato.feature.update.DesktopUpdateMaintenance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

internal fun openInstanceLock(): FileInstanceLock {
    val directory = Path.of(checkNotNull(System.getProperty("user.home")), "Library", "Application Support", "Posato")
    Files.createDirectories(directory)
    return FileInstanceLock(directory.resolve(INSTANCE_LOCK_FILE))
}

internal fun createUpdaterController(
    enforcementClient: MacOsHelperClient,
    maintenance: DesktopUpdateMaintenance,
    instanceLock: FileInstanceLock,
    scope: CoroutineScope,
): UpdaterController {
    val system = MacUpdateSystem.forRunningApplication()
    if (system != null) {
        scope.launch { system.warmUp() }
    }
    val coordinator = UpdateAdmissionCoordinator(
        runningBuild = { system?.runningBuild.orEmpty() },
        gate = maintenance.gate,
        instanceLock = instanceLock,
        helper = enforcementClient,
        helperMaintenance = processHelperMaintenance,
        companion = maintenance.companion,
        storedProxies = { system?.storedProxies() ?: StoredProxyEvidence.UNREADABLE },
        installer = { system?.installer() ?: InstallerObservation.UNKNOWN },
        bundleIdentity = { system?.bundleIdentity() ?: BundleIdentity("", null, signedByTeam = false) },
    )
    return UpdaterController(scope, coordinator, MacUpdater)
}

private const val INSTANCE_LOCK_FILE: String = "instance.lock"
